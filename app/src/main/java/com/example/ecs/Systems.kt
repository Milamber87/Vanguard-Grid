package com.example.ecs

import com.example.data.model.BiomeCatalog
import com.example.data.model.BuildingType
import com.example.data.model.Direction
import com.example.data.model.RecipeCatalog
import com.example.data.model.ResourceType
import com.example.simulation.GRID_SIZE
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Aggregated electrical grid balance returned by [EnergyConsumptionSystem].
 */
data class EnergyGridSummary(
    val totalGenerationMw: Float,
    val totalDemandMw: Float,
    val netMw: Float,
    val efficiency: Float
)

// ============================================================================
// Core ECS Systems (operate directly over EntityManager archetypes)
// ============================================================================

/**
 * Integrates 2D [Velocity] into [Position] for all mobile entities registered in [EntityManager].
 * Stationary grid structures and belt-interpolated cargo packets are excluded when running
 * inside the full planetary pipeline to prevent double integration.
 */
class MovementSystem(
    private val clampToGrid: Boolean = true
) : EcsSystem {
    override val name: String = "MovementSystem"

    fun update(entityManager: EntityManager, dtSeconds: Float): Int {
        if (dtSeconds <= 0f) return 0
        var movedCount = 0
        val maxCoord = (GRID_SIZE - 1).toFloat()
        val movers = entityManager.getEntitiesWith2<Position, Velocity>()
        for (item in movers) {
            // Skip stationary conveyor structures and belt-interpolated packets
            if (entityManager.hasComponent<StructureComponent>(item.entity)) continue
            if (entityManager.hasComponent<CargoPacketComponent>(item.entity)) continue

            val pos = item.a
            val vel = item.b
            if (vel.dx == 0f && vel.dy == 0f) continue

            val nextX = pos.x + vel.dx * dtSeconds
            val nextY = pos.y + vel.dy * dtSeconds
            pos.x = if (clampToGrid) nextX.coerceIn(0f, maxCoord) else nextX
            pos.y = if (clampToGrid) nextY.coerceIn(0f, maxCoord) else nextY
            pos.gridX = pos.x.roundToInt()
            pos.gridY = pos.y.roundToInt()
            movedCount++
        }
        return movedCount
    }

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        update(world.entityManager, dtSeconds)
    }
}

/**
 * Evaluates total electrical generation vs demand across all entities in [EntityManager]
 * carrying [EnergyConsumption] and propagates normalized [EnergyConsumption.gridEfficiency].
 */
class EnergyConsumptionSystem : EcsSystem {
    override val name: String = "EnergyConsumptionSystem"

    fun update(entityManager: EntityManager, dtSeconds: Float): EnergyGridSummary {
        var totalGen = 0f
        var totalDemand = 0f

        val nodes = entityManager.getEntitiesWith<EnergyConsumption>()
        for ((_, energy) in nodes) {
            totalGen += energy.effectiveGenerationMw.coerceAtLeast(0f)
            totalDemand += energy.effectiveDemandMw.coerceAtLeast(0f)
        }

        val efficiency = when {
            totalDemand <= 0f -> 1.0f
            totalGen >= totalDemand -> 1.0f
            else -> (totalGen / totalDemand).coerceIn(0.25f, 1.0f)
        }

        for ((_, energy) in nodes) {
            energy.gridEfficiency = efficiency
        }

        return EnergyGridSummary(
            totalGenerationMw = totalGen,
            totalDemandMw = totalDemand,
            netMw = totalGen - totalDemand,
            efficiency = efficiency
        )
    }

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        val summary = update(world.entityManager, dtSeconds)
        // Only override context power fields if there are standalone EnergyConsumption nodes
        // not already summed by PowerGridSystem
        val hasStandaloneEnergy = world.entityManager.getEntitiesWith<EnergyConsumption>()
            .any { (entity, _) -> !world.entityManager.hasComponent<PowerNodeComponent>(entity) }
        if (hasStandaloneEnergy) {
            ctx.powerGeneratedMw = summary.totalGenerationMw.roundToInt()
            ctx.powerConsumedMw = summary.totalDemandMw.roundToInt()
            ctx.powerEfficiency = summary.efficiency
        }
    }
}

/**
 * Snapshot of a single connected electrical sub-network evaluated by [EnergyGridSystem].
 */
data class EnergyNetworkSnapshot(
    val networkId: Int,
    val producerEntities: List<EntityId>,
    val consumerEntities: List<EntityId>,
    val totalSupplyMw: Float,
    val totalDemandMw: Float,
    val netMw: Float,
    val satisfactionRatio: Float,
    val isBrownout: Boolean,
    val isBlackout: Boolean
)

/**
 * Complete planetary energy distribution report returned by [EnergyGridSystem].
 */
data class EnergyGridReport(
    val networks: List<EnergyNetworkSnapshot> = emptyList(),
    val totalSupplyMw: Float = 0f,
    val totalDemandMw: Float = 0f,
    val netMw: Float = 0f,
    val overallEfficiency: Float = 1.0f,
    val consumerAllocations: Map<EntityId, Float> = emptyMap()
)

