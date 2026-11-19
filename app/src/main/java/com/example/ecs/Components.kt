package com.example.ecs

import com.example.data.model.BuildingType
import com.example.data.model.Direction
import com.example.data.model.RecipeCatalog
import com.example.data.model.ResourceType
import com.example.data.model.UnitArchetype
import com.example.data.model.UnitFaction
import kotlin.math.hypot

enum class ProtectionKind { THERMAL, SCRUBBER, TURRET }

// ============================================================================
// Core ECS Data Components
// ============================================================================

/**
 * Spatial coordinates and facing direction of any entity on the planetary grid.
 * Supports both continuous world-space coordinates (`x`, `y`) for moving packets/projectiles
 * and discrete grid coordinates (`gridX`, `gridY`) for stationary structures.
 */
data class Position(
    var x: Float,
    var y: Float,
    var gridX: Int = x.toInt(),
    var gridY: Int = y.toInt(),
    var direction: Direction = Direction.NORTH,
    val footprint: GridFootprint = GridFootprint.SINGLE
) : Component {
    constructor(
        gridX: Int,
        gridY: Int,
        direction: Direction = Direction.NORTH,
        footprint: GridFootprint = GridFootprint.SINGLE
    ) : this(
        x = gridX.toFloat(),
        y = gridY.toFloat(),
        gridX = gridX,
        gridY = gridY,
        direction = direction,
        footprint = footprint
    )

    val gridCoord: GridCoord
        get() = GridCoord(gridX, gridY)

    fun frontCoord(): GridCoord = gridCoord.step(direction)

    fun toWorldPoint(tileSize: Float = 1.0f): WorldPoint =
        WorldPoint(x * tileSize, y * tileSize)

    fun toWorldCenter(tileSize: Float = 1.0f): WorldPoint =
        gridCoord.toWorldCenter(tileSize)
}

/**
 * 2D translational velocity (tiles/second) for moving entities such as conveyor cargo packets,
 * belt propulsion vectors, drones, or turret projectiles.
 */
data class Velocity(
    var dx: Float = 0f,
    var dy: Float = 0f,
    var speedTilesPerSec: Float = 0f
) : Component {
    val magnitude: Float
        get() = hypot(dx, dy)
}

/**
 * Local resource buffer / inventory attached to Orbital Hubs, Storage Silos, Extractors,
 * and Refineries with bounded capacity and optional resource-type filtering.
 */
data class ResourceStorage(
    val capacity: Int,
    val stored: MutableMap<ResourceType, Int> = mutableMapOf(),
    val acceptedResources: Set<ResourceType> = emptySet() // Empty set accepts any ResourceType
) : Component {
    val totalStored: Int
        get() = stored.values.sum()

    val availableCapacity: Int
        get() = (capacity - totalStored).coerceAtLeast(0)

    fun quantityOf(resource: ResourceType): Int = stored[resource] ?: 0

    fun canAccept(resource: ResourceType, amount: Int = 1): Boolean {
        if (amount <= 0) return false
        if (acceptedResources.isNotEmpty() && resource !in acceptedResources) return false
        return availableCapacity >= amount
    }

    /** Deposits up to [amount] units and returns the actual quantity stored. */
    fun deposit(resource: ResourceType, amount: Int): Int {
        if (amount <= 0) return 0
        if (acceptedResources.isNotEmpty() && resource !in acceptedResources) return 0
        val added = amount.coerceAtMost(availableCapacity)
        if (added > 0) {
            stored[resource] = (stored[resource] ?: 0) + added
        }
        return added
    }

    /** Withdraws up to [amount] units and returns the actual quantity removed. */
    fun withdraw(resource: ResourceType, amount: Int): Int {
        if (amount <= 0) return 0
        val current = stored[resource] ?: 0
        val removed = amount.coerceAtMost(current)
        if (removed > 0) {
            val remaining = current - removed
            if (remaining == 0) stored.remove(resource) else stored[resource] = remaining
        }
        return removed
    }
}

/**
 * Electrical power load and/or generation profile of an entity connected to the planetary power bus.
 * Tracks both nominal base MW and live effective MW after biome, hazard, and fuel modifiers.
 */
