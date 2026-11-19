package com.example.ecs

import com.example.data.model.ActiveDynamicEvent
import com.example.data.model.BuildingType
import com.example.data.model.Direction
import com.example.data.model.EnvironmentalHazard
import com.example.data.model.PlacedBuildingState
import com.example.data.model.ResourceType
import com.example.data.model.UnitArchetype
import com.example.data.model.UnitCatalog
import com.example.data.model.UnitFaction
import com.example.simulation.CargoPacket
import com.example.simulation.CombatBeam
import com.example.simulation.FactoryEngine
import com.example.simulation.GRID_SIZE
import com.example.simulation.PlanetSimState
import com.example.simulation.TurretBeam
import com.example.simulation.UnitSnapshot

/**
 * Runtime diagnostics snapshot for the fixed-timestep ECS Game Loop.
 */
data class GameLoopStats(
    val entityCount: Int = 0,
    val systemCount: Int = 11,
    val fixedHz: Int = 20,
    val timeScale: Float = 1f,
    val paused: Boolean = false,
    val totalTicks: Long = 0L
)

/**
 * Deterministic fixed-timestep accumulator loop (20 Hz logic tick with spiral-of-death clamp)
 * managing an [EcsWorld] and its ordered pipeline of [EcsSystem] processors for a single planet.
 */