/**
 * Manages electrical energy distribution from power producers to consumer buildings across
 * the planetary grid, partitioning nodes into connected transmission networks via [SpatialGrid],
 * calculating total network supply versus demand, allocating MW to consumers, and updating
 * each building's [EnergyConsumption.gridEfficiency] (including brownout and blackout states).
 */
class EnergyGridSystem(
    val transmissionRadius: Int = GRID_SIZE,
    val brownoutFloor: Float = 0.25f
) : EcsSystem {
    override val name: String = "EnergyGridSystem"

    var lastReport: EnergyGridReport = EnergyGridReport()
        private set

    fun update(entityManager: EntityManager, dtSeconds: Float): EnergyGridReport {
        val energyNodes = entityManager.getEntitiesWith<EnergyConsumption>()
        if (energyNodes.isEmpty()) {
            val emptyReport = EnergyGridReport()
            lastReport = emptyReport
            return emptyReport
        }

        val energyByEntity = LinkedHashMap<EntityId, EnergyConsumption>(energyNodes.size)
        val posByEntity = HashMap<EntityId, GridCoord>(energyNodes.size)
        for ((entity, energy) in energyNodes) {
            energyByEntity[entity] = energy
            entityManager.getComponent<Position>(entity)?.let { posByEntity[entity] = it.gridCoord }
        }

        val grid = entityManager.spatialGrid
        val unifiedGrid = transmissionRadius >= max(grid.width, grid.height)

        // Partition entities into connected electrical networks
        val clusters: List<List<EntityId>> = if (unifiedGrid) {
            listOf(energyNodes.map { it.first })
        } else {
            partitionConnectedNetworks(energyNodes.map { it.first }, posByEntity)
        }

        val networkSnapshots = ArrayList<EnergyNetworkSnapshot>(clusters.size)
        val allocations = HashMap<EntityId, Float>()
        var grandSupply = 0f
        var grandDemand = 0f

        clusters.forEachIndexed { index, members ->
            val producers = ArrayList<EntityId>()
            val consumers = ArrayList<EntityId>()
            var supply = 0f
            var demand = 0f

            for (entity in members) {
                val energy = energyByEntity[entity] ?: continue
                val gen = energy.effectiveGenerationMw.coerceAtLeast(0f)
                val use = energy.effectiveDemandMw.coerceAtLeast(0f)
                if (gen > 0f || energy.isGenerator) {
                    producers.add(entity)
                    supply += gen
                }
                if (use > 0f || energy.isConsumer) {
                    consumers.add(entity)
                    demand += use
                }
            }

            val isBlackout = demand > 0f && supply <= 0f
            val isBrownout = demand > 0f && supply > 0f && supply < demand
            val satisfaction = when {
                demand <= 0f -> 1.0f
                supply >= demand -> 1.0f
                supply <= 0f -> 0.0f
                else -> (supply / demand).coerceIn(0f, 1.0f)
            }
            val appliedEfficiency = when {
                isBlackout -> 0.0f
                isBrownout -> satisfaction.coerceIn(brownoutFloor, 1.0f)
                else -> 1.0f
            }

            for (entity in members) {
                val energy = energyByEntity[entity] ?: continue
                energy.gridEfficiency = appliedEfficiency
                val use = energy.effectiveDemandMw.coerceAtLeast(0f)
                if (use > 0f) {
                    allocations[entity] = use * satisfaction
                }
            }

            grandSupply += supply
            grandDemand += demand
            networkSnapshots.add(
                EnergyNetworkSnapshot(
                    networkId = index + 1,
                    producerEntities = producers,
                    consumerEntities = consumers,
                    totalSupplyMw = supply,
                    totalDemandMw = demand,
                    netMw = supply - demand,
                    satisfactionRatio = satisfaction,
                    isBrownout = isBrownout,
                    isBlackout = isBlackout
                )
            )
        }

        val overallEfficiency = when {
            grandDemand <= 0f -> 1.0f
            grandSupply >= grandDemand -> 1.0f
            grandSupply <= 0f -> 0.0f
            else -> (grandSupply / grandDemand).coerceIn(brownoutFloor, 1.0f)
        }

        val report = EnergyGridReport(
            networks = networkSnapshots,
            totalSupplyMw = grandSupply,
            totalDemandMw = grandDemand,
            netMw = grandSupply - grandDemand,
            overallEfficiency = overallEfficiency,
            consumerAllocations = allocations
        )
        lastReport = report
        return report
    }

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        val report = update(world.entityManager, dtSeconds)
        ctx.powerGeneratedMw = report.totalSupplyMw.roundToInt()
        ctx.powerConsumedMw = report.totalDemandMw.roundToInt()
        ctx.powerEfficiency = if (report.totalSupplyMw <= 0f && report.totalDemandMw > 0f) {
            0.0f
        } else {
            report.overallEfficiency
        }
    }

    private fun partitionConnectedNetworks(
        entities: List<EntityId>,
        posByEntity: Map<EntityId, GridCoord>
    ): List<List<EntityId>> {
        val visited = HashSet<EntityId>(entities.size)
        val result = ArrayList<List<EntityId>>()

        for (start in entities) {
            if (!visited.add(start)) continue
            val startPos = posByEntity[start]
            if (startPos == null) {
                result.add(listOf(start))
                continue
            }

            val component = ArrayList<EntityId>()
            val queue = ArrayDeque<EntityId>()
            queue.add(start)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                component.add(current)
                val currPos = posByEntity[current] ?: continue

                for (candidate in entities) {
                    if (candidate in visited) continue
                    val candPos = posByEntity[candidate] ?: continue
                    if (currPos.chebyshevDistanceTo(candPos) <= transmissionRadius) {
                        visited.add(candidate)
                        queue.add(candidate)
                    }
                }
            }
            result.add(component)
        }
        return result
    }
}

