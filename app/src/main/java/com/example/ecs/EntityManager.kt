package com.example.ecs

import kotlin.reflect.KClass

/**
 * Central ECS registry responsible for registering, removing, and retrieving entities
 * and their typed [Component] instances, with O(1) component lookups and spatial indexing
 * for grid-aligned planetary structures.
 */
class EntityManager(
    val spatialGrid: SpatialGrid = SpatialGrid()
) {
    private var nextEntityId: EntityId = 1
    private val activeEntities = LinkedHashSet<EntityId>()
    private val componentStores = HashMap<KClass<out Component>, MutableMap<EntityId, Component>>()

    val entityCount: Int
        get() = activeEntities.size

    /** Returns an immutable snapshot of all currently registered entity IDs. */
    fun entities(): Set<EntityId> = LinkedHashSet(activeEntities)

    /** Returns true if [entity] is currently registered and active. */
    fun isAlive(entity: EntityId): Boolean = entity in activeEntities

    /** Registers a new empty entity and returns its unique [EntityId]. */
    fun createEntity(): EntityId {
        val id = nextEntityId++
        activeEntities.add(id)
        return id
    }

    /** Registers a new entity and immediately attaches all supplied [components]. */
    fun registerEntity(vararg components: Component): EntityId {
        val id = createEntity()
        for (component in components) {
            addComponent(id, component)
        }
        return id
    }

    /**
     * Unregisters [entity], removes all of its attached components, and cleans up
     * any spatial grid placement entry. Returns true if the entity existed.
     */
    fun removeEntity(entity: EntityId): Boolean {
        if (!activeEntities.remove(entity)) return false
        spatialGrid.remove(entity)
        componentStores.values.forEach { it.remove(entity) }
        return true
    }

    /** Alias for [removeEntity]. */
    fun destroyEntity(entity: EntityId): Boolean = removeEntity(entity)

    /** Clears all entities, component stores, spatial grid placements, and resets ID generation. */
    fun clear() {
        activeEntities.clear()
        componentStores.clear()
        spatialGrid.clear()
        nextEntityId = 1
    }

    /** Attaches or replaces a [component] on a registered [entity]. */
    fun <T : Component> addComponent(entity: EntityId, component: T): T {
        require(entity in activeEntities) { "Entity $entity is not registered in EntityManager" }
        val store = componentStores.getOrPut(component::class) { LinkedHashMap() }
        store[entity] = component
        updateSpatialIndexOnAdd(entity, component)
        return component
    }

    /** Retrieves the component of [type] attached to [entity], or null if absent. */
    fun <T : Component> getComponent(entity: EntityId, type: KClass<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return componentStores[type]?.get(entity) as? T
    }

    inline fun <reified T : Component> getComponent(entity: EntityId): T? =
        getComponent(entity, T::class)

    /** Returns all components currently attached to [entity]. */
    fun getAllComponents(entity: EntityId): List<Component> {
        if (entity !in activeEntities) return emptyList()
        val result = ArrayList<Component>()
        for (store in componentStores.values) {
            store[entity]?.let { result.add(it) }
        }
        return result
    }

    /** Returns true if [entity] currently has a component of [type]. */
    fun <T : Component> hasComponent(entity: EntityId, type: KClass<T>): Boolean =
        componentStores[type]?.containsKey(entity) == true

    inline fun <reified T : Component> hasComponent(entity: EntityId): Boolean =
        hasComponent(entity, T::class)

    /**
     * Removes the component of [type] from [entity] and returns the removed instance if present.
     */
    fun <T : Component> removeComponent(entity: EntityId, type: KClass<T>): T? {
        if (type == StructureComponent::class || type == TransformComponent::class || type == Position::class) {
            spatialGrid.remove(entity)
        }
        @Suppress("UNCHECKED_CAST")
        val removed = componentStores[type]?.remove(entity) as? T
        // Re-index if structure still has an alternative coordinate component
        if (removed != null && hasComponent(entity, StructureComponent::class)) {
            syncStructureToSpatialGrid(entity)
        }
        return removed
    }

    inline fun <reified T : Component> removeComponent(entity: EntityId): T? =
        removeComponent(entity, T::class)

    /** O(1) lookup for a placed structure entity occupying tile ([x], [y]). */
    fun buildingEntityAt(x: Int, y: Int): EntityId? = spatialGrid.entityAt(x, y)

    /** O(1) lookup for a placed structure entity occupying [coord]. */
    fun buildingEntityAt(coord: GridCoord): EntityId? = spatialGrid.entityAt(coord)

    /** Projects continuous 2D world coordinates ([worldX], [worldY]) and returns the placed entity. */
    fun buildingEntityAtWorld(worldX: Float, worldY: Float): EntityId? =
        spatialGrid.entityAtWorld(worldX, worldY)

    /** Returns true if a building with [footprint] can be placed at [origin]. */
    fun canPlaceBuildingAt(
        origin: GridCoord,
        footprint: GridFootprint = GridFootprint.SINGLE
    ): Boolean = spatialGrid.canPlace(origin, footprint)

    /** Returns all placed building entities within Chebyshev [radius] of [center]. */
    fun buildingsInRadius(center: GridCoord, radius: Int): List<EntityId> =
        spatialGrid.entitiesInRadius(center, radius)

    /** Returns the total number of spatially indexed building entities. */
    fun buildingCount(): Int = spatialGrid.entityCount

    /** Retrieves all entities matching every specified [componentTypes]. */
    fun entitiesMatching(vararg componentTypes: KClass<out Component>): List<EntityId> {
        if (componentTypes.isEmpty()) return activeEntities.toList()
        val stores = componentTypes.map { componentStores[it] ?: return emptyList() }
        val smallest = stores.minByOrNull { it.size } ?: return emptyList()
        val result = ArrayList<EntityId>(smallest.size)
        for (entity in smallest.keys) {
            if (stores.all { entity in it }) {
                result.add(entity)
            }
        }
        return result
    }

    /** Retrieves all entities with component [aType] along with that component instance. */
    fun <A : Component> getEntitiesWith(aType: KClass<A>): List<Pair<EntityId, A>> {
        val storeA = componentStores[aType] ?: return emptyList()
        val result = ArrayList<Pair<EntityId, A>>(storeA.size)
        for ((entity, compA) in storeA) {
            @Suppress("UNCHECKED_CAST")
            result.add(entity to (compA as A))
        }
        return result
    }

    inline fun <reified A : Component> getEntitiesWith(): List<Pair<EntityId, A>> =
        getEntitiesWith(A::class)

    /** Retrieves all entities possessing both [aType] and [bType], iterating the smaller store first. */
    fun <A : Component, B : Component> getEntitiesWith(
        aType: KClass<A>,
        bType: KClass<B>
    ): List<Query2<A, B>> {
        val storeA = componentStores[aType] ?: return emptyList()
        val storeB = componentStores[bType] ?: return emptyList()
        val result = ArrayList<Query2<A, B>>(minOf(storeA.size, storeB.size))
        if (storeA.size <= storeB.size) {
            for ((entity, compA) in storeA) {
                val compB = storeB[entity] ?: continue
                @Suppress("UNCHECKED_CAST")
                result.add(Query2(entity, compA as A, compB as B))
            }
        } else {
            for ((entity, compB) in storeB) {
                val compA = storeA[entity] ?: continue
                @Suppress("UNCHECKED_CAST")
                result.add(Query2(entity, compA as A, compB as B))
            }
        }
        return result
    }

    inline fun <reified A : Component, reified B : Component> getEntitiesWith2(): List<Query2<A, B>> =
        getEntitiesWith(A::class, B::class)

    /** Retrieves all entities possessing [aType], [bType], and [cType], iterating the smallest store first. */
    fun <A : Component, B : Component, C : Component> getEntitiesWith(
        aType: KClass<A>,
        bType: KClass<B>,
        cType: KClass<C>
    ): List<Query3<A, B, C>> {
        val storeA = componentStores[aType] ?: return emptyList()
        val storeB = componentStores[bType] ?: return emptyList()
        val storeC = componentStores[cType] ?: return emptyList()
        val minSize = minOf(storeA.size, storeB.size, storeC.size)
        val result = ArrayList<Query3<A, B, C>>(minSize)

        val pivotKeys = when {
            storeA.size <= storeB.size && storeA.size <= storeC.size -> storeA.keys
            storeB.size <= storeC.size -> storeB.keys
            else -> storeC.keys
        }
        for (entity in pivotKeys) {
            val compA = storeA[entity] ?: continue
            val compB = storeB[entity] ?: continue
            val compC = storeC[entity] ?: continue
            @Suppress("UNCHECKED_CAST")
            result.add(Query3(entity, compA as A, compB as B, compC as C))
        }
        return result
    }

    inline fun <reified A : Component, reified B : Component, reified C : Component> getEntitiesWith3(): List<Query3<A, B, C>> =
        getEntitiesWith(A::class, B::class, C::class)

    private fun updateSpatialIndexOnAdd(entity: EntityId, component: Component) {
        when (component) {
            is TransformComponent, is Position, is StructureComponent -> {
                if (hasComponent(entity, StructureComponent::class)) {
                    syncStructureToSpatialGrid(entity)
                }
            }
        }
    }

    private fun syncStructureToSpatialGrid(entity: EntityId) {
        val pos = getComponent(entity, Position::class)
        val transform = getComponent(entity, TransformComponent::class)
        val origin = when {
            pos != null -> pos.gridCoord
            transform != null -> GridCoord(transform.x, transform.y)
            else -> return
        }
        val footprint = pos?.footprint ?: GridFootprint.SINGLE
        spatialGrid.place(entity, origin, footprint, replaceExisting = true)
    }
}
