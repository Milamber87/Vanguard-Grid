package com.example.ecs

import com.example.data.model.ActiveDynamicEvent
import com.example.data.model.EnvironmentalHazard
import com.example.data.model.ResourceType
import com.example.simulation.GRID_SIZE
import com.example.simulation.MapTile
import kotlin.reflect.KClass

typealias EntityId = Int

/**
 * Marker interface for pure ECS data components.
 * Components hold state only; behavior lives exclusively in [EcsSystem] implementations.
 */
interface Component

data class Query2<A : Component, B : Component>(
    val entity: EntityId,
    val a: A,
    val b: B
)

data class Query3<A : Component, B : Component, C : Component>(
    val entity: EntityId,
    val a: A,
    val b: B,
    val c: C
)

/**
 * Shared per-planet world context passed to every [EcsSystem] on each fixed simulation step.
 */
class SimulationContext(
    val planetId: String,
    val tiles: List<MapTile>,
    val inventory: MutableMap<ResourceType, Int>,
    var unlockedTechs: Set<String> = emptySet(),
    var activeEvent: ActiveDynamicEvent? = null,
    var activeHazard: EnvironmentalHazard? = null,
    var hazardSecondsRemaining: Float = 0f,
    var powerGeneratedMw: Int = 25,
    var powerConsumedMw: Int = 0,
    var powerEfficiency: Float = 1.0f,
    var tickCount: Long = 0L,
    var nextPacketId: Long = 1L,
    var nextUnitId: Long = 1L,
    var pendingBountyCredits: Long = 0L,
    var enemiesEliminatedCount: Int = 0,
    var throughputWindowCount: Int = 0,
    var throughputTimerSeconds: Float = 0f,
    var itemsPerMinute: Int = 0,
    var fuelBurnAccumulator: Float = 0f,
    var hazardDamageAccumulator: Float = 0f,
    var wildlifeRaidAccumulator: Float = 0f
) {
    fun tileAt(x: Int, y: Int): MapTile? {
        if (x !in 0 until GRID_SIZE || y !in 0 until GRID_SIZE) return null
        return tiles[y * GRID_SIZE + x]
    }
}

/**
 * Stateless logic processor operating over [EcsWorld] component archetypes.
 */
interface EcsSystem {
    val name: String
    fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float)
}

/**
 * Lightweight, type-safe Entity Component System world facade backed by [EntityManager].
 */
class EcsWorld(
    val entityManager: EntityManager = EntityManager()
) {
    val entityCount: Int
        get() = entityManager.entityCount

    fun entities(): Set<EntityId> = entityManager.entities()

    fun isAlive(entity: EntityId): Boolean = entityManager.isAlive(entity)

    fun createEntity(): EntityId = entityManager.createEntity()

    fun registerEntity(vararg components: Component): EntityId =
        entityManager.registerEntity(*components)

    fun destroyEntity(entity: EntityId) {
        entityManager.removeEntity(entity)
    }

    fun clear() = entityManager.clear()

    fun <T : Component> add(entity: EntityId, component: T): T =
        entityManager.addComponent(entity, component)

    fun <T : Component> get(entity: EntityId, type: KClass<T>): T? =
        entityManager.getComponent(entity, type)

    inline fun <reified T : Component> get(entity: EntityId): T? =
        entityManager.getComponent(entity, T::class)

    fun getAllComponents(entity: EntityId): List<Component> =
        entityManager.getAllComponents(entity)

    fun <T : Component> has(entity: EntityId, type: KClass<T>): Boolean =
        entityManager.hasComponent(entity, type)

    inline fun <reified T : Component> has(entity: EntityId): Boolean =
        entityManager.hasComponent(entity, T::class)

    fun <T : Component> remove(entity: EntityId, type: KClass<T>): T? =
        entityManager.removeComponent(entity, type)

    inline fun <reified T : Component> remove(entity: EntityId): T? =
        entityManager.removeComponent(entity, T::class)

    val spatialGrid: SpatialGrid
        get() = entityManager.spatialGrid

    fun buildingEntityAt(x: Int, y: Int): EntityId? =
        entityManager.buildingEntityAt(x, y)

    fun buildingEntityAt(coord: GridCoord): EntityId? =
        entityManager.buildingEntityAt(coord)

    fun buildingEntityAtWorld(worldX: Float, worldY: Float): EntityId? =
        entityManager.buildingEntityAtWorld(worldX, worldY)

    fun buildingsInRadius(center: GridCoord, radius: Int): List<EntityId> =
        entityManager.buildingsInRadius(center, radius)

    fun buildingCount(): Int = entityManager.buildingCount()

    fun <A : Component> query(aType: KClass<A>): List<Pair<EntityId, A>> =
        entityManager.getEntitiesWith(aType)

    inline fun <reified A : Component> query(): List<Pair<EntityId, A>> =
        entityManager.getEntitiesWith(A::class)

    fun <A : Component, B : Component> query(
        aType: KClass<A>,
        bType: KClass<B>
    ): List<Query2<A, B>> = entityManager.getEntitiesWith(aType, bType)

    inline fun <reified A : Component, reified B : Component> query2(): List<Query2<A, B>> =
        entityManager.getEntitiesWith(A::class, B::class)

    fun <A : Component, B : Component, C : Component> query(
        aType: KClass<A>,
        bType: KClass<B>,
        cType: KClass<C>
    ): List<Query3<A, B, C>> = entityManager.getEntitiesWith(aType, bType, cType)

    inline fun <reified A : Component, reified B : Component, reified C : Component> query3(): List<Query3<A, B, C>> =
        entityManager.getEntitiesWith(A::class, B::class, C::class)
}