/**
 * Advances production cycles for entities in [EntityManager] carrying both [ProductionRate]
 * and [ResourceStorage], scaling cycle speed by attached [EnergyConsumption.gridEfficiency]
 * and depositing completed yields into each entity's local [ResourceStorage].
 */
class ResourceProductionSystem(
    private val defaultResource: ResourceType = ResourceType.IRON_ORE
) : EcsSystem {
    override val name: String = "ResourceProductionSystem"

    fun update(
        entityManager: EntityManager,
        dtSeconds: Float,
        resourceResolver: ((EntityId, ResourceStorage) -> ResourceType)? = null
    ): Int {
        if (dtSeconds <= 0f) return 0
        var totalProduced = 0

        val producers = entityManager.getEntitiesWith2<ProductionRate, ResourceStorage>()
        for (item in producers) {
            // Specialized planetary extractors & refineries are processed by ExtractorSystem/RefinerySystem
            if (entityManager.hasComponent<ExtractorComponent>(item.entity)) continue
            if (entityManager.hasComponent<RefineryComponent>(item.entity)) continue

            val rate = item.a
            val storage = item.b
            if (rate.baseCycleSeconds <= 0f || rate.outputAmount <= 0) continue

            val powerEfficiency = entityManager.getComponent<EnergyConsumption>(item.entity)?.gridEfficiency ?: 1.0f
            val stepScale = rate.efficiencyMultiplier.coerceAtLeast(0f) * powerEfficiency
            if (stepScale <= 0f) continue

            rate.progressSeconds += dtSeconds * stepScale

            while (rate.progressSeconds >= rate.baseCycleSeconds) {
                val targetResource = resourceResolver?.invoke(item.entity, storage)
                    ?: storage.acceptedResources.firstOrNull()
                    ?: defaultResource

                if (!storage.canAccept(targetResource, 1)) {
                    // Clamp at ready-to-output when local buffer is full
                    rate.progressSeconds = rate.baseCycleSeconds
                    break
                }

                rate.progressSeconds -= rate.baseCycleSeconds
                totalProduced += storage.deposit(targetResource, rate.outputAmount)
            }
        }
        return totalProduced
    }

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        val produced = update(world.entityManager, dtSeconds) { entity, storage ->
            val pos = world.entityManager.getComponent<Position>(entity)
            val tileOre = pos?.let { ctx.tileAt(it.gridX, it.gridY)?.ore }
            tileOre ?: storage.acceptedResources.firstOrNull() ?: defaultResource
        }
        if (produced > 0) {
            ctx.throughputWindowCount += produced
        }
    }
}

/**
 * Manages rate-limited resource transfers between connected entities carrying [Position]
 * and [Inventory] components in [EntityManager.spatialGrid].
 * Supports directional chains (Extractor -> Conveyor -> Refinery -> Hub), Omni-Router
 * 3-way round-robin splitting, input/output resource filters, power scaling, and backpressure
 * when downstream inventories are full.
 */
class InventorySystem : EcsSystem {
    override val name: String = "InventorySystem"