data class EnergyConsumption(
    val baseDemandMw: Float = 0f,
    var effectiveDemandMw: Float = baseDemandMw,
    val baseGenerationMw: Float = 0f,
    var effectiveGenerationMw: Float = baseGenerationMw,
    var gridEfficiency: Float = 1.0f
) : Component {
    val isConsumer: Boolean
        get() = baseDemandMw > 0f

    val isGenerator: Boolean
        get() = baseGenerationMw > 0f

    val netEffectiveMw: Float
        get() = effectiveGenerationMw - effectiveDemandMw
}

/**
 * Production throughput telemetry and cycle accumulator for extractors, smelters,
 * foundries, assemblers, and quantum manufacturers.
 */
data class ProductionRate(
    var baseCycleSeconds: Float,
    var outputAmount: Int = 1,
    var efficiencyMultiplier: Float = 1.0f,
    var progressSeconds: Float = 0f
) : Component {
    val baseItemsPerSecond: Float
        get() = if (baseCycleSeconds > 0f) outputAmount / baseCycleSeconds else 0f

    val effectiveItemsPerSecond: Float
        get() = baseItemsPerSecond * efficiencyMultiplier.coerceAtLeast(0f)

    val effectiveItemsPerMinute: Float
        get() = effectiveItemsPerSecond * 60f
}

/**
 * Logistics port role controlling how [InventorySystem] transfers items across connected grid tiles.
 */
enum class TransferMode {
    OUTPUT_ONLY,
    PASS_THROUGH,
    INPUT_OUTPUT,
    INPUT_ONLY
}

/**
 * Grid-connected entity inventory supporting bounded capacity, optional input/output resource filters,
 * transfer throughput rate (items/second), and atomic item transfers between neighboring entities.
 */
data class Inventory(
    val capacity: Int,
    val items: MutableMap<ResourceType, Int> = mutableMapOf(),
    val transferMode: TransferMode = TransferMode.INPUT_OUTPUT,
    val inputFilter: Set<ResourceType> = emptySet(),
    val outputFilter: Set<ResourceType> = emptySet(),
    val transferRatePerSecond: Float = 2.0f,
    var transferAccumulator: Float = 0f,
    var routerIndex: Int = 0
) : Component {
    val totalItems: Int
        get() = items.values.sum()

    val freeSpace: Int
        get() = (capacity - totalItems).coerceAtLeast(0)

    val isEmpty: Boolean
        get() = totalItems == 0

    val isFull: Boolean
        get() = freeSpace == 0

    fun countOf(resource: ResourceType): Int = items[resource] ?: 0

    fun canReceive(resource: ResourceType, amount: Int = 1): Boolean {
        if (amount <= 0) return false
        if (transferMode == TransferMode.OUTPUT_ONLY) return false
        if (inputFilter.isNotEmpty() && resource !in inputFilter) return false
        return freeSpace >= amount
    }

    fun canExport(): Boolean = transferMode != TransferMode.INPUT_ONLY && nextExportableResource() != null

    fun nextExportableResource(): ResourceType? {
        if (transferMode == TransferMode.INPUT_ONLY) return null
        for ((resource, qty) in items) {
            if (qty <= 0) continue
            if (outputFilter.isEmpty() || resource in outputFilter) {
                return resource
            }
        }
        return null
    }

    /** Adds up to [amount] units respecting capacity and (optionally) [inputFilter]. */
    fun add(resource: ResourceType, amount: Int, enforceInputFilter: Boolean = true): Int {
        if (amount <= 0) return 0
        if (enforceInputFilter && inputFilter.isNotEmpty() && resource !in inputFilter) return 0
        val added = amount.coerceAtMost(freeSpace)
        if (added > 0) {
            items[resource] = (items[resource] ?: 0) + added
        }
        return added
    }

    /** Removes up to [amount] units of [resource] and returns the actual quantity withdrawn. */
    fun remove(resource: ResourceType, amount: Int): Int {
        if (amount <= 0) return 0
        val current = items[resource] ?: 0
        val removed = amount.coerceAtMost(current)
        if (removed > 0) {
            val remaining = current - removed
            if (remaining == 0) items.remove(resource) else items[resource] = remaining
        }
        return removed
    }

    /**
     * Atomically transfers up to [maxAmount] units of [resource] from this inventory into [target],
     * respecting source stock, target transfer mode, target input filter, and target free capacity.
     */
    fun transferTo(target: Inventory, resource: ResourceType, maxAmount: Int = 1): Int {
        if (maxAmount <= 0) return 0
        if (transferMode == TransferMode.INPUT_ONLY) return 0
        if (outputFilter.isNotEmpty() && resource !in outputFilter) return 0
        if (target.transferMode == TransferMode.OUTPUT_ONLY) return 0
        if (target.inputFilter.isNotEmpty() && resource !in target.inputFilter) return 0

        val available = countOf(resource)
        val transferable = minOf(maxAmount, available, target.freeSpace)
        if (transferable <= 0) return 0

        val removed = remove(resource, transferable)
        return target.add(resource, removed, enforceInputFilter = true)
    }
}