class EcsPlanetRuntime(
    val planetId: String,
    initialState: PlanetSimState
) {
    val world = EcsWorld()
    val context = SimulationContext(
        planetId = planetId,
        tiles = initialState.tiles,
        inventory = initialState.inventory.toMutableMap(),
        activeHazard = initialState.activeHazard,
        hazardSecondsRemaining = initialState.hazardTicksRemaining * 0.2f
    )

    val energyGridSystem = EnergyGridSystem()

    private val systems: List<EcsSystem> = listOf(
        ProtectionCoverageSystem(),
        PowerGridSystem(),
        energyGridSystem,
        UnitCombatAndAiSystem(),
        MovementSystem(),
        ConveyorLogisticsSystem(),
        InventorySystem(),
        ResourceProductionSystem(),
        ExtractorSystem(),
        RefinerySystem(),
        HazardAndDefenseSystem()
    )

    private var accumulatorSeconds: Float = 0f

    init {
        hydrateFromState(initialState)
    }

    fun hydrateFromState(state: PlanetSimState) {
        world.clear()
        context.inventory.clear()
        context.inventory.putAll(state.inventory)
        context.activeHazard = state.activeHazard
        context.hazardSecondsRemaining = state.hazardTicksRemaining * 0.2f
        state.buildings.values.forEach { b ->
            PlanetaryEntityFactory.spawnBuilding(
                world = world,
                x = b.x,
                y = b.y,
                type = b.type,
                direction = b.dir,
                integrity = b.integrity,
                recipeId = b.recipeId
            )
        }
        // Spawn starter UEC Aegis Recon Drone patrolling the Orbital Hub
        val starterDrone = UnitCatalog.byId("uec_scout_drone")
        if (starterDrone != null) {
            spawnPlayerUnit(starterDrone)
        }
        // Run initial coverage + power pass immediately so HUD metrics are warm
        systems[0].update(world, context, 0f)
        systems[1].update(world, context, 0f)
        systems[2].update(world, context, 0f)
    }

    /**
     * Advances the fixed-timestep accumulator with a frame delta (in seconds).
     * Returns the number of deterministic fixed ECS ticks executed during this frame.
     */
    fun advanceFrame(
        frameDeltaSeconds: Float,
        timeScale: Float,
        paused: Boolean,
        activeEvent: ActiveDynamicEvent?,
        unlockedTechs: Set<String>
    ): Int {
        context.activeEvent = activeEvent
        context.unlockedTechs = unlockedTechs
        if (paused || timeScale <= 0f) return 0

        val clampedDelta = frameDeltaSeconds.coerceIn(0f, MAX_FRAME_DELTA_SECONDS) * timeScale
        accumulatorSeconds += clampedDelta

        var stepsExecuted = 0
        while (accumulatorSeconds >= FIXED_STEP_SECONDS && stepsExecuted < MAX_STEPS_PER_FRAME) {
            stepFixedTick(FIXED_STEP_SECONDS)
            accumulatorSeconds -= FIXED_STEP_SECONDS
            stepsExecuted++
        }
        return stepsExecuted
    }

    /** Executes one deterministic fixed-step pass across all registered [EcsSystem]s. */
    fun stepFixedTick(dtSeconds: Float = FIXED_STEP_SECONDS) {
        context.tickCount++
        for (system in systems) {
            system.update(world, context, dtSeconds)
        }
    }

    fun placeBuilding(
        x: Int,
        y: Int,
        type: BuildingType,
        dir: Direction,
        recipeId: String,
        replaceExisting: Boolean
    ): EntityId {
        val existing = world.buildingEntityAt(x, y)
        if (existing != null && replaceExisting) {
            world.destroyEntity(existing)
        }
        val entity = PlanetaryEntityFactory.spawnBuilding(
            world = world,
            x = x,
            y = y,
            type = type,
            direction = dir,
            recipeId = recipeId
        )
        systems[0].update(world, context, 0f)
        systems[1].update(world, context, 0f)
        return entity
    }

    fun removeBuilding(x: Int, y: Int): BuildingType? {
        val existing = world.buildingEntityAt(x, y) ?: return null
        val type = world.get<StructureComponent>(existing)?.buildingType ?: return null
        world.destroyEntity(existing)
        systems[0].update(world, context, 0f)
        systems[1].update(world, context, 0f)
        return type
    }

    fun rotateBuilding(x: Int, y: Int): Boolean {
        val entity = world.buildingEntityAt(x, y) ?: return false
        val transform = world.get<TransformComponent>(entity) ?: return false
        val rotated = transform.direction.rotateClockwise()
        transform.direction = rotated
        world.get<Position>(entity)?.direction = rotated
        world.get<Velocity>(entity)?.let { vel ->
            vel.dx = rotated.dx * vel.speedTilesPerSec
            vel.dy = rotated.dy * vel.speedTilesPerSec
        }
        return true
    }

    fun setBuildingRecipe(x: Int, y: Int, recipeId: String): Boolean {
        val entity = world.buildingEntityAt(x, y) ?: return false
        val refinery = world.get<RefineryComponent>(entity) ?: return false
        refinery.recipeId = recipeId
        refinery.progressSeconds = 0f
        world.get<ProductionRate>(entity)?.let { prod ->
            val recipe = com.example.data.model.RecipeCatalog.byId(recipeId)
            if (recipe != null) {
                prod.baseCycleSeconds = recipe.ticksRequired * 0.2f
                prod.outputAmount = recipe.outputAmount
            }
            prod.progressSeconds = 0f
        }
        return true
    }

    fun repairAll(): Int {
        var count = 0
        world.query<StructureComponent>().forEach { (_, struct) ->
            if (struct.integrity < struct.maxIntegrity) {
                struct.integrity = struct.maxIntegrity
                count++
            }
        }
        return count
    }

    fun damagedBuildingCount(): Int =
        world.query<StructureComponent>().count { (_, s) -> s.integrity < s.maxIntegrity }

    fun triggerHazard(hazard: EnvironmentalHazard, durationSeconds: Float = 35f) {
        context.activeHazard = hazard
        context.hazardSecondsRemaining = durationSeconds
        systems[0].update(world, context, 0f)
        systems[1].update(world, context, 0f)
        systems[2].update(world, context, 0f)
    }

    fun spawnPlayerUnit(archetype: UnitArchetype): EntityId {
        val hub = world.query2<TransformComponent, StructureComponent>()
            .firstOrNull { it.b.buildingType == BuildingType.ORBITAL_HUB }
        val hx = (hub?.a?.x ?: (GRID_SIZE / 2)).toFloat()
        val hy = (hub?.a?.y ?: (GRID_SIZE / 2)).toFloat()
        val angle = (context.nextUnitId % 8) * 0.785398f
        val sx = (hx + kotlin.math.cos(angle) * 1.8f).coerceIn(0.5f, GRID_SIZE - 1.5f)
        val sy = (hy + kotlin.math.sin(angle) * 1.8f).coerceIn(0.5f, GRID_SIZE - 1.5f)
        return PlanetaryEntityFactory.spawnUnit(world, context, archetype, sx, sy)
    }

    fun spawnEnemyUnit(archetype: UnitArchetype, edge: Int = (context.nextUnitId % 4).toInt()): EntityId {
        val offset = ((context.nextUnitId * 3 + context.tickCount) % (GRID_SIZE - 2)).toInt() + 1
        val (sx, sy) = UnitCombatAndAiSystem.perimeterSpawnPoint(edge, offset)
        return PlanetaryEntityFactory.spawnUnit(world, context, archetype, sx, sy)
    }

    fun spawnEnemyWave(waveTier: Int = 1): Int {
        val wave = UnitCatalog.waveForBiome(planetId, waveTier)
        wave.forEachIndexed { idx, archetype ->
            spawnEnemyUnit(archetype, edge = idx % 4)
        }
        return wave.size
    }

    fun rallyPlayerUnitsToHub(): Int {
        val hub = world.query2<TransformComponent, StructureComponent>()
            .firstOrNull { it.b.buildingType == BuildingType.ORBITAL_HUB }
        val hx = (hub?.a?.x ?: (GRID_SIZE / 2)).toFloat()
        val hy = (hub?.a?.y ?: (GRID_SIZE / 2)).toFloat()
        var count = 0
        world.query2<Position, UnitComponent>().forEach { item ->
            if (item.b.faction == UnitFaction.PLAYER) {
                val angle = (item.b.unitId % 8) * 0.785398f
                item.a.x = (hx + kotlin.math.cos(angle) * 2.0f).coerceIn(0.5f, GRID_SIZE - 1.5f)
                item.a.y = (hy + kotlin.math.sin(angle) * 2.0f).coerceIn(0.5f, GRID_SIZE - 1.5f)
                item.a.gridX = item.a.x.toInt()
                item.a.gridY = item.a.y.toInt()
                item.b.statusNote = "RALLIED"
                count++
            }
        }
        return count
    }

    fun exportSnapshot(): PlanetSimState {
        val buildingsMap = LinkedHashMap<Pair<Int, Int>, PlacedBuildingState>(world.buildingCount())
        val structures = world.query2<TransformComponent, StructureComponent>()
        for (item in structures) {
            val coverage = world.get<CoverageStateComponent>(item.entity)
            val refinery = world.get<RefineryComponent>(item.entity)
            val extractor = world.get<ExtractorComponent>(item.entity)
            val progressTicks = when {
                refinery != null -> (refinery.progressSeconds * 50f).toInt()
                extractor != null -> (extractor.progressSeconds * 50f).toInt()
                else -> 0
            }
            buildingsMap[item.a.x to item.a.y] = PlacedBuildingState(
                x = item.a.x,
                y = item.a.y,
                type = item.b.buildingType,
                dir = item.a.direction,
                integrity = item.b.integrity,
                recipeId = refinery?.recipeId ?: item.b.buildingType.defaultRecipeId,
                progressTicks = progressTicks,
                statusNote = item.b.statusNote,
                shieldedThermal = coverage?.shieldedThermal == true,
                shieldedScrubber = coverage?.shieldedScrubber == true,
                protectedTurret = coverage?.protectedTurret == true
            )
        }

        val cargoList = world.query<CargoPacketComponent>().map { (_, pkt) ->
            CargoPacket(
                id = pkt.packetId,
                x = pkt.gridX,
                y = pkt.gridY,
                fromX = pkt.fromX,
                fromY = pkt.fromY,
                resource = pkt.resource,
                progress = pkt.progress.coerceIn(0f, 1f)
            )
        }

        val beamList = world.query<TurretBeamComponent>().map { (_, beam) ->
            TurretBeam(
                fromX = beam.fromX,
                fromY = beam.fromY,
                targetX = beam.targetX,
                targetY = beam.targetY,
                ticksLeft = (beam.ttlSeconds * 10f).toInt().coerceAtLeast(1)
            )
        }

        val unitSnapshots = world.query2<Position, UnitComponent>().map { item ->
            UnitSnapshot(
                id = item.b.unitId,
                archetypeId = item.b.archetype.id,
                shortCode = item.b.archetype.shortCode,
                isPlayer = item.b.faction == UnitFaction.PLAYER,
                x = item.a.x,
                y = item.a.y,
                hp = item.b.hp,
                maxHp = item.b.maxHp,
                statusNote = item.b.statusNote
            )
        }

        val combatBeamList = world.query<CombatBeamComponent>().map { (_, beam) ->
            CombatBeam(
                fromX = beam.fromX,
                fromY = beam.fromY,
                targetX = beam.targetX,
                targetY = beam.targetY,
                isPlayer = beam.isPlayer,
                isRepair = beam.isRepair
            )
        }

        val inventoryCopy = context.inventory.toMap()
        val readiness = FactoryEngine.computeColonizationPercent(
            buildings = buildingsMap,
            inventory = inventoryCopy,
            powerEfficiency = context.powerEfficiency
        )

        return PlanetSimState(
            planetId = planetId,
            tiles = context.tiles,
            buildings = buildingsMap,
            cargoPackets = cargoList,
            turretBeams = beamList,
            units = unitSnapshots,
            combatBeams = combatBeamList,
            inventory = inventoryCopy,
            powerGeneratedMw = context.powerGeneratedMw,
            powerConsumedMw = context.powerConsumedMw,
            powerEfficiency = context.powerEfficiency,
            activeHazard = context.activeHazard,
            hazardTicksRemaining = (context.hazardSecondsRemaining * 5f).toInt(),
            colonizationPercent = readiness,
            tickCount = context.tickCount,
            nextPacketId = context.nextPacketId,
            throughputWindowCount = context.throughputWindowCount,
            itemsPerMinute = context.itemsPerMinute
        )
    }

    fun stats(timeScale: Float, paused: Boolean): GameLoopStats = GameLoopStats(
        entityCount = world.entityCount,
        systemCount = systems.size,
        fixedHz = FIXED_HZ,
        timeScale = timeScale,
        paused = paused,
        totalTicks = context.tickCount
    )

    companion object {
        const val FIXED_HZ = 20
        const val FIXED_STEP_SECONDS = 1f / FIXED_HZ // 0.05s deterministic simulation step
        const val MAX_FRAME_DELTA_SECONDS = 0.25f
        const val MAX_STEPS_PER_FRAME = 5
    }
}
