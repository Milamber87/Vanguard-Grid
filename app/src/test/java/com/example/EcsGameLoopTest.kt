package com.example

import com.example.data.model.BuildingType
import com.example.data.model.Direction
import com.example.data.model.ResourceType
import com.example.ecs.CargoPacketComponent
import com.example.ecs.EcsPlanetRuntime
import com.example.ecs.EcsWorld
import com.example.ecs.ExtractorComponent
import com.example.ecs.FixedTimestepGameLoop
import com.example.ecs.PlanetaryEntityFactory
import com.example.ecs.RefineryComponent
import com.example.ecs.TransformComponent
import com.example.simulation.FactoryEngine
import com.example.simulation.PlanetSimState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EcsGameLoopTest {

    @Test
    fun ecsWorld_createQueryAndDestroyEntities() {
        val world = EcsWorld()
        val drillId = PlanetaryEntityFactory.spawnBuilding(
            world = world,
            x = 3,
            y = 7,
            type = BuildingType.DRILL_MK1,
            direction = Direction.EAST
        )
        val smelterId = PlanetaryEntityFactory.spawnBuilding(
            world = world,
            x = 5,
            y = 7,
            type = BuildingType.ARC_SMELTER,
            direction = Direction.EAST,
            recipeId = "iron_plate"
        )

        assertEquals(2, world.entityCount)
        assertEquals(drillId, world.buildingEntityAt(3, 7))
        assertEquals(smelterId, world.buildingEntityAt(5, 7))
        assertEquals(1, world.query2<TransformComponent, ExtractorComponent>().size)
        assertEquals(1, world.query2<TransformComponent, RefineryComponent>().size)

        world.destroyEntity(drillId)
        assertEquals(1, world.entityCount)
        assertNull(world.buildingEntityAt(3, 7))
        assertNotNull(world.buildingEntityAt(5, 7))
    }

    @Test
    fun coroutineFixedTimestepLoop_drivesEcsIndependentlyOfUiFrameRate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val tiles = FactoryEngine.generateTerrain("temperate")
        val initial = PlanetSimState(
            planetId = "temperate",
            tiles = tiles,
            buildings = emptyMap(),
            inventory = mapOf(ResourceType.COAL to 20, ResourceType.IRON_ORE to 0, ResourceType.IRON_PLATE to 0)
        )
        val runtime = EcsPlanetRuntime("temperate", initial)

        // Spawn complete logistics chain: Drill -> Belt -> Arc Smelter -> Belt -> Orbital Hub
        runtime.placeBuilding(3, 7, BuildingType.DRILL_MK1, Direction.EAST, "NONE", false)
        runtime.placeBuilding(4, 7, BuildingType.CONVEYOR_MK1, Direction.EAST, "NONE", false)
        runtime.placeBuilding(5, 7, BuildingType.ARC_SMELTER, Direction.EAST, "iron_plate", false)
        runtime.placeBuilding(6, 7, BuildingType.CONVEYOR_MK1, Direction.EAST, "NONE", false)
        runtime.placeBuilding(7, 7, BuildingType.ORBITAL_HUB, Direction.NORTH, "NONE", false)
        runtime.context.unlockedTechs = setOf("t1_extraction")

        var recordedStepDt = 0f
        var sawCargoInFlight = false

        // Feed virtual coroutine testScheduler time into nanoTimeProvider
        val loop = FixedTimestepGameLoop(
            scope = this,
            dispatcher = dispatcher,
            targetHz = 20,
            nanoTimeProvider = { testScheduler.currentTime * 1_000_000L },
            onFixedTick = { dt ->
                recordedStepDt = dt
                runtime.stepFixedTick(dt)
                if (runtime.world.query<CargoPacketComponent>().isNotEmpty()) {
                    sawCargoInFlight = true
                }
            }
        )

        loop.start()
        runCurrent()

        // Advance 10 seconds of virtual coroutine time with zero UI frames required
        advanceTimeBy(10_000L)
        runCurrent()

        val ticksBeforePause = runtime.context.tickCount
        assertTrue("Expected ~200 fixed ticks in 10s at 20Hz, got $ticksBeforePause", ticksBeforePause >= 190L)
        assertEquals(0.05f, recordedStepDt, 0.0001f)
        assertTrue("Expected cargo packet entities spawned on conveyor belt", sawCargoInFlight)

        val snapshot = runtime.exportSnapshot()
        assertTrue(
            "Expected Arc Smelter to produce Iron Plates independently of UI, got ${snapshot.inventory[ResourceType.IRON_PLATE]}",
            (snapshot.inventory[ResourceType.IRON_PLATE] ?: 0) > 0
        )

        // Verify pause halts ticks cleanly without stopping coroutine
        loop.setPaused(true)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(ticksBeforePause, runtime.context.tickCount)

        loop.stop()
    }

    @Test
    fun coreComponents_positionVelocityStorageEnergyProduction_behaveAndSyncCorrectly() {
        val world = EcsWorld()
        val drillId = PlanetaryEntityFactory.spawnBuilding(
            world = world,
            x = 3,
            y = 7,
            type = BuildingType.DRILL_MK1,
            direction = Direction.EAST
        )
        val beltId = PlanetaryEntityFactory.spawnBuilding(
            world = world,
            x = 4,
            y = 7,
            type = BuildingType.CONVEYOR_MK2,
            direction = Direction.EAST
        )
        val hubId = PlanetaryEntityFactory.spawnBuilding(
            world = world,
            x = 5,
            y = 7,
            type = BuildingType.ORBITAL_HUB,
            direction = Direction.NORTH
        )

        // Position component verification
        val drillPos = world.get<com.example.ecs.Position>(drillId)
        assertNotNull(drillPos)
        assertEquals(3f, drillPos!!.x, 0.001f)
        assertEquals(7f, drillPos.y, 0.001f)
        assertEquals(Direction.EAST, drillPos.direction)

        // Velocity component verification on Conveyor Mk.2
        val beltVel = world.get<com.example.ecs.Velocity>(beltId)
        assertNotNull(beltVel)
        assertEquals(1.75f, beltVel!!.dx, 0.001f)
        assertEquals(0f, beltVel.dy, 0.001f)
        assertEquals(1.75f, beltVel.magnitude, 0.001f)

        // EnergyConsumption component verification on consumer (Drill) and generator (Hub)
        val drillEnergy = world.get<com.example.ecs.EnergyConsumption>(drillId)
        val hubEnergy = world.get<com.example.ecs.EnergyConsumption>(hubId)
        assertNotNull(drillEnergy)
        assertNotNull(hubEnergy)
        assertTrue(drillEnergy!!.isConsumer)
        assertTrue(hubEnergy!!.isGenerator)
        assertTrue(hubEnergy.netEffectiveMw > 0f)
        assertTrue(drillEnergy.netEffectiveMw < 0f)

        // ProductionRate component verification on Drill
        val drillProd = world.get<com.example.ecs.ProductionRate>(drillId)
        assertNotNull(drillProd)
        assertTrue(drillProd!!.effectiveItemsPerSecond > 0f)
        assertEquals(drillProd.effectiveItemsPerSecond * 60f, drillProd.effectiveItemsPerMinute, 0.01f)

        // ResourceStorage capacity, filtering, deposit, and withdraw verification
        val storage = com.example.ecs.ResourceStorage(
            capacity = 50,
            acceptedResources = setOf(ResourceType.IRON_ORE, ResourceType.IRON_PLATE)
        )
        assertEquals(0, storage.deposit(ResourceType.COPPER_ORE, 10))
        assertEquals(30, storage.deposit(ResourceType.IRON_ORE, 30))
        assertEquals(20, storage.deposit(ResourceType.IRON_PLATE, 40)) // Clamped to remaining capacity
        assertEquals(50, storage.totalStored)
        assertEquals(0, storage.availableCapacity)
        assertEquals(15, storage.withdraw(ResourceType.IRON_ORE, 15))
        assertEquals(15, storage.quantityOf(ResourceType.IRON_ORE))
    }

    @Test
    fun entityManager_registerRetrieveRemoveEntitiesAndComponents() {
        val manager = com.example.ecs.EntityManager()

        val extractor = manager.registerEntity(
            com.example.ecs.Position(gridX = 2, gridY = 4, direction = Direction.EAST),
            com.example.ecs.EnergyConsumption(baseDemandMw = 8f),
            com.example.ecs.ProductionRate(baseCycleSeconds = 1.4f, outputAmount = 1),
            com.example.ecs.ResourceStorage(capacity = 120)
        )
        val packet = manager.registerEntity(
            com.example.ecs.Position(x = 2.5f, y = 4.0f, gridX = 2, gridY = 4, direction = Direction.EAST),
            com.example.ecs.Velocity(dx = 1.1f, dy = 0f, speedTilesPerSec = 1.1f)
        )

        assertEquals(2, manager.entityCount)
        assertTrue(manager.isAlive(extractor))
        assertEquals(4, manager.getAllComponents(extractor).size)

        // Multi-component archetype retrieval
        assertEquals(2, manager.getEntitiesWith<com.example.ecs.Position>().size)
        assertEquals(1, manager.getEntitiesWith2<com.example.ecs.Position, com.example.ecs.Velocity>().size)
        assertEquals(
            1,
            manager.getEntitiesWith3<com.example.ecs.Position, com.example.ecs.EnergyConsumption, com.example.ecs.ProductionRate>().size
        )
        assertEquals(
            listOf(extractor),
            manager.entitiesMatching(
                com.example.ecs.Position::class,
                com.example.ecs.ResourceStorage::class
            )
        )

        // Component removal
        val removedVelocity = manager.removeComponent<com.example.ecs.Velocity>(packet)
        assertNotNull(removedVelocity)
        assertNull(manager.getComponent<com.example.ecs.Velocity>(packet))
        assertEquals(0, manager.getEntitiesWith2<com.example.ecs.Position, com.example.ecs.Velocity>().size)

        // Entity removal cleans up all components
        assertTrue(manager.removeEntity(extractor))
        assertEquals(1, manager.entityCount)
        assertTrue(!manager.isAlive(extractor))
        assertNull(manager.getComponent<com.example.ecs.ProductionRate>(extractor))
        assertTrue(manager.getAllComponents(extractor).isEmpty())
    }

    @Test
    fun coreSystems_movementEnergyAndResourceProduction_iterateEntityManager() {
        val manager = com.example.ecs.EntityManager()

        // 1. Mobile logistics drone entity (Position + Velocity)
        val drone = manager.registerEntity(
            com.example.ecs.Position(x = 1.0f, y = 2.0f, direction = Direction.EAST),
            com.example.ecs.Velocity(dx = 2.0f, dy = 0.5f, speedTilesPerSec = 2.06f)
        )

        // 2. Power generator (30 MW) + Power consumer / producer (60 MW -> 50% grid efficiency)
        val generator = manager.registerEntity(
            com.example.ecs.EnergyConsumption(baseGenerationMw = 30f)
        )
        val producer = manager.registerEntity(
            com.example.ecs.EnergyConsumption(baseDemandMw = 60f),
            com.example.ecs.ProductionRate(baseCycleSeconds = 1.0f, outputAmount = 2),
            com.example.ecs.ResourceStorage(
                capacity = 10,
                acceptedResources = setOf(ResourceType.COPPER_ORE)
            )
        )

        val movementSystem = com.example.ecs.MovementSystem()
        val energySystem = com.example.ecs.EnergyConsumptionSystem()
        val productionSystem = com.example.ecs.ResourceProductionSystem()

        // Run EnergyConsumptionSystem: 30 MW gen / 60 MW demand = 0.5f efficiency
        val gridSummary = energySystem.update(manager, dtSeconds = 0.05f)
        assertEquals(30f, gridSummary.totalGenerationMw, 0.001f)
        assertEquals(60f, gridSummary.totalDemandMw, 0.001f)
        assertEquals(-30f, gridSummary.netMw, 0.001f)
        assertEquals(0.5f, gridSummary.efficiency, 0.001f)
        assertEquals(0.5f, manager.getComponent<com.example.ecs.EnergyConsumption>(producer)!!.gridEfficiency, 0.001f)
        assertEquals(0.5f, manager.getComponent<com.example.ecs.EnergyConsumption>(generator)!!.gridEfficiency, 0.001f)

        // Run MovementSystem for 2.0 seconds -> drone moves (+4.0, +1.0)
        val moved = movementSystem.update(manager, dtSeconds = 2.0f)
        assertEquals(1, moved)
        val dronePos = manager.getComponent<com.example.ecs.Position>(drone)!!
        assertEquals(5.0f, dronePos.x, 0.001f)
        assertEquals(3.0f, dronePos.y, 0.001f)
        assertEquals(5, dronePos.gridX)
        assertEquals(3, dronePos.gridY)

        // Run ResourceProductionSystem for 2.0 seconds at 0.5x power efficiency -> 1 cycle -> 2 Copper Ore
        val produced = productionSystem.update(manager, dtSeconds = 2.0f)
        assertEquals(2, produced)
        val storage = manager.getComponent<com.example.ecs.ResourceStorage>(producer)!!
        assertEquals(2, storage.quantityOf(ResourceType.COPPER_ORE))

        // Run for 20 more seconds -> respects capacity cap of 10
        productionSystem.update(manager, dtSeconds = 20.0f)
        assertEquals(10, storage.totalStored)
        assertEquals(0, storage.availableCapacity)
    }

    @Test
    fun spatialGrid_tracksBuildingPlacementWorldCoordsFootprintsAndQueries() {
        val grid = com.example.ecs.SpatialGrid(width = 14, height = 14, tileSize = 32f)
        val manager = com.example.ecs.EntityManager(spatialGrid = grid)
        val world = EcsWorld(entityManager = manager)

        // Place Extractor (3, 7) facing EAST -> Conveyor (4, 7) -> Orbital Hub (5, 7)
        val drill = PlanetaryEntityFactory.spawnBuilding(world, 3, 7, BuildingType.DRILL_MK1, Direction.EAST)
        val belt = PlanetaryEntityFactory.spawnBuilding(world, 4, 7, BuildingType.CONVEYOR_MK1, Direction.EAST)
        val hub = PlanetaryEntityFactory.spawnBuilding(world, 5, 7, BuildingType.ORBITAL_HUB, Direction.NORTH)

        // World-space <-> Grid-space projection (tileSize = 32f)
        val drillCenter = com.example.ecs.GridCoord(3, 7).toWorldCenter(tileSize = 32f)
        assertEquals(112f, drillCenter.x, 0.001f)
        assertEquals(240f, drillCenter.y, 0.001f)
        assertEquals(drill, world.buildingEntityAtWorld(drillCenter.x, drillCenter.y))
        assertEquals(belt, grid.neighborInDirection(com.example.ecs.GridCoord(3, 7), Direction.EAST))

        // Cardinal adjacency query around conveyor at (4, 7)
        val beltNeighbors = grid.adjacentEntities(com.example.ecs.GridCoord(4, 7)).toMap()
        assertEquals(drill, beltNeighbors[Direction.WEST])
        assertEquals(hub, beltNeighbors[Direction.EAST])

        // Radius query around (4, 7) with radius 1 finds all 3 buildings
        val inRadius = world.buildingsInRadius(com.example.ecs.GridCoord(4, 7), radius = 1)
        assertEquals(setOf(drill, belt, hub), inRadius.toSet())

        // Multi-tile 2x2 footprint occupancy & collision prevention
        val reactor = manager.registerEntity(
            com.example.ecs.Position(
                gridX = 9,
                gridY = 9,
                direction = Direction.NORTH,
                footprint = com.example.ecs.GridFootprint(2, 2)
            ),
            com.example.ecs.StructureComponent(BuildingType.FUSION_CORE, 200, 200)
        )
        assertEquals(reactor, grid.entityAt(9, 9))
        assertEquals(reactor, grid.entityAt(10, 9))
        assertEquals(reactor, grid.entityAt(9, 10))
        assertEquals(reactor, grid.entityAt(10, 10))
        assertTrue(!grid.canPlace(com.example.ecs.GridCoord(10, 10)))

        // Removing entity frees all 4 cells
        manager.removeEntity(reactor)
        assertNull(grid.entityAt(10, 10))
        assertTrue(grid.canPlace(com.example.ecs.GridCoord(10, 10)))
    }

    @Test
    fun inventorySystem_transfersResourcesAcrossConnectedGridEntitiesAndRouters() {
        val manager = com.example.ecs.EntityManager()
        val world = EcsWorld(entityManager = manager)

        // Build chain: Extractor (2,5) -> Omni-Router (3,5) -> East Silo (4,5) & South Silo (3,6)
        val drill = PlanetaryEntityFactory.spawnBuilding(world, 2, 5, BuildingType.DRILL_MK1, Direction.EAST)
        val router = PlanetaryEntityFactory.spawnBuilding(world, 3, 5, BuildingType.OMNI_ROUTER, Direction.EAST)
        val eastSilo = PlanetaryEntityFactory.spawnBuilding(world, 4, 5, BuildingType.STORAGE_SILO, Direction.EAST)
        val southSilo = PlanetaryEntityFactory.spawnBuilding(world, 3, 6, BuildingType.STORAGE_SILO, Direction.SOUTH)

        // Seed extractor inventory with 6 Iron Ore
        val drillInv = manager.getComponent<com.example.ecs.Inventory>(drill)!!
        assertEquals(6, drillInv.add(ResourceType.IRON_ORE, 6))

        // Add an input filter to a third test inventory to verify filter enforcement
        val filteredInv = com.example.ecs.Inventory(
            capacity = 5,
            transferMode = com.example.ecs.TransferMode.INPUT_ONLY,
            inputFilter = setOf(ResourceType.COPPER_ORE)
        )
        assertEquals(0, drillInv.transferTo(filteredInv, ResourceType.IRON_ORE, 2))
        assertEquals(6, drillInv.countOf(ResourceType.IRON_ORE))

        // Drive InventorySystem across 6 seconds in 0.5s ticks
        val inventorySystem = com.example.ecs.InventorySystem()
        repeat(12) {
            inventorySystem.update(manager, dtSeconds = 0.5f)
        }

        val eastInv = manager.getComponent<com.example.ecs.Inventory>(eastSilo)!!
        val southInv = manager.getComponent<com.example.ecs.Inventory>(southSilo)!!
        val routerInv = manager.getComponent<com.example.ecs.Inventory>(router)!!

        // Conservation of mass: all 6 ore remain accounted for and were split across east & south silos
        val total = drillInv.countOf(ResourceType.IRON_ORE) +
            routerInv.countOf(ResourceType.IRON_ORE) +
            eastInv.countOf(ResourceType.IRON_ORE) +
            southInv.countOf(ResourceType.IRON_ORE)
        assertEquals(6, total)
        assertTrue("Expected East Silo to receive Iron Ore via router, got ${eastInv.countOf(ResourceType.IRON_ORE)}", eastInv.countOf(ResourceType.IRON_ORE) >= 2)
        assertTrue("Expected South Silo to receive Iron Ore via router, got ${southInv.countOf(ResourceType.IRON_ORE)}", southInv.countOf(ResourceType.IRON_ORE) >= 2)
    }

    @Test
    fun energyGridSystem_distributesSupplyToConsumersAndDetectsSubNetworks() {
        val manager = com.example.ecs.EntityManager()
        val world = EcsWorld(entityManager = manager)

        // Sub-network A (around x=2..4): 30 MW generator feeding two 30 MW consumers -> 50% brownout
        val genA = manager.registerEntity(
            com.example.ecs.Position(gridX = 2, gridY = 2),
            com.example.ecs.StructureComponent(BuildingType.COAL_GENERATOR, 100, 100),
            com.example.ecs.EnergyConsumption(baseGenerationMw = 30f)
        )
        val consumerA1 = manager.registerEntity(
            com.example.ecs.Position(gridX = 3, gridY = 2),
            com.example.ecs.StructureComponent(BuildingType.ARC_SMELTER, 100, 100),
            com.example.ecs.EnergyConsumption(baseDemandMw = 30f)
        )
        val consumerA2 = manager.registerEntity(
            com.example.ecs.Position(gridX = 4, gridY = 2),
            com.example.ecs.StructureComponent(BuildingType.ASSEMBLER, 100, 100),
            com.example.ecs.EnergyConsumption(baseDemandMw = 30f)
        )

        // Sub-network B (isolated at x=12, y=12): consumer with no generator -> blackout (0% efficiency)
        val isolatedConsumer = manager.registerEntity(
            com.example.ecs.Position(gridX = 12, gridY = 12),
            com.example.ecs.StructureComponent(BuildingType.DRILL_MK1, 100, 100),
            com.example.ecs.EnergyConsumption(baseDemandMw = 10f)
        )

        val gridSystem = com.example.ecs.EnergyGridSystem(transmissionRadius = 3)
        val report = gridSystem.update(manager, dtSeconds = 0.05f)

        assertEquals(2, report.networks.size)
        assertEquals(30f, report.totalSupplyMw, 0.001f)
        assertEquals(70f, report.totalDemandMw, 0.001f)
        assertEquals(-40f, report.netMw, 0.001f)

        val netA = report.networks.first { genA in it.producerEntities }
        assertTrue(netA.isBrownout)
        assertTrue(!netA.isBlackout)
        assertEquals(0.5f, netA.satisfactionRatio, 0.001f)
        assertEquals(15f, report.consumerAllocations[consumerA1]!!, 0.001f)
        assertEquals(15f, report.consumerAllocations[consumerA2]!!, 0.001f)
        assertEquals(0.5f, manager.getComponent<com.example.ecs.EnergyConsumption>(consumerA1)!!.gridEfficiency, 0.001f)

        val netB = report.networks.first { isolatedConsumer in it.consumerEntities }
        assertTrue(netB.isBlackout)
        assertEquals(0.0f, netB.satisfactionRatio, 0.001f)
        assertEquals(0.0f, report.consumerAllocations[isolatedConsumer]!!, 0.001f)
        assertEquals(0.0f, manager.getComponent<com.example.ecs.EnergyConsumption>(isolatedConsumer)!!.gridEfficiency, 0.001f)

        // Adding a 60 MW generator near network A resolves its brownout to 100% satisfaction
        manager.registerEntity(
            com.example.ecs.Position(gridX = 2, gridY = 3),
            com.example.ecs.StructureComponent(BuildingType.GEOTHERMAL_TURBINE, 100, 100),
            com.example.ecs.EnergyConsumption(baseGenerationMw = 60f)
        )
        val updated = gridSystem.update(manager, dtSeconds = 0.05f)
        val updatedNetA = updated.networks.first { genA in it.producerEntities }
        assertTrue(!updatedNetA.isBrownout)
        assertEquals(90f, updatedNetA.totalSupplyMw, 0.001f)
        assertEquals(60f, updatedNetA.totalDemandMw, 0.001f)
        assertEquals(1.0f, updatedNetA.satisfactionRatio, 0.001f)
        assertEquals(30f, updated.consumerAllocations[consumerA1]!!, 0.001f)
        assertEquals(1.0f, world.get<com.example.ecs.EnergyConsumption>(consumerA2)!!.gridEfficiency, 0.001f)
    }

    @Test
    fun unitRosterAndCombatAi_playerUnitsEngageHostilesAwardBountiesAndRepairBuildings() {
        assertEquals(5, com.example.data.model.UnitCatalog.playerUnits.size)
        assertEquals(5, com.example.data.model.UnitCatalog.enemyUnits.size)

        val initial = FactoryEngine.createInitialPlanetState("temperate")
        val runtime = EcsPlanetRuntime("temperate", initial)

        // Starter garrison includes 1 patrolling Aegis Recon Drone
        assertEquals(1, runtime.exportSnapshot().units.count { it.isPlayer })

        // Deploy a Bastion Exosuit and a Valkyrie Repair Mech, plus spawn an enemy Xeno Skitterer
        val exosuit = com.example.data.model.UnitCatalog.byId("uec_heavy_trooper")!!
        val repairMech = com.example.data.model.UnitCatalog.byId("uec_repair_mech")!!
        val skitterer = com.example.data.model.UnitCatalog.byId("enemy_skitterer")!!

        runtime.spawnPlayerUnit(exosuit)
        runtime.spawnPlayerUnit(repairMech)
        runtime.spawnEnemyUnit(skitterer, edge = 0)

        // Damage a placed building to test Valkyrie Repair Mech welding
        val damagedStruct = runtime.world.query<com.example.ecs.StructureComponent>()
            .first { (_, s) -> s.buildingType != BuildingType.ORBITAL_HUB }.second
        damagedStruct.integrity = 40

        // Advance simulation 20 seconds (400 fixed 20Hz ticks)
        repeat(400) {
            runtime.stepFixedTick(EcsPlanetRuntime.FIXED_STEP_SECONDS)
        }

        val snapshot = runtime.exportSnapshot()
        assertEquals("Expected UEC garrison to eliminate Xeno Skitterer", 0, snapshot.units.count { !it.isPlayer })
        assertTrue("Expected credit bounty from eliminated enemy", runtime.context.pendingBountyCredits >= skitterer.creditBounty)
        assertTrue("Expected Valkyrie Repair Mech to heal damaged building above 40, got ${damagedStruct.integrity}", damagedStruct.integrity > 40)
    }
}