// ============================================================================
// Specialized Planetary Logistics & Defense Components
// ============================================================================

/** Grid position and facing orientation of a planetary structure. */
data class TransformComponent(
    val x: Int,
    val y: Int,
    var direction: Direction
) : Component

/** Structural identity, hull health, and live telemetry status of a placed building. */
data class StructureComponent(
    val buildingType: BuildingType,
    var integrity: Int,
    val maxIntegrity: Int,
    var statusNote: String = "ONLINE"
) : Component

/** Resource extractor state (Kinetic Drill Mk.1, Deep Core Drill Mk.2, Cryo/Thermal Pump). */
data class ExtractorComponent(
    var progressSeconds: Float = 0f,
    val baseCycleSeconds: Float,
    val outputAmount: Int = 1
) : Component

/** Conveyor belt or Omni-Router logistics component. */
data class ConveyorComponent(
    val tilesPerSecond: Float,
    val isRouter: Boolean = false,
    var routerTurnIndex: Int = 0,
    val cryoImmune: Boolean = false
) : Component

/** Refinery / Smelter / Foundry / Assembler / Quantum Manufacturer fabrication state. */
data class RefineryComponent(
    var recipeId: String,
    var progressSeconds: Float = 0f,
    var beltBoosted: Boolean = false
) : Component

/** Power generator or consumer node attached to the planetary electrical bus. */
data class PowerNodeComponent(
    val baseMw: Int, // Positive = generates MW, Negative = consumes MW
    var effectiveMw: Int = baseMw
) : Component

/** Area-of-effect protective field emitter (Thermal Shield Pylon, Scrubber, Rail-Turret). */
data class ProtectionFieldComponent(
    val radius: Int,
    val kind: ProtectionKind
) : Component

/** Per-structure environmental & perimeter coverage flags updated by ProtectionCoverageSystem. */
data class CoverageStateComponent(
    var shieldedThermal: Boolean = false,
    var shieldedScrubber: Boolean = false,
    var protectedTurret: Boolean = false
) : Component

/** First-class ECS entity representing an individual item packet moving across conveyor belts. */
data class CargoPacketComponent(
    val packetId: Long,
    val resource: ResourceType,
    var fromX: Int,
    var fromY: Int,
    var gridX: Int,
    var gridY: Int,
    var progress: Float = 0.05f
) : Component

/** Transient ECS visual/combat entity representing a Rail-Turret defensive shot. */
data class TurretBeamComponent(
    val fromX: Int,
    val fromY: Int,
    val targetX: Float,
    val targetY: Float,
    var ttlSeconds: Float
) : Component

/**
 * First-class ECS mobile combat or support unit belonging to either the UEC Player Garrison
 * or Hostile Planetary Fauna. Driven by [UnitCombatAndAiSystem] + [MovementSystem].
 */
data class UnitComponent(
    val unitId: Long,
    val archetype: UnitArchetype,
    val faction: UnitFaction = archetype.faction,
    val maxHp: Int = archetype.maxHp,
    var hp: Int = archetype.maxHp,
    val attackDamage: Int = archetype.attackDamage,
    val attackRange: Float = archetype.attackRangeTiles,
    val attackCooldownSeconds: Float = archetype.attackCooldownSec,
    var cooldownTimer: Float = 0f,
    val moveSpeed: Float = archetype.moveSpeedTilesPerSec,
    val repairPerSecond: Float = archetype.repairPerSecond,
    var patrolAngleRad: Float = 0f,
    var statusNote: String = if (archetype.faction == UnitFaction.PLAYER) "PATROL" else "HOSTILE"
) : Component

