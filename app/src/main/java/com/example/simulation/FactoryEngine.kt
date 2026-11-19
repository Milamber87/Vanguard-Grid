package com.example.simulation

import com.example.data.model.ActiveDynamicEvent
import com.example.data.model.BiomeCatalog
import com.example.data.model.BuildingType
import com.example.data.model.Direction
import com.example.data.model.EnvironmentalHazard
import com.example.data.model.PlacedBuildingState
import com.example.data.model.PlanetBiome
import com.example.data.model.RecipeCatalog
import com.example.data.model.ResourceType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

const val GRID_SIZE = 14

data class MapTile(
    val x: Int,
    val y: Int,
    val ore: ResourceType? = null,
    val hazardTerrain: Boolean = false,
    val geothermalVent: Boolean = false
)

data class CargoPacket(
    val id: Long,
    val x: Int,
    val y: Int,
    val fromX: Int,
    val fromY: Int,
    val resource: ResourceType,
    val progress: Float // 0f..1f interpolation from (fromX,fromY) to (x,y)
)

data class TurretBeam(
    val fromX: Int,
    val fromY: Int,
    val targetX: Float,
    val targetY: Float,
    val ticksLeft: Int
)

data class CombatBeam(
    val fromX: Float,
    val fromY: Float,
    val targetX: Float,
    val targetY: Float,
    val isPlayer: Boolean,
    val isRepair: Boolean = false
)

data class UnitSnapshot(
    val id: Long,
    val archetypeId: String,
    val shortCode: String,
    val isPlayer: Boolean,
    val x: Float,
    val y: Float,
    val hp: Int,
    val maxHp: Int,
    val statusNote: String = "PATROL"
)

data class PlanetSimState(
    val planetId: String,
    val tiles: List<MapTile>,
    val buildings: Map<Pair<Int, Int>, PlacedBuildingState>,
    val cargoPackets: List<CargoPacket> = emptyList(),
    val turretBeams: List<TurretBeam> = emptyList(),
    val units: List<UnitSnapshot> = emptyList(),
    val combatBeams: List<CombatBeam> = emptyList(),
    val inventory: Map<ResourceType, Int> = emptyMap(),
    val powerGeneratedMw: Int = 25,
    val powerConsumedMw: Int = 0,
    val powerEfficiency: Float = 1.0f,
    val activeHazard: EnvironmentalHazard? = null,
    val hazardTicksRemaining: Int = 0,
    val colonizationPercent: Int = 10,
    val tickCount: Long = 0L,
    val nextPacketId: Long = 1L,
    val throughputWindowCount: Int = 0,
    val itemsPerMinute: Int = 0
) {
    fun tileAt(x: Int, y: Int): MapTile? {
        if (x !in 0 until GRID_SIZE || y !in 0 until GRID_SIZE) return null
        return tiles[y * GRID_SIZE + x]
    }
}

object FactoryEngine {

    fun createInitialPlanetState(planetId: String): PlanetSimState {
        val tiles = generateTerrain(planetId)
        val starterBuildings = createStarterOutpost(planetId, tiles)
        val starterInventory = when (planetId) {
            "temperate" -> mapOf(
                ResourceType.IRON_ORE to 35,
                ResourceType.COPPER_ORE to 25,
                ResourceType.COAL to 30,
                ResourceType.SILICA to 18,
                ResourceType.IRON_PLATE to 55,
                ResourceType.COPPER_WIRE to 36,
                ResourceType.STEEL_BEAM to 12
            )
            "volcanic" -> mapOf(
                ResourceType.IRON_PLATE to 45,
                ResourceType.COPPER_WIRE to 30,
                ResourceType.STEEL_BEAM to 20,
                ResourceType.COAL to 30,
                ResourceType.TITANIUM_ORE to 15,
                ResourceType.SILICA to 15
            )
            "cryo" -> mapOf(
                ResourceType.IRON_PLATE to 45,
                ResourceType.COPPER_WIRE to 35,
                ResourceType.STEEL_BEAM to 22,
                ResourceType.COAL to 25,
                ResourceType.CRYO_GEL to 15,
                ResourceType.SILICA to 20
            )
            else -> mapOf(
                ResourceType.IRON_PLATE to 45,
                ResourceType.COPPER_WIRE to 35,
                ResourceType.STEEL_BEAM to 20,
                ResourceType.COAL to 20,
                ResourceType.SILICA to 28,
                ResourceType.URANIUM_ORE to 10
            )
        }
        val state = PlanetSimState(
            planetId = planetId,
            tiles = tiles,
            buildings = starterBuildings,
            inventory = starterInventory,
            colonizationPercent = computeColonizationPercent(starterBuildings, starterInventory, 1.0f)
        )
        return recomputeCoverageAndPower(state, null, emptySet())
    }