    fun update(entityManager: EntityManager, dtSeconds: Float): Int {
        if (dtSeconds <= 0f) return 0
        var totalTransferred = 0
        val spatialGrid = entityManager.spatialGrid
        val nodes = entityManager.getEntitiesWith2<Position, Inventory>()

        for (item in nodes) {
            val entity = item.entity
            val pos = item.a
            val inv = item.b
            if (inv.transferMode == TransferMode.INPUT_ONLY || inv.transferRatePerSecond <= 0f) continue

            val powerEfficiency = entityManager.getComponent<EnergyConsumption>(entity)?.gridEfficiency ?: 1.0f
            val rate = inv.transferRatePerSecond * powerEfficiency
            if (rate <= 0f) continue

            if (inv.isEmpty) {
                // Keep accumulator primed up to 1 item without unbounded idle buildup
                inv.transferAccumulator = (inv.transferAccumulator + dtSeconds * rate).coerceAtMost(1.0f)
                continue
            }

            inv.transferAccumulator += dtSeconds * rate
            val isRouter = entityManager.getComponent<ConveyorComponent>(entity)?.isRouter == true

            while (inv.transferAccumulator >= 1.0f) {
                val resource = inv.nextExportableResource() ?: break
                val candidateDirs = if (isRouter) {
                    val base = pos.direction
                    val all = listOf(
                        base,
                        base.rotateClockwise(),
                        base.rotateClockwise().rotateClockwise().rotateClockwise()
                    )
                    val start = inv.routerIndex % all.size
                    List(all.size) { idx -> all[(start + idx) % all.size] }
                } else {
                    listOf(pos.direction)
                }

                var transferredThisStep = false
                for ((attemptIdx, dir) in candidateDirs.withIndex()) {
                    val neighborEntity = spatialGrid.neighborInDirection(pos.gridCoord, dir) ?: continue
                    val targetInv = entityManager.getComponent<Inventory>(neighborEntity)
                    if (targetInv != null && targetInv.canReceive(resource, 1)) {
                        val moved = inv.transferTo(targetInv, resource, 1)
                        if (moved > 0) {
                            // Keep legacy ResourceStorage mirrored if present on sink/buffer
                            entityManager.getComponent<ResourceStorage>(neighborEntity)?.deposit(resource, moved)
                            totalTransferred += moved
                            inv.transferAccumulator -= 1.0f
                            if (isRouter) {
                                inv.routerIndex = (inv.routerIndex + attemptIdx + 1) % candidateDirs.size
                            }
                            transferredThisStep = true
                            break
                        }
                    } else if (targetInv == null) {
                        val targetStorage = entityManager.getComponent<ResourceStorage>(neighborEntity)
                        if (targetStorage != null && targetStorage.canAccept(resource, 1)) {
                            val withdrawn = inv.remove(resource, 1)
                            if (withdrawn > 0) {
                                val deposited = targetStorage.deposit(resource, withdrawn)
                                totalTransferred += deposited
                                inv.transferAccumulator -= 1.0f
                                if (isRouter) {
                                    inv.routerIndex = (inv.routerIndex + attemptIdx + 1) % candidateDirs.size
                                }
                                transferredThisStep = true
                                break
                            }
                        }
                    }
                }

                if (!transferredThisStep) {
                    // Downstream blocked or full: apply backpressure cleanly
                    inv.transferAccumulator = 1.0f
                    break
                }
            }
        }
        return totalTransferred
    }

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        val moved = update(world.entityManager, dtSeconds)
        if (moved > 0) {
            ctx.throughputWindowCount += moved
        }
    }
}

// ============================================================================
// Specialized Planetary Logistics, Power & Hazard Systems
// ============================================================================

/**
 * System 1: Evaluates Thermal Shield Pylon, Atmospheric Scrubber, and Rail-Turret radii
 * onto every structure's [CoverageStateComponent].
 */
class ProtectionCoverageSystem : EcsSystem {
    override val name: String = "ProtectionCoverageSystem"

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        // Reset all coverage flags first
        val coverages = world.query<CoverageStateComponent>()
        for ((_, coverage) in coverages) {
            coverage.shieldedThermal = false
            coverage.shieldedScrubber = false
            coverage.protectedTurret = false
        }

        // Apply each active emitter using SpatialGrid radius query (O(emitters * radius^2))
        val emitters = world.query3<TransformComponent, StructureComponent, ProtectionFieldComponent>()
            .filter { it.b.integrity > 0 }
        for (emitter in emitters) {
            val center = GridCoord(emitter.a.x, emitter.a.y)
            val coveredEntities = world.spatialGrid.entitiesInRadius(center, emitter.c.radius)
            for (targetEntity in coveredEntities) {
                val coverage = world.get<CoverageStateComponent>(targetEntity) ?: continue
                when (emitter.c.kind) {
                    ProtectionKind.THERMAL -> coverage.shieldedThermal = true
                    ProtectionKind.SCRUBBER -> coverage.shieldedScrubber = true
                    ProtectionKind.TURRET -> coverage.protectedTurret = true
                }
            }
        }
    }
}

/**
 * System 2: Calculates planetary power generation vs demand (including biome solar/geothermal
 * modifiers and hazard thermal spikes), burns periodic fuel, and sets global [SimulationContext.powerEfficiency].
 */
class PowerGridSystem : EcsSystem {
    override val name: String = "PowerGridSystem"

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        val biome = BiomeCatalog.byId(ctx.planetId)
        val hazard = ctx.activeHazard

        // Periodic fuel consumption every 5.0 simulation seconds
        ctx.fuelBurnAccumulator += dtSeconds
        val burnFuelNow = ctx.fuelBurnAccumulator >= 5.0f
        if (burnFuelNow) ctx.fuelBurnAccumulator -= 5.0f

        val hasCoal = (ctx.inventory[ResourceType.COAL] ?: 0) > 0
        val hasFuelCell = (ctx.inventory[ResourceType.FUEL_CELL] ?: 0) > 0
        val solarMult = biome.solarMultiplier *
            (hazard?.solarOutputMultiplier ?: 1.0f) *
            (ctx.activeEvent?.solarMultiplier ?: 1.0f)

        var totalGen = 0
        var totalUse = 0

