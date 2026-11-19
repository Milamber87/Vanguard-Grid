package com.example.ecs

import com.example.data.model.Direction
import com.example.simulation.GRID_SIZE
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/**
 * Discrete 2D tile coordinate on the planetary surface grid.
 */
data class GridCoord(
    val x: Int,
    val y: Int
) {
    fun offset(dx: Int, dy: Int): GridCoord = GridCoord(x + dx, y + dy)

    fun step(direction: Direction, distance: Int = 1): GridCoord =
        GridCoord(x + direction.dx * distance, y + direction.dy * distance)

    fun neighbors4(): List<Pair<Direction, GridCoord>> =
        Direction.entries.map { dir -> dir to step(dir) }

    fun chebyshevDistanceTo(other: GridCoord): Int =
        max(abs(x - other.x), abs(y - other.y))

    fun manhattanDistanceTo(other: GridCoord): Int =
        abs(x - other.x) + abs(y - other.y)

    fun euclideanDistanceTo(other: GridCoord): Float =
        hypot((x - other.x).toFloat(), (y - other.y).toFloat())

    /** Converts this grid tile to the continuous 2D world-space coordinate of its top-left origin. */
    fun toWorldOrigin(tileSize: Float = 1.0f): WorldPoint =
        WorldPoint(x * tileSize, y * tileSize)

    /** Converts this grid tile to the continuous 2D world-space coordinate of its center. */
    fun toWorldCenter(tileSize: Float = 1.0f): WorldPoint =
        WorldPoint((x + 0.5f) * tileSize, (y + 0.5f) * tileSize)
}

/**
 * Continuous 2D world-space coordinate used by renderers, moving packets, and touch hit-testing.
 */
data class WorldPoint(
    val x: Float,
    val y: Float
) {
    /** Projects a continuous world-space point into the containing discrete [GridCoord]. */
    fun toGridCoord(tileSize: Float = 1.0f): GridCoord {
        require(tileSize > 0f) { "tileSize must be positive" }
        return GridCoord(
            x = floor(x / tileSize).toInt(),
            y = floor(y / tileSize).toInt()
        )
    }
}

/**
 * Rectangular tile footprint occupied by a placed structure (defaults to 1x1).
 */
data class GridFootprint(
    val width: Int = 1,
    val height: Int = 1
) {
    init {
        require(width > 0 && height > 0) { "GridFootprint dimensions must be positive" }
    }

    fun occupiedCoords(origin: GridCoord): List<GridCoord> {
        val coords = ArrayList<GridCoord>(width * height)
        for (dy in 0 until height) {
            for (dx in 0 until width) {
                coords.add(GridCoord(origin.x + dx, origin.y + dy))
            }
        }
        return coords
    }

    companion object {
        val SINGLE = GridFootprint(1, 1)
    }
}

/**
 * 2D spatial grid coordinate system backing the ECS architecture for O(1) building placement,
 * world-to-grid projection, multi-tile footprint occupancy tracking, and local neighborhood/radius queries.
 */