/** Transient ECS combat/repair beam between mobile units or structures. */
data class CombatBeamComponent(
    val fromX: Float,
    val fromY: Float,
    val targetX: Float,
    val targetY: Float,
    val isPlayer: Boolean,
    val isRepair: Boolean = false,
    var ttlSeconds: Float = 0.25f
) : Component

/**
 * Factory helper for spawning fully-assembled planetary structure archetypes into an [EcsWorld].
 */
object PlanetaryEntityFactory {
    fun spawnBuilding(
        world: EcsWorld,
        x: Int,
        y: Int,
        type: BuildingType,
        direction: Direction,
        integrity: Int = type.maxIntegrity,
        recipeId: String = type.defaultRecipeId
    ): EntityId {
        val entity = world.createEntity()
        world.add(entity, Position(gridX = x, gridY = y, direction = direction))
        world.add(entity, TransformComponent(x, y, direction))
        world.add(
            entity,
            StructureComponent(
                buildingType = type,
                integrity = integrity.coerceIn(1, type.maxIntegrity),
                maxIntegrity = type.maxIntegrity
            )
        )
        world.add(entity, CoverageStateComponent())

        if (type.powerMw != 0) {
            world.add(entity, PowerNodeComponent(baseMw = type.powerMw))
            val energy = if (type.powerMw > 0) {
                EnergyConsumption(
                    baseDemandMw = 0f,
                    baseGenerationMw = type.powerMw.toFloat()
                )
            } else {
                EnergyConsumption(
                    baseDemandMw = (-type.powerMw).toFloat(),
                    baseGenerationMw = 0f
                )
            }
            world.add(entity, energy)
        }

        val storageCapacity = when {
            type == BuildingType.ORBITAL_HUB -> 10_000
            type == BuildingType.STORAGE_SILO -> 2_000
            type.isExtractor() -> 120
            type.isProducer() -> 180
            else -> 0
        }
        if (storageCapacity > 0) {
            world.add(entity, ResourceStorage(capacity = storageCapacity))
        }

        val inventory = when {
            type == BuildingType.ORBITAL_HUB -> Inventory(
                capacity = 10_000,
                transferMode = TransferMode.INPUT_ONLY,
                transferRatePerSecond = 0f
            )
            type == BuildingType.STORAGE_SILO -> Inventory(
                capacity = 2_000,
                transferMode = TransferMode.INPUT_OUTPUT,
                transferRatePerSecond = 4.0f
            )
            type.isExtractor() -> Inventory(
                capacity = 120,
                transferMode = TransferMode.OUTPUT_ONLY,
                transferRatePerSecond = 2.0f
            )
            type.isConveyor() -> {
                val beltSpeed = when (type) {
                    BuildingType.CONVEYOR_MK3 -> 2.6f
                    BuildingType.CONVEYOR_MK2 -> 1.75f
                    BuildingType.OMNI_ROUTER -> 1.6f
                    else -> 1.1f
                }
                Inventory(
                    capacity = 4,
                    transferMode = TransferMode.PASS_THROUGH,
                    transferRatePerSecond = beltSpeed
                )
            }
            type.isProducer() -> Inventory(
                capacity = 180,
                transferMode = TransferMode.INPUT_OUTPUT,
                transferRatePerSecond = 2.5f
            )
            else -> null
        }
        if (inventory != null) {
            world.add(entity, inventory)
        }

        if (type.isExtractor()) {
            val cycleSec = when (type) {
                BuildingType.DRILL_MK2 -> 0.85f
                BuildingType.THERMAL_EXTRACTOR -> 0.85f
                else -> 1.40f
            }
            val yieldQty = if (type == BuildingType.DRILL_MK2) 2 else 1
            world.add(
                entity,
                ExtractorComponent(
                    baseCycleSeconds = cycleSec,
                    outputAmount = yieldQty
                )
            )
            world.add(
                entity,
                ProductionRate(
                    baseCycleSeconds = cycleSec,
                    outputAmount = yieldQty
                )
            )
        }

        if (type.isConveyor()) {
            val speed = when (type) {
                BuildingType.CONVEYOR_MK3 -> 2.6f
                BuildingType.CONVEYOR_MK2 -> 1.75f
                BuildingType.OMNI_ROUTER -> 1.6f
                else -> 1.1f
            }
            world.add(
                entity,
                ConveyorComponent(
                    tilesPerSecond = speed,
                    isRouter = type == BuildingType.OMNI_ROUTER,
                    cryoImmune = type == BuildingType.CONVEYOR_MK3
                )
            )
            world.add(
                entity,
                Velocity(
                    dx = direction.dx * speed,
                    dy = direction.dy * speed,
                    speedTilesPerSec = speed
                )
            )
        }

        if (type.isProducer()) {
            val resolvedRecipeId = recipeId.ifBlank { type.defaultRecipeId }
            val recipe = RecipeCatalog.byId(resolvedRecipeId)
            val cycleSec = (recipe?.ticksRequired ?: 10) * 0.2f
            val yieldQty = recipe?.outputAmount ?: 1
            world.add(
                entity,
                RefineryComponent(
                    recipeId = resolvedRecipeId
                )
            )
            world.add(
                entity,
                ProductionRate(
                    baseCycleSeconds = cycleSec,
                    outputAmount = yieldQty
                )
            )
        }

        if (type.protectionRadius > 0) {
            val kind = when (type) {
                BuildingType.THERMAL_PYLON -> ProtectionKind.THERMAL
                BuildingType.ATMOS_SCRUBBER -> ProtectionKind.SCRUBBER
                else -> ProtectionKind.TURRET
            }
            world.add(entity, ProtectionFieldComponent(radius = type.protectionRadius, kind = kind))
        }

        return entity
    }