        val powerNodes = world.query3<TransformComponent, StructureComponent, PowerNodeComponent>()
        for (node in powerNodes) {
            val type = node.b.buildingType
            val coverage = world.get<CoverageStateComponent>(node.entity)
            if (node.c.baseMw > 0) {
                val tile = ctx.tileAt(node.a.x, node.a.y)
                val output = when (type) {
                    BuildingType.COAL_GENERATOR -> {
                        if (burnFuelNow && hasCoal) {
                            ctx.inventory[ResourceType.COAL] = ((ctx.inventory[ResourceType.COAL] ?: 0) - 1).coerceAtLeast(0)
                        }
                        if (hasCoal) node.c.baseMw else 6
                    }
                    BuildingType.SOLAR_COLLECTOR -> (node.c.baseMw * solarMult).toInt().coerceAtLeast(3)
                    BuildingType.GEOTHERMAL_TURBINE -> {
                        val ventBonus = if (tile?.geothermalVent == true) 35 else 0
                        node.c.baseMw + biome.geothermalBonusMw + ventBonus
                    }
                    BuildingType.FUSION_CORE -> {
                        if (burnFuelNow && hasFuelCell && ctx.tickCount % 2L == 0L) {
                            ctx.inventory[ResourceType.FUEL_CELL] = ((ctx.inventory[ResourceType.FUEL_CELL] ?: 0) - 1).coerceAtLeast(0)
                        }
                        if (hasFuelCell) node.c.baseMw else 50
                    }
                    else -> node.c.baseMw
                }
                node.c.effectiveMw = output
                world.get<EnergyConsumption>(node.entity)?.let { energy ->
                    energy.effectiveGenerationMw = output.toFloat()
                    energy.effectiveDemandMw = 0f
                }
                totalGen += output
            } else {
                val baseDraw = -node.c.baseMw
                val inThermal = coverage?.shieldedThermal == true
                val demandMult = if (hazard != null && !inThermal && hazard.mitigatedBy == BuildingType.THERMAL_PYLON) {
                    hazard.powerDemandMultiplier
                } else {
                    1.0f
                }
                val draw = (baseDraw * demandMult).toInt()
                node.c.effectiveMw = -draw
                world.get<EnergyConsumption>(node.entity)?.let { energy ->
                    energy.effectiveDemandMw = draw.toFloat()
                    energy.effectiveGenerationMw = 0f
                }
                totalUse += draw
            }
        }

        ctx.powerGeneratedMw = totalGen
        ctx.powerConsumedMw = totalUse
        ctx.powerEfficiency = when {
            totalUse <= 0 -> 1.0f
            totalGen >= totalUse -> 1.0f
            else -> (totalGen.toFloat() / totalUse.toFloat()).coerceIn(0.25f, 1.0f)
        }
        world.query<EnergyConsumption>().forEach { (_, energy) ->
            energy.gridEfficiency = ctx.powerEfficiency
        }
    }
}

/**
 * System 3: Advances all [CargoPacketComponent] entities across [ConveyorComponent] networks,
 * handles Omni-Router 3-way distribution, and delivers cargo into Hubs, Silos, and Refineries.
 */
class ConveyorLogisticsSystem : EcsSystem {
    override val name: String = "ConveyorLogisticsSystem"

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        val hazard = ctx.activeHazard
        val eventSpeedMult = ctx.activeEvent?.factorySpeedMultiplier ?: 1.0f
        val packets = world.query<CargoPacketComponent>()

        val occupiedTiles = HashSet<Pair<Int, Int>>(packets.size * 2)
        packets.forEach { (_, pkt) -> occupiedTiles.add(pkt.gridX to pkt.gridY) }

        val toDestroy = mutableListOf<EntityId>()