class SpatialGrid(
    val width: Int = GRID_SIZE,
    val height: Int = GRID_SIZE,
    val tileSize: Float = 1.0f
) {
    init {
        require(width > 0 && height > 0) { "SpatialGrid dimensions must be positive" }
        require(tileSize > 0f) { "SpatialGrid tileSize must be positive" }
    }

    private data class PlacementRecord(
        val origin: GridCoord,
        val footprint: GridFootprint,
        val cells: List<GridCoord>
    )

    private val cellToEntity = HashMap<GridCoord, EntityId>()
    private val entityRecords = HashMap<EntityId, PlacementRecord>()

    /** Number of distinct entities currently placed in the spatial grid. */
    val entityCount: Int
        get() = entityRecords.size

    /** Total number of occupied grid cells (equals [entityCount] when all footprints are 1x1). */
    val occupiedCellCount: Int
        get() = cellToEntity.size

    fun isInBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    fun isInBounds(coord: GridCoord): Boolean = isInBounds(coord.x, coord.y)

    fun worldToGrid(worldX: Float, worldY: Float): GridCoord =
        WorldPoint(worldX, worldY).toGridCoord(tileSize)

    fun gridToWorldOrigin(coord: GridCoord): WorldPoint = coord.toWorldOrigin(tileSize)

    fun gridToWorldCenter(coord: GridCoord): WorldPoint = coord.toWorldCenter(tileSize)

    fun isOccupied(coord: GridCoord): Boolean = coord in cellToEntity

    fun isOccupied(x: Int, y: Int): Boolean = isOccupied(GridCoord(x, y))

    /** Returns true if every cell in [footprint] anchored at [origin] is in bounds and unoccupied. */
    fun canPlace(
        origin: GridCoord,
        footprint: GridFootprint = GridFootprint.SINGLE,
        ignoringEntity: EntityId? = null
    ): Boolean {
        val cells = footprint.occupiedCoords(origin)
        for (cell in cells) {
            if (!isInBounds(cell)) return false
            val occupant = cellToEntity[cell]
            if (occupant != null && occupant != ignoringEntity) return false
        }
        return true
    }

    /**
     * Registers [entity] across its [footprint] anchored at [origin].
     * If [replaceExisting] is true, any previously occupying entities on overlapping cells are evicted.
     * Returns true if placement succeeded, or false if out of bounds or blocked.
     */
    fun place(
        entity: EntityId,
        origin: GridCoord,
        footprint: GridFootprint = GridFootprint.SINGLE,
        replaceExisting: Boolean = false
    ): Boolean {
        val cells = footprint.occupiedCoords(origin)
        if (cells.any { !isInBounds(it) }) return false

        // Remove any prior registration for the same entity before re-placing
        remove(entity)

        if (!replaceExisting) {
            if (cells.any { it in cellToEntity }) return false
        } else {
            val evicted = HashSet<EntityId>()
            for (cell in cells) {
                cellToEntity[cell]?.let { evicted.add(it) }
            }
            evicted.forEach { remove(it) }
        }

        for (cell in cells) {
            cellToEntity[cell] = entity
        }
        entityRecords[entity] = PlacementRecord(origin, footprint, cells)
        return true
    }

    /** Moves an already-placed [entity] to [newOrigin] while preserving its footprint. */
    fun move(entity: EntityId, newOrigin: GridCoord, replaceExisting: Boolean = false): Boolean {
        val existing = entityRecords[entity] ?: return false
        if (!replaceExisting && !canPlace(newOrigin, existing.footprint, ignoringEntity = entity)) {
            return false
        }
        return place(entity, newOrigin, existing.footprint, replaceExisting)
    }

    /** Removes [entity] from all occupied cells. Returns true if the entity was present. */
    fun remove(entity: EntityId): Boolean {
        val record = entityRecords.remove(entity) ?: return false
        for (cell in record.cells) {
            if (cellToEntity[cell] == entity) {
                cellToEntity.remove(cell)
            }
        }
        return true
    }

    /** Removes and returns the entity occupying [coord], if any. */
    fun removeAt(coord: GridCoord): EntityId? {
        val entity = cellToEntity[coord] ?: return null
        remove(entity)
        return entity
    }

    fun clear() {
        cellToEntity.clear()
        entityRecords.clear()
    }

    /** O(1) lookup of the building entity occupying discrete tile ([x], [y]). */
    fun entityAt(x: Int, y: Int): EntityId? = cellToEntity[GridCoord(x, y)]

    /** O(1) lookup of the building entity occupying [coord]. */
    fun entityAt(coord: GridCoord): EntityId? = cellToEntity[coord]

    /** Projects continuous 2D world coordinates ([worldX], [worldY]) to a tile and returns its entity. */
    fun entityAtWorld(worldX: Float, worldY: Float): EntityId? =
        entityAt(worldToGrid(worldX, worldY))

    fun originOf(entity: EntityId): GridCoord? = entityRecords[entity]?.origin

    fun footprintOf(entity: EntityId): GridFootprint? = entityRecords[entity]?.footprint

    /** Returns the placed entity immediately adjacent to [coord] in [direction], if any. */
    fun neighborInDirection(coord: GridCoord, direction: Direction): EntityId? =
        entityAt(coord.step(direction))

    /** Returns all orthogonally adjacent (N, E, S, W) placed entities around [coord]. */
    fun adjacentEntities(coord: GridCoord): List<Pair<Direction, EntityId>> {
        val result = ArrayList<Pair<Direction, EntityId>>(4)
        for (dir in Direction.entries) {
            val neighbor = entityAt(coord.step(dir))
            if (neighbor != null) {
                result.add(dir to neighbor)
            }
        }
        return result
    }

    /**
     * Returns all distinct entities occupying cells within a Chebyshev [radius] around [center].
     * Ideal for Thermal Shield Pylon, Atmospheric Scrubber, and Rail-Turret coverage queries.
     */
    fun entitiesInRadius(center: GridCoord, radius: Int): List<EntityId> {
        if (radius < 0) return emptyList()
        val minX = (center.x - radius).coerceAtLeast(0)
        val maxX = (center.x + radius).coerceAtMost(width - 1)
        val minY = (center.y - radius).coerceAtLeast(0)
        val maxY = (center.y + radius).coerceAtMost(height - 1)
        return entitiesInBounds(GridCoord(minX, minY), GridCoord(maxX, maxY))
    }

    /** Returns all distinct entities occupying cells within the inclusive axis-aligned rectangle. */
    fun entitiesInBounds(minCoord: GridCoord, maxCoord: GridCoord): List<EntityId> {
        val x0 = minCoord.x.coerceIn(0, width - 1)
        val x1 = maxCoord.x.coerceIn(0, width - 1)
        val y0 = minCoord.y.coerceIn(0, height - 1)
        val y1 = maxCoord.y.coerceIn(0, height - 1)
        if (x0 > x1 || y0 > y1) return emptyList()

        val found = LinkedHashSet<EntityId>()
        for (y in y0..y1) {
            for (x in x0..x1) {
                cellToEntity[GridCoord(x, y)]?.let { found.add(it) }
            }
        }
        return found.toList()
    }
}