    fun spawnCargoPacket(
        world: EcsWorld,
        ctx: SimulationContext,
        resource: ResourceType,
        fromX: Int,
        fromY: Int,
        gridX: Int,
        gridY: Int
    ): EntityId {
        val entity = world.createEntity()
        val initialProgress = 0.05f
        val interpX = fromX + (gridX - fromX) * initialProgress
        val interpY = fromY + (gridY - fromY) * initialProgress
        val beltEntity = world.buildingEntityAt(gridX, gridY)
        val beltTransform = beltEntity?.let { world.get<TransformComponent>(it) }
        val beltConveyor = beltEntity?.let { world.get<ConveyorComponent>(it) }
        val dir = beltTransform?.direction ?: Direction.NORTH
        val speed = beltConveyor?.tilesPerSecond ?: 1.1f

        world.add(
            entity,
            Position(
                x = interpX,
                y = interpY,
                gridX = gridX,
                gridY = gridY,
                direction = dir
            )
        )
        world.add(
            entity,
            Velocity(
                dx = dir.dx * speed,
                dy = dir.dy * speed,
                speedTilesPerSec = speed
            )
        )
        world.add(
            entity,
            CargoPacketComponent(
                packetId = ctx.nextPacketId++,
                resource = resource,
                fromX = fromX,
                fromY = fromY,
                gridX = gridX,
                gridY = gridY,
                progress = initialProgress
            )
        )
        return entity
    }

    fun spawnUnit(
        world: EcsWorld,
        ctx: SimulationContext,
        archetype: UnitArchetype,
        x: Float,
        y: Float
    ): EntityId {
        val entity = world.createEntity()
        val uid = ctx.nextUnitId++
        world.add(
            entity,
            Position(
                x = x,
                y = y,
                gridX = x.toInt(),
                gridY = y.toInt(),
                direction = Direction.EAST
            )
        )
        world.add(
            entity,
            Velocity(
                dx = 0f,
                dy = 0f,
                speedTilesPerSec = archetype.moveSpeedTilesPerSec
            )
        )
        world.add(
            entity,
            UnitComponent(
                unitId = uid,
                archetype = archetype,
                patrolAngleRad = (uid % 8) * 0.785398f
            )
        )
        return entity
    }
}