        for ((pktEntity, pkt) in packets) {
            val beltEntity = world.buildingEntityAt(pkt.gridX, pkt.gridY)
            val conveyor = beltEntity?.let { world.get<ConveyorComponent>(it) }
            val transform = beltEntity?.let { world.get<TransformComponent>(it) }

            if (beltEntity == null || conveyor == null || transform == null) {
                ctx.inventory[pkt.resource] = (ctx.inventory[pkt.resource] ?: 0) + 1
                ctx.throughputWindowCount++
                toDestroy.add(pktEntity)
                continue
            }

            val coverage = world.get<CoverageStateComponent>(beltEntity)
            val hazardBeltPenalty = if (hazard != null) {
                val immune = (conveyor.cryoImmune && hazard.id == "cryo_blizzard") ||
                    (hazard.mitigatedBy == BuildingType.THERMAL_PYLON && coverage?.shieldedThermal == true) ||
                    (hazard.mitigatedBy == BuildingType.ATMOS_SCRUBBER && coverage?.shieldedScrubber == true)
                if (immune) 1.0f else hazard.beltSpeedMultiplier
            } else 1.0f

            val effectiveSpeed = conveyor.tilesPerSecond * ctx.powerEfficiency * hazardBeltPenalty * eventSpeedMult
            pkt.progress += effectiveSpeed * dtSeconds

            // Keep core Position and Velocity components synced for moving cargo packets
            world.get<Velocity>(pktEntity)?.let { vel ->
                vel.dx = transform.direction.dx * effectiveSpeed
                vel.dy = transform.direction.dy * effectiveSpeed
                vel.speedTilesPerSec = effectiveSpeed
            }
            world.get<Position>(pktEntity)?.let { pos ->
                val clamped = pkt.progress.coerceIn(0f, 1f)
                pos.x = pkt.fromX + (pkt.gridX - pkt.fromX) * clamped
                pos.y = pkt.fromY + (pkt.gridY - pkt.fromY) * clamped
                pos.gridX = pkt.gridX
                pos.gridY = pkt.gridY
                pos.direction = transform.direction
            }

            if (pkt.progress >= 1.0f) {
                val outDir = if (conveyor.isRouter) {
                    val dirs = listOf(
                        transform.direction,
                        transform.direction.rotateClockwise(),
                        transform.direction.rotateClockwise().rotateClockwise().rotateClockwise()
                    )
                    val chosen = dirs[conveyor.routerTurnIndex % dirs.size]
                    conveyor.routerTurnIndex = (conveyor.routerTurnIndex + 1) % dirs.size
                    chosen
                } else {
                    transform.direction
                }

                val nx = transform.x + outDir.dx
                val ny = transform.y + outDir.dy
                val nextEntity = world.buildingEntityAt(nx, ny)
                val nextStruct = nextEntity?.let { world.get<StructureComponent>(it) }

                when {
                    nextEntity == null || nextStruct == null -> {
                        pkt.progress = 0.95f
                    }
                    nextStruct.buildingType == BuildingType.ORBITAL_HUB ||
                        nextStruct.buildingType == BuildingType.STORAGE_SILO ||
                        nextStruct.buildingType.isProducer() -> {
                        world.get<ResourceStorage>(nextEntity)?.deposit(pkt.resource, 1)
                        ctx.inventory[pkt.resource] = (ctx.inventory[pkt.resource] ?: 0) + 1
                        ctx.throughputWindowCount++
                        occupiedTiles.remove(pkt.gridX to pkt.gridY)
                        toDestroy.add(pktEntity)
                    }
                    world.has<ConveyorComponent>(nextEntity) && (nx to ny) !in occupiedTiles -> {
                        occupiedTiles.remove(pkt.gridX to pkt.gridY)
                        pkt.fromX = pkt.gridX
                        pkt.fromY = pkt.gridY
                        pkt.gridX = nx
                        pkt.gridY = ny
                        pkt.progress = 0.05f
                        world.get<Position>(pktEntity)?.let { pos ->
                            pos.gridX = nx
                            pos.gridY = ny
                            pos.x = pkt.fromX + (nx - pkt.fromX) * pkt.progress
                            pos.y = pkt.fromY + (ny - pkt.fromY) * pkt.progress
                            pos.direction = outDir
                        }
                        occupiedTiles.add(nx to ny)
                    }
                    else -> {
                        pkt.progress = 0.95f
                    }
                }
            }
        }

        toDestroy.forEach { world.destroyEntity(it) }
    }
}

/**
 * System 4: Runs [ExtractorComponent] mining cycles on mineral veins and spawns
 * [CargoPacketComponent] entities onto front conveyor belts (or delivers directly to storage).
 */
class ExtractorSystem : EcsSystem {
    override val name: String = "ExtractorSystem"

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        val vanguardBonus = if ("t5_vanguard" in ctx.unlockedTechs) 1.35f else 1.0f
        val eventSpeedMult = ctx.activeEvent?.factorySpeedMultiplier ?: 1.0f
        val hazard = ctx.activeHazard

        val existingPackets = world.query<CargoPacketComponent>()
        val occupiedBeltTiles = existingPackets.mapTo(HashSet()) { it.second.gridX to it.second.gridY }
        var packetCount = existingPackets.size

        val extractors = world.query3<TransformComponent, StructureComponent, ExtractorComponent>()
        for (item in extractors) {
            if (item.b.integrity <= 0) continue
            val tile = ctx.tileAt(item.a.x, item.a.y)
            val ore = tile?.ore
            if (ore == null) {
                item.b.statusNote = "NO VEIN"
                continue
            }

            val coverage = world.get<CoverageStateComponent>(item.entity)
            val scarcityMult = if (ctx.activeEvent?.scarcityResource == ore) {
                ctx.activeEvent?.drillYieldMultiplier ?: 1.0f
            } else 1.0f
            val hazardDrillMult = if (hazard != null &&
                !(hazard.mitigatedBy == BuildingType.ATMOS_SCRUBBER && coverage?.shieldedScrubber == true) &&
                !(hazard.mitigatedBy == BuildingType.THERMAL_PYLON && coverage?.shieldedThermal == true)
            ) hazard.drillOutputMultiplier else 1.0f

            val rateFactor = ctx.powerEfficiency * scarcityMult * hazardDrillMult * vanguardBonus * eventSpeedMult
            item.c.progressSeconds += dtSeconds * rateFactor
            world.get<ProductionRate>(item.entity)?.let { prod ->
                prod.efficiencyMultiplier = rateFactor
                prod.progressSeconds = item.c.progressSeconds
            }
            item.b.statusNote = when {
                scarcityMult < 0.9f -> "SCARCITY"
                hazardDrillMult < 0.9f -> "HAZARD"
                ctx.powerEfficiency < 0.85f -> "LOW PWR"
                else -> "MINING ${ore.code}"
            }

            if (item.c.progressSeconds >= item.c.baseCycleSeconds) {
                item.c.progressSeconds -= item.c.baseCycleSeconds
                world.get<ProductionRate>(item.entity)?.progressSeconds = item.c.progressSeconds
                val fx = item.a.x + item.a.direction.dx
                val fy = item.a.y + item.a.direction.dy
                val frontEntity = world.buildingEntityAt(fx, fy)
                if (frontEntity != null &&
                    world.has<ConveyorComponent>(frontEntity) &&
                    (fx to fy) !in occupiedBeltTiles &&
                    packetCount < MAX_ACTIVE_CARGO_PACKETS
                ) {
                    PlanetaryEntityFactory.spawnCargoPacket(
                        world = world,
                        ctx = ctx,
                        resource = ore,
                        fromX = item.a.x,
                        fromY = item.a.y,
                        gridX = fx,
                        gridY = fy
                    )
                    occupiedBeltTiles.add(fx to fy)
                    packetCount++
                    if (item.c.outputAmount > 1) {
                        ctx.inventory[ore] = (ctx.inventory[ore] ?: 0) + (item.c.outputAmount - 1)
                        ctx.throughputWindowCount += (item.c.outputAmount - 1)
                    }
                } else {
                    world.get<ResourceStorage>(item.entity)?.deposit(ore, item.c.outputAmount)
                    ctx.inventory[ore] = (ctx.inventory[ore] ?: 0) + item.c.outputAmount
                    ctx.throughputWindowCount += item.c.outputAmount
                }
            }
        }
    }

    companion object {
        private const val MAX_ACTIVE_CARGO_PACKETS = 48
    }
}