    fun generateTerrain(planetId: String): List<MapTile> {
        val list = MutableList(GRID_SIZE * GRID_SIZE) { idx ->
            MapTile(x = idx % GRID_SIZE, y = idx / GRID_SIZE)
        }
        fun setTile(x: Int, y: Int, block: (MapTile) -> MapTile) {
            if (x in 0 until GRID_SIZE && y in 0 until GRID_SIZE) {
                val idx = y * GRID_SIZE + x
                list[idx] = block(list[idx])
            }
        }
        fun placeCluster(cx: Int, cy: Int, ore: ResourceType, coords: List<Pair<Int, Int>>) {
            coords.forEach { (dx, dy) ->
                val tx = cx + dx
                val ty = cy + dy
                if (!(tx in 6..8 && ty in 6..8)) {
                    setTile(tx, ty) { it.copy(ore = ore, hazardTerrain = false) }
                }
            }
        }

        val patchSmall = listOf(0 to 0, 1 to 0, 0 to 1)
        val patchMed = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1, -1 to 0)

        when (planetId) {
            "temperate" -> {
                placeCluster(2, 7, ResourceType.IRON_ORE, patchMed)
                placeCluster(10, 3, ResourceType.IRON_ORE, patchSmall)
                placeCluster(7, 2, ResourceType.COPPER_ORE, patchMed)
                placeCluster(11, 9, ResourceType.COPPER_ORE, patchSmall)
                placeCluster(3, 11, ResourceType.COAL, patchMed)
                placeCluster(11, 11, ResourceType.SILICA, patchSmall)
                placeCluster(2, 2, ResourceType.TITANIUM_ORE, listOf(0 to 0, 1 to 0))
                // Acidic marsh pockets on outer corners
                listOf(0 to 0, 1 to 0, 0 to 1, 12 to 1, 13 to 1, 13 to 2).forEach { (x, y) ->
                    setTile(x, y) { it.copy(hazardTerrain = true, ore = null) }
                }
                setTile(9, 12) { it.copy(geothermalVent = true, ore = null) }
            }
            "volcanic" -> {
                // Active magma river cutting across top-left and bottom-right
                listOf(
                    0 to 3, 1 to 3, 2 to 3, 2 to 4, 3 to 4,
                    10 to 9, 11 to 9, 11 to 10, 12 to 10, 13 to 10
                ).forEach { (x, y) ->
                    setTile(x, y) { it.copy(hazardTerrain = true, ore = null) }
                }
                // Rich Geothermal vents
                listOf(4 to 3, 9 to 10, 11 to 4, 3 to 10).forEach { (x, y) ->
                    setTile(x, y) { it.copy(geothermalVent = true, ore = null, hazardTerrain = false) }
                }
                placeCluster(3, 7, ResourceType.IRON_ORE, patchMed)
                placeCluster(10, 2, ResourceType.TITANIUM_ORE, patchMed)
                placeCluster(2, 11, ResourceType.COAL, patchMed)
                placeCluster(7, 2, ResourceType.COPPER_ORE, patchSmall)
                placeCluster(11, 7, ResourceType.SILICA, patchSmall)
            }
            "cryo" -> {
                // Glacial fissures
                listOf(
                    1 to 1, 2 to 1, 2 to 2, 11 to 1, 12 to 1, 12 to 2,
                    1 to 12, 2 to 12, 11 to 12, 12 to 12
                ).forEach { (x, y) ->
                    setTile(x, y) { it.copy(hazardTerrain = true, ore = null) }
                }
                placeCluster(3, 7, ResourceType.COPPER_ORE, patchMed)
                placeCluster(7, 2, ResourceType.CRYO_GEL, patchMed)
                placeCluster(11, 7, ResourceType.CRYO_GEL, patchSmall)
                placeCluster(10, 11, ResourceType.SILICA, patchMed)
                placeCluster(3, 10, ResourceType.IRON_ORE, patchSmall)
                placeCluster(9, 3, ResourceType.TITANIUM_ORE, patchSmall)
                setTile(5, 12) { it.copy(geothermalVent = true) }
            }
            else -> { // arid
                // Ionized canyon chasms
                listOf(
                    0 to 5, 1 to 5, 1 to 6, 12 to 6, 12 to 7, 13 to 7,
                    6 to 12, 7 to 12, 7 to 13
                ).forEach { (x, y) ->
                    setTile(x, y) { it.copy(hazardTerrain = true, ore = null) }
                }
                placeCluster(3, 7, ResourceType.COPPER_ORE, patchMed)
                placeCluster(7, 2, ResourceType.SILICA, patchMed)
                placeCluster(10, 3, ResourceType.URANIUM_ORE, patchMed)
                placeCluster(3, 11, ResourceType.IRON_ORE, patchSmall)
                placeCluster(10, 10, ResourceType.TITANIUM_ORE, patchSmall)
                placeCluster(2, 2, ResourceType.COAL, patchSmall)
            }
        }
        return list
    }

    private fun createStarterOutpost(
        planetId: String,
        tiles: List<MapTile>
    ): Map<Pair<Int, Int>, PlacedBuildingState> {
        val map = mutableMapOf<Pair<Int, Int>, PlacedBuildingState>()
        // Central UEC Orbital Hub at (7, 7)
        map[7 to 7] = PlacedBuildingState(
            x = 7,
            y = 7,
            type = BuildingType.ORBITAL_HUB,
            dir = Direction.NORTH,
            integrity = BuildingType.ORBITAL_HUB.maxIntegrity
        )

        // Starter automated extraction & smelting line so the factory is immediately alive!
        if (planetId == "temperate") {
            // Iron Drill at (3,7) -> Belt (4,7) -> Arc Smelter (5,7) -> Belt (6,7) -> Hub (7,7)
            map[3 to 7] = PlacedBuildingState(3, 7, BuildingType.DRILL_MK1, Direction.EAST, 100)
            map[4 to 7] = PlacedBuildingState(4, 7, BuildingType.CONVEYOR_MK1, Direction.EAST, 60)
            map[5 to 7] = PlacedBuildingState(5, 7, BuildingType.ARC_SMELTER, Direction.EAST, 110, "iron_plate")
            map[6 to 7] = PlacedBuildingState(6, 7, BuildingType.CONVEYOR_MK1, Direction.EAST, 60)

            // Copper Drill at (7,3) -> Belt (7,4) -> Arc Smelter (7,5) -> Belt (7,6) -> Hub (7,7)
            map[7 to 3] = PlacedBuildingState(7, 3, BuildingType.DRILL_MK1, Direction.SOUTH, 100)
            map[7 to 4] = PlacedBuildingState(7, 4, BuildingType.CONVEYOR_MK1, Direction.SOUTH, 60)
            map[7 to 5] = PlacedBuildingState(7, 5, BuildingType.ARC_SMELTER, Direction.SOUTH, 110, "copper_wire")
            map[7 to 6] = PlacedBuildingState(7, 6, BuildingType.CONVEYOR_MK1, Direction.SOUTH, 60)

            // Coal Generator near hub
            map[6 to 9] = PlacedBuildingState(6, 9, BuildingType.COAL_GENERATOR, Direction.NORTH, 110)
        } else {
            // Forward drop-outpost on frontier planets: Hub + 1 starter drill + 1 generator
            val firstOreTile = tiles.firstOrNull { it.ore == ResourceType.IRON_ORE || it.ore == ResourceType.COPPER_ORE }
            if (firstOreTile != null) {
                map[firstOreTile.x to firstOreTile.y] = PlacedBuildingState(
                    x = firstOreTile.x,
                    y = firstOreTile.y,
                    type = BuildingType.DRILL_MK1,
                    dir = Direction.EAST,
                    integrity = 100
                )
            }
            map[6 to 8] = PlacedBuildingState(6, 8, BuildingType.COAL_GENERATOR, Direction.NORTH, 110)
        }
        return map
    }

    fun recomputeCoverageAndPower(
        state: PlanetSimState,
        activeEvent: ActiveDynamicEvent?,
        unlockedTechs: Set<String>
    ): PlanetSimState {
        val biome = BiomeCatalog.byId(state.planetId)
        val hazard = state.activeHazard
        val thermalPylons = state.buildings.values.filter { it.type == BuildingType.THERMAL_PYLON && it.integrity > 0 }
        val scrubbers = state.buildings.values.filter { it.type == BuildingType.ATMOS_SCRUBBER && it.integrity > 0 }
        val turrets = state.buildings.values.filter { it.type == BuildingType.AUTO_TURRET && it.integrity > 0 }

        var generatedMw = 0
        var consumedMw = 0
        val hasCoal = (state.inventory[ResourceType.COAL] ?: 0) > 0
        val hasFuelCell = (state.inventory[ResourceType.FUEL_CELL] ?: 0) > 0
        val solarMult = biome.solarMultiplier *
            (hazard?.solarOutputMultiplier ?: 1.0f) *
            (activeEvent?.solarMultiplier ?: 1.0f)

        val updatedBuildings = state.buildings.mapValues { (_, b) ->
            val inThermal = thermalPylons.any { distChebyshev(it.x, it.y, b.x, b.y) <= BuildingType.THERMAL_PYLON.protectionRadius }
            val inScrubber = scrubbers.any { distChebyshev(it.x, it.y, b.x, b.y) <= BuildingType.ATMOS_SCRUBBER.protectionRadius }
            val inTurret = turrets.any { distChebyshev(it.x, it.y, b.x, b.y) <= BuildingType.AUTO_TURRET.protectionRadius }

            if (b.type.powerMw > 0) {
                val tile = state.tileAt(b.x, b.y)
                val output = when (b.type) {
                    BuildingType.COAL_GENERATOR -> if (hasCoal) b.type.powerMw else 6
                    BuildingType.SOLAR_COLLECTOR -> (b.type.powerMw * solarMult).toInt().coerceAtLeast(3)
                    BuildingType.GEOTHERMAL_TURBINE -> {
                        val ventBonus = if (tile?.geothermalVent == true) 35 else 0
                        b.type.powerMw + biome.geothermalBonusMw + ventBonus
                    }
                    BuildingType.FUSION_CORE -> if (hasFuelCell) b.type.powerMw else 50
                    else -> b.type.powerMw
                }
                generatedMw += output
            } else if (b.type.powerMw < 0) {
                val baseDraw = -b.type.powerMw
                val demandMult = if (hazard != null && !inThermal && hazard.mitigatedBy == BuildingType.THERMAL_PYLON) {
                    hazard.powerDemandMultiplier
                } else {
                    1.0f
                }
                consumedMw += (baseDraw * demandMult).toInt()
            }

            b.copy(
                shieldedThermal = inThermal,
                shieldedScrubber = inScrubber,
                protectedTurret = inTurret
            )
        }

        val efficiency = when {
            consumedMw <= 0 -> 1.0f
            generatedMw >= consumedMw -> 1.0f
            else -> (generatedMw.toFloat() / consumedMw.toFloat()).coerceIn(0.25f, 1.0f)
        }
        val readiness = computeColonizationPercent(updatedBuildings, state.inventory, efficiency)

        return state.copy(
            buildings = updatedBuildings,
            powerGeneratedMw = generatedMw,
            powerConsumedMw = consumedMw,
            powerEfficiency = efficiency,
            colonizationPercent = readiness
        )
    }

    fun stepSimulation(
        current: PlanetSimState,
        activeEvent: ActiveDynamicEvent?,
        unlockedTechs: Set<String>
    ): PlanetSimState {
        val biome = BiomeCatalog.byId(current.planetId)
        val baseState = recomputeCoverageAndPower(current, activeEvent, unlockedTechs)
        val tick = baseState.tickCount + 1L
        val inv = baseState.inventory.toMutableMap()
        val buildings = baseState.buildings.toMutableMap()
        var nextPacketId = baseState.nextPacketId
        val newPackets = mutableListOf<CargoPacket>()
        val beams = baseState.turretBeams.mapNotNull {
            if (it.ticksLeft > 1) it.copy(ticksLeft = it.ticksLeft - 1) else null
        }.toMutableList()
        var deliveredThisTick = 0

        val vanguardBonus = if ("t5_vanguard" in unlockedTechs) 1.35f else 1.0f
        val eventSpeedMult = activeEvent?.factorySpeedMultiplier ?: 1.0f
        val hazard = baseState.activeHazard

        // 1. Periodic fuel burn (every 25 ticks = 5 seconds)
        if (tick % 25L == 0L) {
            val coalGens = buildings.values.count { it.type == BuildingType.COAL_GENERATOR }
            if (coalGens > 0) {
                val curCoal = inv[ResourceType.COAL] ?: 0
                if (curCoal > 0) inv[ResourceType.COAL] = (curCoal - coalGens).coerceAtLeast(0)
            }
            val fusionGens = buildings.values.count { it.type == BuildingType.FUSION_CORE }
            if (fusionGens > 0 && tick % 50L == 0L) {
                val curFuel = inv[ResourceType.FUEL_CELL] ?: 0
                if (curFuel > 0) inv[ResourceType.FUEL_CELL] = (curFuel - fusionGens).coerceAtLeast(0)
            }
        }

        // 2. Advance existing Cargo Packets along conveyor belts
        val occupiedBeltTiles = mutableSetOf<Pair<Int, Int>>()
        for (packet in baseState.cargoPackets) {
            val b = buildings[packet.x to packet.y]
            if (b == null || !b.type.isConveyor()) {
                // Absorbed into hub/silo/machine or dropped into buffer
                inv[packet.resource] = (inv[packet.resource] ?: 0) + 1
                deliveredThisTick++
                continue
            }

            // Compute belt speed based on tier, power, and Cryo/Ash hazards
            val baseStep = when (b.type) {
                BuildingType.CONVEYOR_MK3 -> 0.50f
                BuildingType.CONVEYOR_MK2 -> 0.34f
                else -> 0.22f
            }
            val hazardBeltPenalty = if (hazard != null) {
                val isImmune = (b.type == BuildingType.CONVEYOR_MK3 && hazard.id == "cryo_blizzard") ||
                    (hazard.mitigatedBy == BuildingType.THERMAL_PYLON && b.shieldedThermal) ||
                    (hazard.mitigatedBy == BuildingType.ATMOS_SCRUBBER && b.shieldedScrubber)
                if (isImmune) 1.0f else hazard.beltSpeedMultiplier
            } else 1.0f

            val step = baseStep * baseState.powerEfficiency * hazardBeltPenalty * eventSpeedMult
            val nextProgress = packet.progress + step
            if (nextProgress < 1.0f) {
                newPackets.add(packet.copy(progress = nextProgress))
                occupiedBeltTiles.add(packet.x to packet.y)
            } else {
                // Attempt handoff to next tile in belt direction (or router branches)
                val targetDir = if (b.type == BuildingType.OMNI_ROUTER) {
                    val dirs = listOf(b.dir, b.dir.rotateClockwise(), b.dir.rotateClockwise().rotateClockwise().rotateClockwise())
                    dirs[(tick.toInt() + packet.id.toInt()) % dirs.size]
                } else {
                    b.dir
                }
                val nx = b.x + targetDir.dx
                val ny = b.y + targetDir.dy
                val nextBuilding = buildings[nx to ny]

                when {
                    nextBuilding == null -> {
                        // Dead end belt pauses at end
                        newPackets.add(packet.copy(progress = 0.95f))
                        occupiedBeltTiles.add(packet.x to packet.y)
                    }
                    nextBuilding.type == BuildingType.ORBITAL_HUB || nextBuilding.type == BuildingType.STORAGE_SILO -> {
                        inv[packet.resource] = (inv[packet.resource] ?: 0) + 1
                        deliveredThisTick++
                    }
                    nextBuilding.type.isProducer() -> {
                        // Deposit directly into planetary/machine pool so downstream smelters/assemblers run smoothly
                        inv[packet.resource] = (inv[packet.resource] ?: 0) + 1
                        deliveredThisTick++
                    }
                    nextBuilding.type.isConveyor() && (nx to ny) !in occupiedBeltTiles -> {
                        newPackets.add(
                            packet.copy(
                                fromX = packet.x,
                                fromY = packet.y,
                                x = nx,
                                y = ny,
                                progress = 0.05f
                            )
                        )
                        occupiedBeltTiles.add(nx to ny)
                    }
                    else -> {
                        newPackets.add(packet.copy(progress = 0.95f))
                        occupiedBeltTiles.add(packet.x to packet.y)
                    }
                }
            }
        }

        // 3. Process Extractors & Production Buildings
        for ((pos, b) in baseState.buildings) {
            if (b.integrity <= 0) continue

            if (b.type.isExtractor()) {
                val tile = baseState.tileAt(b.x, b.y)
                val ore = tile?.ore
                if (ore == null) {
                    buildings[pos] = b.copy(statusNote = "NO VEIN")
                    continue
                }
                val scarcityMult = if (activeEvent?.scarcityResource == ore) activeEvent.drillYieldMultiplier else 1.0f
                val hazardDrillMult = if (hazard != null && !(hazard.mitigatedBy == BuildingType.ATMOS_SCRUBBER && b.shieldedScrubber) &&
                    !(hazard.mitigatedBy == BuildingType.THERMAL_PYLON && b.shieldedThermal)
                ) hazard.drillOutputMultiplier else 1.0f

                val rateFactor = baseState.powerEfficiency * scarcityMult * hazardDrillMult * vanguardBonus * eventSpeedMult
                val ticksPerOre = when (b.type) {
                    BuildingType.DRILL_MK2 -> 4
                    BuildingType.THERMAL_EXTRACTOR -> 4
                    else -> 7
                }
                val progressInc = max(1, (rateFactor * 10f).toInt())
                val newProg = b.progressTicks + progressInc
                val threshold = ticksPerOre * 10
                val note = when {
                    scarcityMult < 0.9f -> "SCARCITY"
                    hazardDrillMult < 0.9f -> "HAZARD"
                    baseState.powerEfficiency < 0.85f -> "LOW PWR"
                    else -> "MINING ${ore.code}"
                }
                if (newProg >= threshold) {
                    val fx = b.x + b.dir.dx
                    val fy = b.y + b.dir.dy
                    val frontB = buildings[fx to fy]
                    if (frontB != null && frontB.type.isConveyor() && (fx to fy) !in occupiedBeltTiles && newPackets.size < 45) {
                        newPackets.add(
                            CargoPacket(
                                id = nextPacketId++,
                                x = fx,
                                y = fy,
                                fromX = b.x,
                                fromY = b.y,
                                resource = ore,
                                progress = 0.05f
                            )
                        )
                        occupiedBeltTiles.add(fx to fy)
                    } else {
                        // Direct feed to adjacent Hub/Smelter or stockpile buffer
                        val yieldAmount = if (b.type == BuildingType.DRILL_MK2) 2 else 1
                        inv[ore] = (inv[ore] ?: 0) + yieldAmount
                        deliveredThisTick += yieldAmount
                    }
                    buildings[pos] = b.copy(progressTicks = 0, statusNote = note)
                } else {
                    buildings[pos] = b.copy(progressTicks = newProg, statusNote = note)
                }
            } else if (b.type.isProducer()) {
                val recipe = RecipeCatalog.byId(b.recipeId)
                    ?: RecipeCatalog.forMachine(b.type, unlockedTechs).firstOrNull()
                if (recipe == null) {
                    buildings[pos] = b.copy(statusNote = "IDLE")
                    continue
                }
                val hasInputs = recipe.inputs.all { (res, qty) -> (inv[res] ?: 0) >= qty }
                if (!hasInputs) {
                    buildings[pos] = b.copy(statusNote = "STARVED")
                    continue
                }

                // Bonus speed when connected to an adjacent incoming or outgoing belt!
                val beltConnected = Direction.entries.any { d ->
                    val nb = buildings[(b.x + d.dx) to (b.y + d.dy)]
                    nb != null && (nb.type.isConveyor() || nb.type == BuildingType.ORBITAL_HUB)
                }
                val beltMult = if (beltConnected) 1.45f else 1.0f
                val hazardSlow = if (hazard != null && !b.shieldedThermal && !b.shieldedScrubber) 0.75f else 1.0f
                val inc = max(1, (baseState.powerEfficiency * beltMult * hazardSlow * vanguardBonus * eventSpeedMult * 10f).toInt())
                val nextProg = b.progressTicks + inc
                val threshold = recipe.ticksRequired * 10

                if (nextProg >= threshold) {
                    recipe.inputs.forEach { (res, qty) ->
                        inv[res] = ((inv[res] ?: 0) - qty).coerceAtLeast(0)
                    }
                    val fx = b.x + b.dir.dx
                    val fy = b.y + b.dir.dy
                    val frontB = buildings[fx to fy]
                    if (frontB != null && frontB.type.isConveyor() && (fx to fy) !in occupiedBeltTiles && newPackets.size < 45) {
                        newPackets.add(
                            CargoPacket(
                                id = nextPacketId++,
                                x = fx,
                                y = fy,
                                fromX = b.x,
                                fromY = b.y,
                                resource = recipe.output,
                                progress = 0.05f
                            )
                        )
                        occupiedBeltTiles.add(fx to fy)
                        if (recipe.outputAmount > 1) {
                            inv[recipe.output] = (inv[recipe.output] ?: 0) + (recipe.outputAmount - 1)
                        }
                    } else {
                        inv[recipe.output] = (inv[recipe.output] ?: 0) + recipe.outputAmount
                        deliveredThisTick += recipe.outputAmount
                    }
                    buildings[pos] = b.copy(
                        recipeId = recipe.id,
                        progressTicks = 0,
                        statusNote = if (beltConnected) "ONLINE+" else "ONLINE"
                    )
                } else {
                    buildings[pos] = b.copy(
                        recipeId = recipe.id,
                        progressTicks = nextProg,
                        statusNote = if (hazardSlow < 1f) "EXPOSED" else if (beltConnected) "ONLINE+" else "ONLINE"
                    )
                }
            }
        }

        // 4. Environmental Hazard Integrity Damage & Wildlife Migration Attacks (every 15 ticks = 3s)
        var nextHazard = hazard
        var nextHazardTicks = baseState.hazardTicksRemaining
        if (hazard != null) {
            nextHazardTicks = (nextHazardTicks - 1).coerceAtLeast(0)
            if (tick % 15L == 0L && hazard.integrityDamagePerCycle > 0) {
                val armorBonus = if ("t2_hazards" in unlockedTechs) 0.8f else 1.0f
                for ((pos, b) in buildings.toMap()) {
                    if (b.type == BuildingType.ORBITAL_HUB || b.type == BuildingType.THERMAL_PYLON || b.type == BuildingType.ATMOS_SCRUBBER) continue
                    val protected = (hazard.mitigatedBy == BuildingType.THERMAL_PYLON && b.shieldedThermal) ||
                        (hazard.mitigatedBy == BuildingType.ATMOS_SCRUBBER && b.shieldedScrubber)
                    if (!protected) {
                        val lavaAdjacent = hazard.id == "lava_surge" && Direction.entries.any { d ->
                            baseState.tileAt(b.x + d.dx, b.y + d.dy)?.hazardTerrain == true
                        }
                        val extra = if (lavaAdjacent) 3 else 0
                        val dmg = max(1, ((hazard.integrityDamagePerCycle + extra) * armorBonus).toInt())
                        buildings[pos] = b.copy(
                            integrity = (b.integrity - dmg).coerceAtLeast(15),
                            statusNote = "HAZARD DMG"
                        )
                    }
                }
            }
            if (nextHazardTicks <= 0) {
                nextHazard = null
            }
        }

        // Wildlife raid turrets firing + perimeter damage
        if (activeEvent?.wildlifeRaidActive == true && tick % 10L == 0L) {
            val turrets = buildings.values.filter { it.type == BuildingType.AUTO_TURRET }
            if (turrets.isNotEmpty()) {
                val rng = Random(tick)
                turrets.forEach { t ->
                    val angle = rng.nextFloat() * 6.28f
                    val tx = (t.x + kotlin.math.cos(angle) * 3.2f).coerceIn(0f, (GRID_SIZE - 1).toFloat())
                    val ty = (t.y + kotlin.math.sin(angle) * 3.2f).coerceIn(0f, (GRID_SIZE - 1).toFloat())
                    beams.add(TurretBeam(t.x, t.y, tx, ty, ticksLeft = 4))
                }
            }
            for ((pos, b) in buildings.toMap()) {
                if (b.type == BuildingType.ORBITAL_HUB || b.type == BuildingType.AUTO_TURRET) continue
                if (!b.protectedTurret) {
                    buildings[pos] = b.copy(
                        integrity = (b.integrity - 3).coerceAtLeast(15),
                        statusNote = "UNDER ATTACK"
                    )
                }
            }
        }

        val updatedWindow = baseState.throughputWindowCount + deliveredThisTick
        val (finalWindow, itemsPerMin) = if (tick % 25L == 0L) {
            // 25 ticks = 5 seconds -> multiply by 12 for items/min
            0 to (updatedWindow * 12)
        } else {
            updatedWindow to baseState.itemsPerMinute
        }

        val readiness = computeColonizationPercent(buildings, inv, baseState.powerEfficiency)

        return baseState.copy(
            buildings = buildings,
            cargoPackets = newPackets,
            turretBeams = beams,
            inventory = inv,
            activeHazard = nextHazard,
            hazardTicksRemaining = nextHazardTicks,
            colonizationPercent = readiness,
            tickCount = tick,
            nextPacketId = nextPacketId,
            throughputWindowCount = finalWindow,
            itemsPerMinute = itemsPerMin
        )
    }

    fun computeColonizationPercent(
        buildings: Map<Pair<Int, Int>, PlacedBuildingState>,
        inventory: Map<ResourceType, Int>,
        powerEfficiency: Float
    ): Int {
        val bCount = buildings.size
        val hasDefense = buildings.values.any {
            it.type == BuildingType.THERMAL_PYLON || it.type == BuildingType.ATMOS_SCRUBBER || it.type == BuildingType.AUTO_TURRET
        }
        val hasAdvancedCraft = buildings.values.any {
            it.type == BuildingType.FOUNDRY || it.type == BuildingType.ASSEMBLER || it.type == BuildingType.QUANTUM_MFG
        }
        val uecExports = (inventory[ResourceType.MUNITIONS_CRATE] ?: 0) +
            (inventory[ResourceType.COLONY_MED_POD] ?: 0) +
            (inventory[ResourceType.QUANTUM_MATRIX] ?: 0) * 3
        val score = (bCount * 3).coerceAtMost(45) +
            (if (hasDefense) 18 else 0) +
            (if (hasAdvancedCraft) 15 else 0) +
            (if (powerEfficiency >= 0.95f) 10 else 3) +
            (uecExports * 2).coerceAtMost(12)
        return min(100, max(5, score))
    }

    private fun distChebyshev(x1: Int, y1: Int, x2: Int, y2: Int): Int =
        max(abs(x1 - x2), abs(y1 - y2))
}
