package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.CloudSyncRepository
import com.example.data.model.ActiveDynamicEvent
import com.example.data.model.BiomeCatalog
import com.example.data.model.BuildingCategory
import com.example.data.model.BuildingType
import com.example.data.model.CommanderProfile
import com.example.data.model.Direction
import com.example.data.model.DynamicEventCategory
import com.example.data.model.DynamicEventFactory
import com.example.data.model.MissionEventLog
import com.example.data.model.PlacedBuildingState
import com.example.data.model.PlanetColonyDoc
import com.example.data.model.RecipeCatalog
import com.example.data.model.ResourceType
import com.example.data.model.TechTreeCatalog
import com.example.data.model.UnitCatalog
import com.example.data.model.UnitFaction
import com.example.ecs.EcsPlanetRuntime
import com.example.ecs.FixedTimestepGameLoop
import com.example.ecs.GameLoopStats
import com.example.simulation.FactoryEngine
import com.example.simulation.GRID_SIZE
import com.example.simulation.PlanetSimState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncStatus { SYNCING, SYNCED, ERROR }

data class VanguardUiState(
    val isLoading: Boolean = true,
    val profile: CommanderProfile = CommanderProfile(),
    val activePlanetId: String = "temperate",
    val planets: Map<String, PlanetSimState> = emptyMap(),
    val eventLogs: List<MissionEventLog> = emptyList(),
    val activeDynamicEvent: ActiveDynamicEvent? = null,
    val selectedCategory: BuildingCategory = BuildingCategory.LOGISTICS,
    val selectedBuildTool: BuildingType? = BuildingType.CONVEYOR_MK1,
    val buildDirection: Direction = Direction.EAST,
    val demolishMode: Boolean = false,
    val inspectedTile: Pair<Int, Int>? = null,
    val loopStats: GameLoopStats = GameLoopStats(),
    val syncStatus: SyncStatus = SyncStatus.SYNCING,
    val statusToast: String? = null,
    val errorMessage: String? = null
) {
    val activePlanet: PlanetSimState
        get() = planets[activePlanetId] ?: FactoryEngine.createInitialPlanetState(activePlanetId)

    val unlockedTechSet: Set<String>
        get() = profile.unlockedTechIds.toSet()
}