/**
 * System 5: Processes [RefineryComponent] recipes on Smelters, Foundries, Assemblers,
 * and Quantum Manufacturers, applying adjacent belt boosts and outputting crafted cargo packets.
 */
class RefinerySystem : EcsSystem {
    override val name: String = "RefinerySystem"

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        val vanguardBonus = if ("t5_vanguard" in ctx.unlockedTechs) 1.35f else 1.0f
        val eventSpeedMult = ctx.activeEvent?.factorySpeedMultiplier ?: 1.0f
        val hazard = ctx.activeHazard

        val existingPackets = world.query<CargoPacketComponent>()
        val occupiedBeltTiles = existingPackets.mapTo(HashSet()) { it.second.gridX to it.second.gridY }
        var packetCount = existingPackets.size

        val refineries = world.query3<TransformComponent, StructureComponent, RefineryComponent>()
        for (item in refineries) {
            if (item.b.integrity <= 0) continue

            val recipe = RecipeCatalog.byId(item.c.recipeId)
                ?: RecipeCatalog.forMachine(item.b.buildingType, ctx.unlockedTechs).firstOrNull()
            if (recipe == null) {
                item.b.statusNote = "IDLE"
                continue
            }
            item.c.recipeId = recipe.id

            val hasInputs = recipe.inputs.all { (res, qty) -> (ctx.inventory[res] ?: 0) >= qty }
            if (!hasInputs) {
                item.b.statusNote = "STARVED"
                continue
            }

            // Adjacent conveyor/hub boost via SpatialGrid neighborhood query
            val beltConnected = world.spatialGrid
                .adjacentEntities(GridCoord(item.a.x, item.a.y))
                .any { (_, neighbor) ->
                    val nType = world.get<StructureComponent>(neighbor)?.buildingType
                    nType != null && (nType.isConveyor() || nType == BuildingType.ORBITAL_HUB)
                }
            item.c.beltBoosted = beltConnected

            val coverage = world.get<CoverageStateComponent>(item.entity)
            val hazardSlow = if (hazard != null && coverage?.shieldedThermal != true && coverage?.shieldedScrubber != true) {
                0.75f
            } else 1.0f

            val beltMult = if (beltConnected) 1.45f else 1.0f
            val cycleDurationSec = recipe.ticksRequired * 0.2f
            val effectiveEfficiency = ctx.powerEfficiency * beltMult * hazardSlow * vanguardBonus * eventSpeedMult
            item.c.progressSeconds += dtSeconds * effectiveEfficiency
            world.get<ProductionRate>(item.entity)?.let { prod ->
                prod.baseCycleSeconds = cycleDurationSec
                prod.outputAmount = recipe.outputAmount
                prod.efficiencyMultiplier = effectiveEfficiency
                prod.progressSeconds = item.c.progressSeconds
            }

            item.b.statusNote = when {
                hazardSlow < 1f -> "EXPOSED"
                beltConnected -> "ONLINE+"
                else -> "ONLINE"
            }

            if (item.c.progressSeconds >= cycleDurationSec) {
                item.c.progressSeconds -= cycleDurationSec
                world.get<ProductionRate>(item.entity)?.progressSeconds = item.c.progressSeconds
                recipe.inputs.forEach { (res, qty) ->
                    ctx.inventory[res] = ((ctx.inventory[res] ?: 0) - qty).coerceAtLeast(0)
                }

                val fx = item.a.x + item.a.direction.dx
                val fy = item.a.y + item.a.direction.dy
                val frontEntity = world.buildingEntityAt(fx, fy)
                if (frontEntity != null &&
                    world.has<ConveyorComponent>(frontEntity) &&
                    (fx to fy) !in occupiedBeltTiles &&
                    packetCount < 48
                ) {
                    PlanetaryEntityFactory.spawnCargoPacket(
                        world = world,
                        ctx = ctx,
                        resource = recipe.output,
                        fromX = item.a.x,
                        fromY = item.a.y,
                        gridX = fx,
                        gridY = fy
                    )
                    occupiedBeltTiles.add(fx to fy)
                    packetCount++
                    if (recipe.outputAmount > 1) {
                        ctx.inventory[recipe.output] = (ctx.inventory[recipe.output] ?: 0) + (recipe.outputAmount - 1)
                        ctx.throughputWindowCount += (recipe.outputAmount - 1)
                    }
                } else {
                    world.get<ResourceStorage>(item.entity)?.deposit(recipe.output, recipe.outputAmount)
                    ctx.inventory[recipe.output] = (ctx.inventory[recipe.output] ?: 0) + recipe.outputAmount
                    ctx.throughputWindowCount += recipe.outputAmount
                }
            }
        }
    }
}