class VanguardViewModel(
    private val repository: CloudSyncRepository,
    private val currentUserId: String,
    private val defaultCallsign: String
) : ViewModel() {

    // Synchronizes background fixed-timestep ECS coroutine ticks with UI user commands
    private val simMutex = Mutex()

    // Per-planet ECS worlds & runtimes
    private val planetRuntimes: MutableMap<String, EcsPlanetRuntime> =
        BiomeCatalog.allBiomes.associate { biome ->
            val initial = FactoryEngine.createInitialPlanetState(biome.id)
            biome.id to EcsPlanetRuntime(biome.id, initial)
        }.toMutableMap()

    private val _uiState = MutableStateFlow(
        VanguardUiState(
            profile = CommanderProfile(
                userId = currentUserId,
                callsign = defaultCallsign.ifBlank { "Commander" }.take(40)
            ),
            planets = planetRuntimes.mapValues { it.value.exportSnapshot() },
            loopStats = planetRuntimes.getValue("temperate").stats(timeScale = 1f, paused = false)
        )
    )
    val uiState: StateFlow<VanguardUiState> = _uiState.asStateFlow()

    private var profileExistsOnServer = false
    private var hasHydratedFromCloud = false
    private var dirtyLocalChanges = false

    private var eventTimerAccumulatorSec: Float = 0f
    private var hazardSpawnAccumulatorSec: Float = 0f
    private var dynamicEventSpawnAccumulatorSec: Float = 0f
    private var autoSaveAccumulatorSec: Float = 0f
    private var eventCounter: Int = 1

    // Independent fixed-timestep coroutine game loop running on Dispatchers.Default
    private val gameLoop = FixedTimestepGameLoop(
        scope = viewModelScope,
        dispatcher = Dispatchers.Default,
        targetHz = EcsPlanetRuntime.FIXED_HZ,
        onFixedTick = { dtSeconds -> executeFixedSimulationStep(dtSeconds) },
        onBatchComplete = { _, measuredHz -> publishSnapshotFromLoop(measuredHz) }
    )

    init {
        startCloudObservers()
        gameLoop.start()
    }

    override fun onCleared() {
        gameLoop.stop()
        super.onCleared()
    }

    private fun activeRuntimeLocked(): EcsPlanetRuntime {
        val pid = _uiState.value.activePlanetId
        return planetRuntimes.getOrPut(pid) {
            EcsPlanetRuntime(pid, FactoryEngine.createInitialPlanetState(pid))
        }
    }

    // --- INDEPENDENT COROUTINE FIXED-TIMESTEP SIMULATION TICK ---

    private suspend fun executeFixedSimulationStep(dtSeconds: Float) {
        var shouldTriggerAutoSave = false
        simMutex.withLock {
            val snap = _uiState.value
            val runtime = activeRuntimeLocked()
            val prevHazard = runtime.context.activeHazard

            runtime.context.activeEvent = snap.activeDynamicEvent?.takeIf { it.planetId == runtime.planetId }
            runtime.context.unlockedTechs = snap.unlockedTechSet
            runtime.stepFixedTick(dtSeconds)

            var nextEvent = snap.activeDynamicEvent
            var nextProfile = snap.profile
            var profileOrEventChanged = false

            // Credit UEC bounties earned from eliminated hostile units
            if (runtime.context.pendingBountyCredits > 0L) {
                nextProfile = nextProfile.copy(
                    uecCredits = nextProfile.uecCredits + runtime.context.pendingBountyCredits
                )
                runtime.context.pendingBountyCredits = 0L
                profileOrEventChanged = true
                dirtyLocalChanges = true
            }

            // Decrement active dynamic event timer at 1s cadence
            if (nextEvent != null) {
                eventTimerAccumulatorSec += dtSeconds
                if (eventTimerAccumulatorSec >= 1.0f) {
                    val wholeSecs = eventTimerAccumulatorSec.toInt()
                    eventTimerAccumulatorSec -= wholeSecs
                    val remaining = nextEvent.remainingSeconds - wholeSecs
                    if (remaining <= 0) {
                        val expired = nextEvent
                        nextEvent = null
                        nextProfile = nextProfile.copy(hazardsSurvived = nextProfile.hazardsSurvived + 1)
                        profileOrEventChanged = true
                        appendCloudLog(
                            planetId = expired.planetId,
                            category = expired.category.firestoreValue,
                            title = expired.title,
                            outcome = "Window elapsed; sector operations resumed nominal parameters.",
                            creditsDelta = 0L
                        )
                    } else {
                        nextEvent = nextEvent.copy(remainingSeconds = remaining)
                        profileOrEventChanged = true
                    }
                }
            } else {
                eventTimerAccumulatorSec = 0f
            }

            // Detect environmental hazard completion or trigger periodic biome hazards (~55s)
            if (prevHazard != null && runtime.context.activeHazard == null) {
                nextProfile = nextProfile.copy(
                    hazardsSurvived = nextProfile.hazardsSurvived + 1,
                    colonizationScore = nextProfile.colonizationScore + 5,
                    uecCredits = nextProfile.uecCredits + 25L
                )
                profileOrEventChanged = true
                appendCloudLog(
                    planetId = runtime.planetId,
                    category = "hazard",
                    title = "${prevHazard.name} Subsided",
                    outcome = "Planetary grid weathered ${prevHazard.name} on ${BiomeCatalog.byId(runtime.planetId).name}.",
                    creditsDelta = 25L
                )
                dirtyLocalChanges = true
            } else if (runtime.context.activeHazard == null) {
                hazardSpawnAccumulatorSec += dtSeconds
                if (hazardSpawnAccumulatorSec >= 55f) {
                    hazardSpawnAccumulatorSec = 0f
                    val biome = BiomeCatalog.byId(runtime.planetId)
                    if (biome.hazards.isNotEmpty()) {
                        val chosen = biome.hazards[(runtime.context.tickCount.toInt() + eventCounter) % biome.hazards.size]
                        runtime.triggerHazard(chosen, durationSeconds = 35f)
                    }
                }
            }

            // Trigger periodic unpredictable dynamic events (~45s)
            if (nextEvent == null) {
                dynamicEventSpawnAccumulatorSec += dtSeconds
                if (dynamicEventSpawnAccumulatorSec >= 45f) {
                    dynamicEventSpawnAccumulatorSec = 0f
                    eventCounter++
                    nextEvent = DynamicEventFactory.createRandomEvent(
                        planetId = runtime.planetId,
                        unlockedTechs = nextProfile.unlockedTechIds.toSet(),
                        seed = eventCounter * 31 + runtime.context.tickCount.toInt()
                    )
                    profileOrEventChanged = true
                }
            }

            if (profileOrEventChanged) {
                _uiState.update {
                    it.copy(
                        profile = nextProfile,
                        activeDynamicEvent = nextEvent
                    )
                }
            }

            autoSaveAccumulatorSec += dtSeconds
            if ((dirtyLocalChanges && autoSaveAccumulatorSec >= 8f) || autoSaveAccumulatorSec >= 40f) {
                autoSaveAccumulatorSec = 0f
                shouldTriggerAutoSave = true
            }
        }

        if (shouldTriggerAutoSave) {
            syncNow(silent = true)
        }
    }

    private suspend fun publishSnapshotFromLoop(measuredHz: Int) {
        val (planetId, snapshot, stats) = simMutex.withLock {
            val runtime = activeRuntimeLocked()
            Triple(
                runtime.planetId,
                runtime.exportSnapshot(),
                runtime.stats(timeScale = gameLoop.timeScale, paused = gameLoop.paused)
                    .copy(fixedHz = measuredHz.coerceAtLeast(1))
            )
        }
        _uiState.update {
            it.copy(
                planets = it.planets.toMutableMap().apply { put(planetId, snapshot) },
                loopStats = stats
            )
        }
    }

    fun toggleSimulationPause() {
        val nextPaused = !gameLoop.paused
        gameLoop.setPaused(nextPaused)
        viewModelScope.launch {
            val stats = simMutex.withLock {
                activeRuntimeLocked().stats(timeScale = gameLoop.timeScale, paused = nextPaused)
            }
            _uiState.update {
                it.copy(
                    loopStats = stats,
                    statusToast = if (nextPaused) "ECS Coroutine Loop Paused" else "ECS Coroutine Loop Resumed (${gameLoop.timeScale.toInt()}x)"
                )
            }
        }
    }

    fun cycleSimulationSpeed() {
        val nextScale = when {
            gameLoop.timeScale < 1.5f -> 2f
            gameLoop.timeScale < 3f -> 4f
            else -> 1f
        }
        gameLoop.setTimeScale(nextScale)
        if (gameLoop.paused) gameLoop.setPaused(false)
        viewModelScope.launch {
            val stats = simMutex.withLock {
                activeRuntimeLocked().stats(timeScale = nextScale, paused = false)
            }
            _uiState.update {
                it.copy(
                    loopStats = stats,
                    statusToast = "ECS Fixed-Timestep Speed: ${nextScale.toInt()}x"
                )
            }
        }
    }

    private suspend fun publishActiveRuntimeSnapshotLocked() {
        val runtime = activeRuntimeLocked()
        val snapshot = runtime.exportSnapshot()
        val stats = runtime.stats(timeScale = gameLoop.timeScale, paused = gameLoop.paused)
        _uiState.update {
            it.copy(
                planets = it.planets.toMutableMap().apply { put(runtime.planetId, snapshot) },
                loopStats = stats
            )
        }
    }

    // --- CLOUD OBSERVERS ---

    private fun startCloudObservers() {
        viewModelScope.launch {
            repository.observeProfile(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Profile sync error", root)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            syncStatus = SyncStatus.ERROR,
                            errorMessage = root.message ?: "Cloud profile sync unavailable"
                        )
                    }
                }
                .collect { remoteProfile ->
                    if (remoteProfile == null) {
                        profileExistsOnServer = false
                        val initialProfile = _uiState.value.profile.copy(
                            userId = currentUserId,
                            callsign = defaultCallsign.ifBlank { "Commander" }.take(40)
                        )
                        _uiState.update { it.copy(isLoading = false, profile = initialProfile) }
                        if (!hasHydratedFromCloud) {
                            hasHydratedFromCloud = true
                            persistProfileAndActivePlanet(initialProfile)
                        }
                    } else {
                        profileExistsOnServer = true
                        hasHydratedFromCloud = true
                        _uiState.update { current ->
                            val mergedTechs = (current.profile.unlockedTechIds + remoteProfile.unlockedTechIds).distinct()
                            val updatedProfile = if (!dirtyLocalChanges) {
                                remoteProfile.copy(unlockedTechIds = mergedTechs)
                            } else {
                                current.profile.copy(unlockedTechIds = mergedTechs)
                            }
                            val nextPlanetId = if (!dirtyLocalChanges) remoteProfile.activePlanetId else current.activePlanetId
                            current.copy(
                                isLoading = false,
                                profile = updatedProfile,
                                activePlanetId = nextPlanetId,
                                syncStatus = SyncStatus.SYNCED,
                                errorMessage = null
                            )
                        }
                    }
                }
        }

        viewModelScope.launch {
            repository.observePlanets(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Planets sync error", firestoreError ?: error)
                    _uiState.update { it.copy(syncStatus = SyncStatus.ERROR) }
                }
                .collect { remotePlanets ->
                    if (remotePlanets.isEmpty() || dirtyLocalChanges) return@collect
                    simMutex.withLock {
                        val current = _uiState.value
                        val updatedMap = current.planets.toMutableMap()
                        remotePlanets.forEach { doc ->
                            val runtime = planetRuntimes.getOrPut(doc.planetId) {
                                EcsPlanetRuntime(doc.planetId, FactoryEngine.createInitialPlanetState(doc.planetId))
                            }
                            val restoredBuildings = doc.buildings.mapNotNull {
                                PlacedBuildingState.fromFirestoreMap(it)
                            }.associateBy { it.x to it.y }
                            val restoredInv = doc.inventory.mapNotNull { (k, v) ->
                                val res = ResourceType.safeValueOf(k) ?: return@mapNotNull null
                                res to v.toInt().coerceAtLeast(0)
                            }.toMap()
                            val biome = BiomeCatalog.byId(doc.planetId)
                            val hazard = biome.hazards.firstOrNull { it.id == doc.activeHazardId }
                            if (restoredBuildings.isNotEmpty()) {
                                val existingSnap = runtime.exportSnapshot()
                                runtime.hydrateFromState(
                                    existingSnap.copy(
                                        buildings = restoredBuildings,
                                        inventory = if (restoredInv.isNotEmpty()) restoredInv else existingSnap.inventory,
                                        activeHazard = hazard,
                                        hazardTicksRemaining = if (hazard != null) 120 else 0
                                    )
                                )
                                updatedMap[doc.planetId] = runtime.exportSnapshot()
                            }
                        }
                        val stats = activeRuntimeLocked().stats(gameLoop.timeScale, gameLoop.paused)
                        _uiState.update {
                            it.copy(
                                planets = updatedMap,
                                loopStats = stats,
                                syncStatus = SyncStatus.SYNCED
                            )
                        }
                    }
                }
        }

        viewModelScope.launch {
            repository.observeEventLogs(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Event logs sync error", firestoreError ?: error)
                }
                .collect { logs ->
                    _uiState.update { it.copy(eventLogs = logs) }
                }
        }
    }

    // --- FACTORY BUILDING & ECS ENTITY MANAGEMENT ---

    fun selectBuildCategory(category: BuildingCategory) {
        val unlocked = _uiState.value.unlockedTechSet
        val firstInCat = BuildingType.entries.firstOrNull {
            it.category == category && it != BuildingType.ORBITAL_HUB &&
                (it.requiredTechId == null || it.requiredTechId in unlocked)
        }
        _uiState.update {
            it.copy(
                selectedCategory = category,
                selectedBuildTool = firstInCat ?: it.selectedBuildTool,
                demolishMode = false
            )
        }
    }

    fun selectBuildTool(type: BuildingType?) {
        _uiState.update {
            it.copy(
                selectedBuildTool = type,
                demolishMode = false,
                inspectedTile = if (type != null) null else it.inspectedTile
            )
        }
    }

    fun toggleDemolishMode() {
        _uiState.update {
            it.copy(
                demolishMode = !it.demolishMode,
                inspectedTile = null
            )
        }
    }

    fun rotateBuildDirection() {
        _uiState.update { it.copy(buildDirection = it.buildDirection.rotateClockwise()) }
    }

    fun clearInspectedTile() {
        _uiState.update { it.copy(inspectedTile = null) }
    }

    fun clearToast() {
        _uiState.update { it.copy(statusToast = null) }
    }

    fun onTileTapped(x: Int, y: Int) {
        if (x !in 0 until GRID_SIZE || y !in 0 until GRID_SIZE) return
        val state = _uiState.value
        val existing = state.activePlanet.buildings[x to y]

        if (state.demolishMode) {
            if (existing != null) deconstructAt(x, y)
            return
        }

        if (existing != null) {
            val tool = state.selectedBuildTool
            if (tool != null && tool.isConveyor() && existing.type.isConveyor() && tool != existing.type) {
                placeBuildingAt(x, y, tool, replaceExisting = true)
            } else {
                _uiState.update { it.copy(inspectedTile = x to y) }
            }
            return
        }

        val tool = state.selectedBuildTool
        if (tool != null) {
            placeBuildingAt(x, y, tool, replaceExisting = false)
        } else {
            _uiState.update { it.copy(inspectedTile = x to y) }
        }
    }

    private fun placeBuildingAt(x: Int, y: Int, type: BuildingType, replaceExisting: Boolean) {
        viewModelScope.launch {
            simMutex.withLock {
                val state = _uiState.value
                val runtime = activeRuntimeLocked()
                val tile = runtime.context.tileAt(x, y) ?: return@withLock

                if (type.requiredTechId != null && type.requiredTechId !in state.unlockedTechSet) {
                    postToast("Research required in Tech Tree to build ${type.displayName}.")
                    return@withLock
                }
                if (tile.hazardTerrain) {
                    postToast("Cannot build directly on ${BiomeCatalog.byId(runtime.planetId).hazardTileLabel}.")
                    return@withLock
                }
                if (type.isExtractor() && tile.ore == null) {
                    postToast("${type.displayName} must be placed on a glowing mineral vein.")
                    return@withLock
                }
                if (!replaceExisting && runtime.world.buildingCount() >= 195) {
                    postToast("Sector grid structure limit reached.")
                    return@withLock
                }

                val inv = runtime.context.inventory
                val missing = type.buildCost.entries.firstOrNull { (res, cost) -> (inv[res] ?: 0) < cost }
                if (missing != null) {
                    postToast("Need ${missing.value} ${missing.key.displayName} to build ${type.shortCode}.")
                    return@withLock
                }

                type.buildCost.forEach { (res, cost) ->
                    inv[res] = ((inv[res] ?: 0) - cost).coerceAtLeast(0)
                }

                val defaultRecipe = RecipeCatalog.forMachine(type, state.unlockedTechSet).firstOrNull()?.id
                    ?: type.defaultRecipeId
                runtime.placeBuilding(
                    x = x,
                    y = y,
                    type = type,
                    dir = state.buildDirection,
                    recipeId = defaultRecipe,
                    replaceExisting = replaceExisting
                )

                dirtyLocalChanges = true
                _uiState.update { it.copy(inspectedTile = null) }
                publishActiveRuntimeSnapshotLocked()
            }
        }
    }

    fun deconstructAt(x: Int, y: Int) {
        viewModelScope.launch {
            simMutex.withLock {
                val runtime = activeRuntimeLocked()
                val existing = _uiState.value.activePlanet.buildings[x to y] ?: return@withLock
                if (existing.type == BuildingType.ORBITAL_HUB) {
                    postToast("UEC Orbital Hub cannot be deconstructed.")
                    return@withLock
                }
                val removedType = runtime.removeBuilding(x, y) ?: return@withLock
                removedType.buildCost.forEach { (res, cost) ->
                    runtime.context.inventory[res] = (runtime.context.inventory[res] ?: 0) + cost
                }
                dirtyLocalChanges = true
                _uiState.update {
                    it.copy(
                        inspectedTile = null,
                        statusToast = "Deconstructed ${removedType.displayName} (100% materials refunded)"
                    )
                }
                publishActiveRuntimeSnapshotLocked()
            }
        }
    }

    fun rotateBuildingAt(x: Int, y: Int) {
        viewModelScope.launch {
            simMutex.withLock {
                val runtime = activeRuntimeLocked()
                if (runtime.rotateBuilding(x, y)) {
                    dirtyLocalChanges = true
                    publishActiveRuntimeSnapshotLocked()
                }
            }
        }
    }

    fun setBuildingRecipe(x: Int, y: Int, recipeId: String) {
        viewModelScope.launch {
            simMutex.withLock {
                val runtime = activeRuntimeLocked()
                if (runtime.setBuildingRecipe(x, y, recipeId)) {
                    dirtyLocalChanges = true
                    postToast("Recipe updated on refinery")
                    publishActiveRuntimeSnapshotLocked()
                }
            }
        }
    }

    fun repairAllBuildings() {
        viewModelScope.launch {
            simMutex.withLock {
                val runtime = activeRuntimeLocked()
                val damagedCount = runtime.damagedBuildingCount()
                if (damagedCount == 0) {
                    postToast("All structures at 100% hull integrity.")
                    return@withLock
                }
                val plateCost = (damagedCount * 2).coerceAtMost(20)
                val curPlates = runtime.context.inventory[ResourceType.IRON_PLATE] ?: 0
                if (curPlates < plateCost) {
                    postToast("Need $plateCost Iron Plates to dispatch repair drones.")
                    return@withLock
                }
                runtime.context.inventory[ResourceType.IRON_PLATE] = curPlates - plateCost
                val repaired = runtime.repairAll()
                dirtyLocalChanges = true
                postToast("Repaired $repaired structures (-$plateCost Iron Plates)")
                publishActiveRuntimeSnapshotLocked()
            }
        }
    }

    // --- MANUAL WORKBENCH CRAFTING & ORBITAL SHIPMENTS ---

    fun manualCraft(recipeId: String) {
        val recipe = RecipeCatalog.byId(recipeId) ?: return
        viewModelScope.launch {
            simMutex.withLock {
                val state = _uiState.value
                if (recipe.requiredTechId != null && recipe.requiredTechId !in state.unlockedTechSet) {
                    postToast("Research ${TechTreeCatalog.byId(recipe.requiredTechId)?.title ?: "tech"} first.")
                    return@withLock
                }
                val runtime = activeRuntimeLocked()
                val inv = runtime.context.inventory
                val missing = recipe.inputs.entries.firstOrNull { (res, qty) -> (inv[res] ?: 0) < qty }
                if (missing != null) {
                    postToast("Need ${missing.value} ${missing.key.displayName} to fabricate.")
                    return@withLock
                }
                recipe.inputs.forEach { (res, qty) -> inv[res] = (inv[res] ?: 0) - qty }
                inv[recipe.output] = (inv[recipe.output] ?: 0) + recipe.outputAmount
                dirtyLocalChanges = true
                postToast("Fabricated +${recipe.outputAmount} ${recipe.output.displayName}")
                publishActiveRuntimeSnapshotLocked()
            }
        }
    }

    fun launchOrbitalShipment(resource: ResourceType, amount: Int) {
        viewModelScope.launch {
            var logData: MissionEventLog? = null
            simMutex.withLock {
                val state = _uiState.value
                val runtime = activeRuntimeLocked()
                val held = runtime.context.inventory[resource] ?: 0
                val sendQty = amount.coerceAtMost(held)
                if (sendQty <= 0) {
                    postToast("No ${resource.displayName} available for orbital launch.")
                    return@withLock
                }
                val bonusMult = if ("t5_vanguard" in state.unlockedTechSet) 1.5 else 1.0
                val earnedCredits = (sendQty * resource.creditValue * bonusMult).toLong().coerceAtLeast(1L)
                val colPoints = (sendQty / 3).coerceAtLeast(1).toLong()
                runtime.context.inventory[resource] = held - sendQty
                val updatedProfile = state.profile.copy(
                    uecCredits = state.profile.uecCredits + earnedCredits,
                    colonizationScore = state.profile.colonizationScore + colPoints,
                    contractsCompleted = state.profile.contractsCompleted + 1L
                )
                dirtyLocalChanges = true
                _uiState.update {
                    it.copy(
                        profile = updatedProfile,
                        statusToast = "Orbital Drop launched: $sendQty ${resource.code} -> +$earnedCredits UEC Credits"
                    )
                }
                publishActiveRuntimeSnapshotLocked()
                logData = MissionEventLog(
                    userId = currentUserId,
                    planetId = runtime.planetId,
                    eventType = DynamicEventCategory.SUPPLY_CONTRACT.firestoreValue,
                    title = "Orbital Cargo Launch: ${resource.displayName}",
                    outcome = "Uploaded $sendQty ${resource.displayName} to UEC Orbital Tender (+$earnedCredits Credits).",
                    creditsDelta = earnedCredits
                )
            }
            logData?.let { repository.appendEventLog(it) }
            syncNow(silent = true)
        }
    }

    // --- TECH TREE RESEARCH ---

    fun unlockTech(techId: String) {
        val node = TechTreeCatalog.byId(techId) ?: return
        viewModelScope.launch {
            var unlockedNow = false
            simMutex.withLock {
                val state = _uiState.value
                if (techId in state.unlockedTechSet) return@withLock

                val missingPrereq = node.prerequisites.firstOrNull { it !in state.unlockedTechSet }
                if (missingPrereq != null) {
                    postToast("Requires ${TechTreeCatalog.byId(missingPrereq)?.title ?: missingPrereq} first.")
                    return@withLock
                }
                if (state.profile.uecCredits < node.creditCost) {
                    postToast("Need ${node.creditCost} UEC Credits to authorize research.")
                    return@withLock
                }
                val runtime = activeRuntimeLocked()
                val inv = runtime.context.inventory
                val missingRes = node.resourceCosts.entries.firstOrNull { (res, qty) -> (inv[res] ?: 0) < qty }
                if (missingRes != null) {
                    postToast("Need ${missingRes.value} ${missingRes.key.displayName} in active sector storage.")
                    return@withLock
                }

                node.resourceCosts.forEach { (res, qty) -> inv[res] = (inv[res] ?: 0) - qty }
                val updatedProfile = state.profile.copy(
                    uecCredits = state.profile.uecCredits - node.creditCost,
                    colonizationScore = state.profile.colonizationScore + (node.tier * 15L),
                    unlockedTechIds = (state.profile.unlockedTechIds + techId).distinct()
                )
                dirtyLocalChanges = true
                unlockedNow = true
                _uiState.update {
                    it.copy(
                        profile = updatedProfile,
                        statusToast = "Tech Unlocked: ${node.title}"
                    )
                }
                publishActiveRuntimeSnapshotLocked()
            }
            if (unlockedNow) syncNow(silent = true)
        }
    }

    // --- PLANET SWITCHING & BIOME HAZARDS ---

    fun switchPlanet(planetId: String) {
        if (planetId == _uiState.value.activePlanetId) return
        viewModelScope.launch {
            simMutex.withLock {
                val currentState = _uiState.value
                val updatedProfile = currentState.profile.copy(activePlanetId = planetId)
                dirtyLocalChanges = true
                _uiState.update {
                    it.copy(
                        activePlanetId = planetId,
                        profile = updatedProfile,
                        inspectedTile = null,
                        statusToast = "Orbital Drop complete: ${BiomeCatalog.byId(planetId).name}"
                    )
                }
                publishActiveRuntimeSnapshotLocked()
            }
            syncNow(silent = true)
        }
    }

    fun triggerHazardPulse() {
        viewModelScope.launch {
            simMutex.withLock {
                val runtime = activeRuntimeLocked()
                val biome = BiomeCatalog.byId(runtime.planetId)
                if (biome.hazards.isEmpty()) return@withLock
                val currentId = runtime.context.activeHazard?.id
                val nextHazard = biome.hazards.firstOrNull { it.id != currentId } ?: biome.hazards.first()
                runtime.triggerHazard(nextHazard, durationSeconds = 35f)
                dirtyLocalChanges = true
                postToast("WARNING: ${nextHazard.name} striking ${biome.name}!")
                publishActiveRuntimeSnapshotLocked()
            }
        }
    }

    // --- DYNAMIC EVENT SYSTEM ---

    fun triggerAnomalyScan() {
        viewModelScope.launch {
            simMutex.withLock {
                val state = _uiState.value
                eventCounter++
                val event = DynamicEventFactory.createRandomEvent(
                    planetId = state.activePlanetId,
                    unlockedTechs = state.unlockedTechSet,
                    seed = eventCounter * 17 + activeRuntimeLocked().context.tickCount.toInt()
                )
                _uiState.update {
                    it.copy(
                        activeDynamicEvent = event,
                        statusToast = "Sector Alert: ${event.title}"
                    )
                }
            }
        }
    }

    fun respondToDynamicEvent(optionId: String) {
        viewModelScope.launch {
            var logData: MissionEventLog? = null
            simMutex.withLock {
                val state = _uiState.value
                val event = state.activeDynamicEvent ?: return@withLock
                val option = event.options.firstOrNull { it.id == optionId } ?: return@withLock
                val runtime = activeRuntimeLocked()

                if (option.requiresTurretOnline) {
                    val hasTurret = state.activePlanet.buildings.values.any { it.type == BuildingType.AUTO_TURRET }
                    if (!hasTurret) {
                        postToast("Build a UEC Rail-Turret on the grid first to execute this response.")
                        return@withLock
                    }
                }
                if (state.profile.uecCredits < option.creditCost) {
                    postToast("Need ${option.creditCost} UEC Credits for this response.")
                    return@withLock
                }
                val inv = runtime.context.inventory
                val missingRes = option.resourceCost.entries.firstOrNull { (res, qty) -> (inv[res] ?: 0) < qty }
                if (missingRes != null) {
                    postToast("Need ${missingRes.value} ${missingRes.key.displayName} in sector storage.")
                    return@withLock
                }

                option.resourceCost.forEach { (res, qty) -> inv[res] = (inv[res] ?: 0) - qty }
                option.resourceReward.forEach { (res, qty) -> inv[res] = (inv[res] ?: 0) + qty }

                val rewardMult = if ("t5_vanguard" in state.unlockedTechSet && event.category == DynamicEventCategory.SUPPLY_CONTRACT) 1.5 else 1.0
                val finalCreditReward = (option.creditReward * rewardMult).toLong()
                val netCredits = finalCreditReward - option.creditCost.toLong()

                val updatedProfile = state.profile.copy(
                    uecCredits = (state.profile.uecCredits + netCredits).coerceAtLeast(0L),
                    colonizationScore = state.profile.colonizationScore + option.colonizationBonus.toLong(),
                    contractsCompleted = state.profile.contractsCompleted +
                        if (event.category == DynamicEventCategory.SUPPLY_CONTRACT && finalCreditReward > 0) 1L else 0L,
                    hazardsSurvived = state.profile.hazardsSurvived + 1L
                )
                dirtyLocalChanges = true
                _uiState.update {
                    it.copy(
                        profile = updatedProfile,
                        activeDynamicEvent = null,
                        statusToast = option.outcomeSummary
                    )
                }
                publishActiveRuntimeSnapshotLocked()
                logData = MissionEventLog(
                    userId = currentUserId,
                    planetId = event.planetId,
                    eventType = event.category.firestoreValue,
                    title = event.title,
                    outcome = option.outcomeSummary,
                    creditsDelta = netCredits
                )
            }
            logData?.let { repository.appendEventLog(it) }
            syncNow(silent = true)
        }
    }

    // --- UNIT ROSTER: DEPLOYMENT, COMBAT WAVES & GARRISON RALLY ---

    fun deployPlayerUnit(archetypeId: String) {
        val archetype = UnitCatalog.byId(archetypeId) ?: return
        if (archetype.faction != UnitFaction.PLAYER) return

        viewModelScope.launch {
            simMutex.withLock {
                val state = _uiState.value
                val reqTech = archetype.requiredTechId
                if (reqTech != null && reqTech !in state.unlockedTechSet) {
                    val techName = TechTreeCatalog.byId(reqTech)?.title ?: reqTech
                    postToast("Unlock $techName in Tech Tree to deploy ${archetype.name}.")
                    return@withLock
                }

                val runtime = activeRuntimeLocked()
                val inv = runtime.context.inventory
                val missing = archetype.buildCost.entries.firstOrNull { (res, qty) -> (inv[res] ?: 0) < qty }
                if (missing != null) {
                    postToast("Need ${missing.value} ${missing.key.displayName} to fabricate ${archetype.name}.")
                    return@withLock
                }

                archetype.buildCost.forEach { (res, qty) ->
                    inv[res] = ((inv[res] ?: 0) - qty).coerceAtLeast(0)
                }
                runtime.spawnPlayerUnit(archetype)
                dirtyLocalChanges = true
                publishActiveRuntimeSnapshotLocked()
                postToast("${archetype.name} deployed to ${BiomeCatalog.byId(runtime.planetId).name}.")
            }
        }
    }

    fun spawnHostileSkirmish(archetypeId: String) {
        val archetype = UnitCatalog.byId(archetypeId) ?: return
        if (archetype.faction != UnitFaction.ENEMY) return
        viewModelScope.launch {
            simMutex.withLock {
                val runtime = activeRuntimeLocked()
                runtime.spawnEnemyUnit(archetype)
                publishActiveRuntimeSnapshotLocked()
                postToast("Hostile Alert: ${archetype.name} breached perimeter!")
            }
        }
    }

    fun spawnHostileWave(waveTier: Int = 1) {
        viewModelScope.launch {
            simMutex.withLock {
                val runtime = activeRuntimeLocked()
                val spawned = runtime.spawnEnemyWave(waveTier)
                publishActiveRuntimeSnapshotLocked()
                postToast("Hostile wave detected: $spawned xeno contacts inbound!")
            }
        }
    }

    fun rallyGarrison() {
        viewModelScope.launch {
            simMutex.withLock {
                val runtime = activeRuntimeLocked()
                val count = runtime.rallyPlayerUnitsToHub()
                publishActiveRuntimeSnapshotLocked()
                postToast(
                    if (count > 0) "Rallied $count UEC unit(s) to Orbital Hub perimeter."
                    else "No active UEC units on this planet."
                )
            }
        }
    }

    // --- CLOUD SYNC ---

    fun syncNow(silent: Boolean = false) {
        val snap = _uiState.value
        _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING) }
        persistProfileAndActivePlanet(snap.profile, silent = silent)
    }

    private fun persistProfileAndActivePlanet(profile: CommanderProfile, silent: Boolean = true) {
        viewModelScope.launch {
            val planet = simMutex.withLock { activeRuntimeLocked().exportSnapshot() }
            val biome = BiomeCatalog.byId(planet.planetId)
            val colonyDoc = PlanetColonyDoc(
                userId = currentUserId,
                planetId = planet.planetId,
                biomeName = biome.name,
                colonizationPercent = planet.colonizationPercent.toLong(),
                powerGeneratedMw = planet.powerGeneratedMw.toLong(),
                powerConsumedMw = planet.powerConsumedMw.toLong(),
                buildings = planet.buildings.values.map { it.toFirestoreMap() },
                inventory = planet.inventory.mapKeys { it.key.name }.mapValues { it.value.toLong() },
                activeHazardId = planet.activeHazard?.id ?: "none"
            )

            val profRes = repository.saveCommanderProfile(profile, profileExistsOnServer)
            val planetRes = repository.savePlanetColony(colonyDoc)

            if (profRes.isSuccess && planetRes.isSuccess) {
                profileExistsOnServer = true
                dirtyLocalChanges = false
                _uiState.update {
                    it.copy(
                        syncStatus = SyncStatus.SYNCED,
                        errorMessage = null,
                        statusToast = if (!silent) "Cloud Sync complete across UEC network" else it.statusToast
                    )
                }
            } else {
                val err = profRes.exceptionOrNull() ?: planetRes.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        syncStatus = SyncStatus.ERROR,
                        errorMessage = err?.localizedMessage ?: "Cloud sync queued locally"
                    )
                }
            }
        }
    }

    private fun appendCloudLog(
        planetId: String,
        category: String,
        title: String,
        outcome: String,
        creditsDelta: Long
    ) {
        viewModelScope.launch {
            repository.appendEventLog(
                MissionEventLog(
                    userId = currentUserId,
                    planetId = planetId,
                    eventType = category,
                    title = title,
                    outcome = outcome,
                    creditsDelta = creditsDelta
                )
            )
        }
    }

    private fun postToast(message: String) {
        _uiState.update { it.copy(statusToast = message) }
    }

    companion object {
        private const val TAG = "VanguardVM"
    }
}