/**
 * System 6: Applies active biome hazard wear, wildlife migration raids, turret laser beams,
 * and rolling throughput telemetry (items/min).
 */
class HazardAndDefenseSystem : EcsSystem {
    override val name: String = "HazardAndDefenseSystem"

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        // Decay active turret laser beam entities
        val beams = world.query<TurretBeamComponent>()
        val expiredBeams = mutableListOf<EntityId>()
        for ((beamEntity, beam) in beams) {
            beam.ttlSeconds -= dtSeconds
            if (beam.ttlSeconds <= 0f) expiredBeams.add(beamEntity)
        }
        expiredBeams.forEach { world.destroyEntity(it) }

        // Active biome environmental hazard timer & structural wear
        val hazard = ctx.activeHazard
        if (hazard != null) {
            ctx.hazardSecondsRemaining = (ctx.hazardSecondsRemaining - dtSeconds).coerceAtLeast(0f)
            ctx.hazardDamageAccumulator += dtSeconds
            if (ctx.hazardDamageAccumulator >= 3.0f && hazard.integrityDamagePerCycle > 0) {
                ctx.hazardDamageAccumulator -= 3.0f
                val armorMult = if ("t2_hazards" in ctx.unlockedTechs) 0.8f else 1.0f
                val structures = world.query3<TransformComponent, StructureComponent, CoverageStateComponent>()
                for (item in structures) {
                    val t = item.b.buildingType
                    if (t == BuildingType.ORBITAL_HUB || t == BuildingType.THERMAL_PYLON || t == BuildingType.ATMOS_SCRUBBER) continue
                    val protected = (hazard.mitigatedBy == BuildingType.THERMAL_PYLON && item.c.shieldedThermal) ||
                        (hazard.mitigatedBy == BuildingType.ATMOS_SCRUBBER && item.c.shieldedScrubber)
                    if (!protected) {
                        val lavaAdjacent = hazard.id == "lava_surge" && Direction.entries.any { d ->
                            ctx.tileAt(item.a.x + d.dx, item.a.y + d.dy)?.hazardTerrain == true
                        }
                        val extra = if (lavaAdjacent) 3 else 0
                        val dmg = max(1, ((hazard.integrityDamagePerCycle + extra) * armorMult).toInt())
                        item.b.integrity = (item.b.integrity - dmg).coerceAtLeast(15)
                        item.b.statusNote = "HAZARD DMG"
                    }
                }
            }
            if (ctx.hazardSecondsRemaining <= 0f) {
                ctx.activeHazard = null
            }
        }

        // Wildlife migration attacks & Rail-Turret interception
        if (ctx.activeEvent?.wildlifeRaidActive == true) {
            ctx.wildlifeRaidAccumulator += dtSeconds
            if (ctx.wildlifeRaidAccumulator >= 2.0f) {
                ctx.wildlifeRaidAccumulator -= 2.0f
                val structures = world.query3<TransformComponent, StructureComponent, CoverageStateComponent>()
                val rng = Random(ctx.tickCount)
                for (item in structures) {
                    if (item.b.buildingType == BuildingType.AUTO_TURRET && item.b.integrity > 0) {
                        val angle = rng.nextFloat() * 6.283185f
                        val tx = (item.a.x + cos(angle) * 3.2f).coerceIn(0f, (GRID_SIZE - 1).toFloat())
                        val ty = (item.a.y + sin(angle) * 3.2f).coerceIn(0f, (GRID_SIZE - 1).toFloat())
                        val beamEntity = world.createEntity()
                        world.add(beamEntity, TurretBeamComponent(item.a.x, item.a.y, tx, ty, ttlSeconds = 0.7f))
                    } else if (item.b.buildingType != BuildingType.ORBITAL_HUB && !item.c.protectedTurret) {
                        item.b.integrity = (item.b.integrity - 3).coerceAtLeast(15)
                        item.b.statusNote = "UNDER ATTACK"
                    }
                }
            }
        }

        // Rolling 5-second throughput meter (items/min)
        ctx.throughputTimerSeconds += dtSeconds
        if (ctx.throughputTimerSeconds >= 5.0f) {
            ctx.itemsPerMinute = ctx.throughputWindowCount * 12
            ctx.throughputWindowCount = 0
            ctx.throughputTimerSeconds -= 5.0f
        }
    }
}
