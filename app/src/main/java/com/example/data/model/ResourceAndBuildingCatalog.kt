package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class Direction(val dx: Int, val dy: Int, val degrees: Float, val shortLabel: String) {
    NORTH(0, -1, 0f, "N"),
    EAST(1, 0, 90f, "E"),
    SOUTH(0, 1, 180f, "S"),
    WEST(-1, 0, 270f, "W");

    fun rotateClockwise(): Direction = when (this) {
        NORTH -> EAST
        EAST -> SOUTH
        SOUTH -> WEST
        WEST -> NORTH
    }

    companion object {
        fun safeValueOf(raw: String?): Direction =
            entries.firstOrNull { it.name == raw } ?: EAST
    }
}

enum class ResourceCategory { RAW, INTERMEDIATE, ADVANCED, UEC_SUPPLY }

enum class ResourceType(
    val displayName: String,
    val code: String,
    val category: ResourceCategory,
    val color: Color,
    val creditValue: Int
) {
    // Raw planetary ores & gases
    IRON_ORE("Iron Ore", "Fe", ResourceCategory.RAW, Color(0xFFB0BEC5), 2),
    COPPER_ORE("Copper Ore", "Cu", ResourceCategory.RAW, Color(0xFFE07A5F), 2),
    COAL("Coal", "C", ResourceCategory.RAW, Color(0xFF475569), 3),
    SILICA("Silica Quartz", "Si", ResourceCategory.RAW, Color(0xFFE2E8F0), 4),
    TITANIUM_ORE("Titanium Ore", "Ti", ResourceCategory.RAW, Color(0xFF94A3B8), 6),
    CRYO_GEL("Cryo-Gel", "Cg", ResourceCategory.RAW, Color(0xFF38BDF8), 8),
    URANIUM_ORE("Uranium Ore", "U", ResourceCategory.RAW, Color(0xFF84CC16), 12),

    // Smelted & Intermediate Components
    IRON_PLATE("Iron Plate", "FeP", ResourceCategory.INTERMEDIATE, Color(0xFFCBD5E1), 6),
    COPPER_WIRE("Copper Wire", "CuW", ResourceCategory.INTERMEDIATE, Color(0xFFF59E0B), 6),
    STEEL_BEAM("Steel Beam", "Stl", ResourceCategory.INTERMEDIATE, Color(0xFF64748B), 14),
    CIRCUIT_BOARD("Circuit Board", "PCB", ResourceCategory.INTERMEDIATE, Color(0xFF10B981), 22),
    REINFORCED_PLATE("Reinforced Plate", "RPl", ResourceCategory.INTERMEDIATE, Color(0xFF9CA3AF), 24),

    // Advanced Industrial & Hazard Components
    THERMAL_ALLOY("Thermal Alloy", "ThA", ResourceCategory.ADVANCED, Color(0xFFF97316), 38),
    CRYO_CORE("Cryo-Core", "CrC", ResourceCategory.ADVANCED, Color(0xFF06B6D4), 48),
    FUEL_CELL("Fusion Fuel Cell", "FC", ResourceCategory.ADVANCED, Color(0xFFA3E635), 65),
    HEAVY_FRAME("Heavy Modular Frame", "HMF", ResourceCategory.ADVANCED, Color(0xFF60A5FA), 80),

    // UEC Military, Colonist & Orbital Supply Exports
    MUNITIONS_CRATE("UEC Munitions Crate", "MUN", ResourceCategory.UEC_SUPPLY, Color(0xFFEF4444), 55),
    COLONY_MED_POD("Colony Med-Pod", "MED", ResourceCategory.UEC_SUPPLY, Color(0xFF2DD4BF), 75),
    QUANTUM_MATRIX("Orbital Quantum Matrix", "QMX", ResourceCategory.UEC_SUPPLY, Color(0xFFC084FC), 160);

    companion object {
        fun safeValueOf(raw: String?): ResourceType? =
            entries.firstOrNull { it.name == raw }
    }
}

data class CraftingRecipe(
    val id: String,
    val output: ResourceType,
    val outputAmount: Int,
    val inputs: Map<ResourceType, Int>,
    val ticksRequired: Int,
    val machineTypes: Set<BuildingType>,
    val requiredTechId: String? = null
)

enum class BuildingCategory(val label: String) {
    LOGISTICS("Logistics"),
    EXTRACTION("Extract"),
    CRAFTING("Process"),
    POWER("Power"),
    DEFENSE("Hazard & Def")
}

enum class BuildingType(
    val displayName: String,
    val shortCode: String,
    val category: BuildingCategory,
    val description: String,
    val buildCost: Map<ResourceType, Int>,
    val powerMw: Int, // Positive = generates MW, Negative = consumes MW
    val maxIntegrity: Int,
    val accentColor: Color,
    val requiredTechId: String?,
    val beltSpeedTicks: Int = 0, // Lower is faster for conveyors
    val protectionRadius: Int = 0, // Tile radius for shields/scrubbers/turrets
    val defaultRecipeId: String = "NONE"
) {
    ORBITAL_HUB(
        displayName = "UEC Orbital Hub",
        shortCode = "HUB",
        category = BuildingCategory.LOGISTICS,
        description = "Central planetary drop-core. Accepts conveyor inputs into global storage and launches UEC orbital supply drops.",
        buildCost = emptyMap(),
        powerMw = 25,
        maxIntegrity = 250,
        accentColor = Color(0xFF22D3EE),
        requiredTechId = null
    ),
    DRILL_MK1(
        displayName = "Kinetic Drill Mk.1",
        shortCode = "DR1",
        category = BuildingCategory.EXTRACTION,
        description = "Extracts raw planetary ores from mineral veins onto the front tile or Hub buffer.",
        buildCost = mapOf(ResourceType.IRON_PLATE to 8, ResourceType.COPPER_WIRE to 4),
        powerMw = -6,
        maxIntegrity = 100,
        accentColor = Color(0xFFF59E0B),
        requiredTechId = "t1_extraction"
    ),
    DRILL_MK2(
        displayName = "Deep Core Drill Mk.2",
        shortCode = "DR2",
        category = BuildingCategory.EXTRACTION,
        description = "Double-speed deep-crust extractor capable of mining Titanium and Uranium efficiently.",
        buildCost = mapOf(ResourceType.STEEL_BEAM to 12, ResourceType.CIRCUIT_BOARD to 6, ResourceType.REINFORCED_PLATE to 8),
        powerMw = -14,
        maxIntegrity = 140,
        accentColor = Color(0xFFFBBF24),
        requiredTechId = "t3_mk2_logistics"
    ),
    THERMAL_EXTRACTOR(
        displayName = "Cryo/Thermal Pump",
        shortCode = "PMP",
        category = BuildingCategory.EXTRACTION,
        description = "Specialized pressurized extractor for Cryo-Gel pockets and high-yield volatile seams.",
        buildCost = mapOf(ResourceType.THERMAL_ALLOY to 8, ResourceType.HEAVY_FRAME to 4),
        powerMw = -16,
        maxIntegrity = 150,
        accentColor = Color(0xFF38BDF8),
        requiredTechId = "t4_colonist"
    ),
    CONVEYOR_MK1(
        displayName = "Conveyor Belt Mk.1",
        shortCode = "BL1",
        category = BuildingCategory.LOGISTICS,
        description = "Directional industrial transport belt. Moves cargo packets along your factory floor.",
        buildCost = mapOf(ResourceType.IRON_PLATE to 1),
        powerMw = 0,
        maxIntegrity = 60,
        accentColor = Color(0xFF94A3B8),
        requiredTechId = "t1_extraction",
        beltSpeedTicks = 3
    ),
    CONVEYOR_MK2(
        displayName = "High-Speed Belt Mk.2",
        shortCode = "BL2",
        category = BuildingCategory.LOGISTICS,
        description = "Reinforced dual-track logistics belt with 2x throughput speed.",
        buildCost = mapOf(ResourceType.STEEL_BEAM to 2, ResourceType.REINFORCED_PLATE to 1),
        powerMw = -1,
        maxIntegrity = 90,
        accentColor = Color(0xFF38BDF8),
        requiredTechId = "t3_mk2_logistics",
        beltSpeedTicks = 2
    ),
    CONVEYOR_MK3(
        displayName = "Mag-Lev Belt Mk.3",
        shortCode = "BL3",
        category = BuildingCategory.LOGISTICS,
        description = "Frictionless magnetic levitation track. Max speed and immune to Cryo-Blizzard freezing.",
        buildCost = mapOf(ResourceType.THERMAL_ALLOY to 2, ResourceType.CIRCUIT_BOARD to 2),
        powerMw = -2,
        maxIntegrity = 120,
        accentColor = Color(0xFFC084FC),
        requiredTechId = "t4_maglev",
        beltSpeedTicks = 1
    ),
    OMNI_ROUTER(
        displayName = "Omni-Splitter Router",
        shortCode = "RTR",
        category = BuildingCategory.LOGISTICS,
        description = "Distributes incoming conveyor cargo across left, forward, and right output belts.",
        buildCost = mapOf(ResourceType.IRON_PLATE to 6, ResourceType.COPPER_WIRE to 4),
        powerMw = -1,
        maxIntegrity = 80,
        accentColor = Color(0xFF60A5FA),
        requiredTechId = "t1_logistics",
        beltSpeedTicks = 2
    ),
    STORAGE_SILO(
        displayName = "Pressurized Silo",
        shortCode = "SLO",
        category = BuildingCategory.LOGISTICS,
        description = "Absorbs belt throughput directly into planetary inventory and shields reserves during scarcity.",
        buildCost = mapOf(ResourceType.IRON_PLATE to 14, ResourceType.STEEL_BEAM to 4),
        powerMw = -2,
        maxIntegrity = 160,
        accentColor = Color(0xFF2DD4BF),
        requiredTechId = "t1_logistics"
    ),
    ARC_SMELTER(
        displayName = "Arc Smelter",
        shortCode = "SML",
        category = BuildingCategory.CRAFTING,
        description = "Refines Iron Ore into Iron Plates and Copper Ore into Copper Wire automatically.",
        buildCost = mapOf(ResourceType.IRON_PLATE to 10, ResourceType.COPPER_WIRE to 6),
        powerMw = -8,
        maxIntegrity = 110,
        accentColor = Color(0xFFF97316),
        requiredTechId = "t1_extraction",
        defaultRecipeId = "iron_plate"
    ),
    FOUNDRY(
        displayName = "Industrial Foundry",
        shortCode = "FND",
        category = BuildingCategory.CRAFTING,
        description = "High-heat dual-input furnace producing Steel Beams and Thermal Alloys.",
        buildCost = mapOf(ResourceType.IRON_PLATE to 18, ResourceType.COPPER_WIRE to 10, ResourceType.COAL to 10),
        powerMw = -16,
        maxIntegrity = 135,
        accentColor = Color(0xFFEA580C),
        requiredTechId = "t1_steel",
        defaultRecipeId = "steel_beam"
    ),
    ASSEMBLER(
        displayName = "Precision Assembler",
        shortCode = "ASM",
        category = BuildingCategory.CRAFTING,
        description = "Automated multi-component fabricator for Circuit Boards, Reinforced Plates, Munitions, and Med-Pods.",
        buildCost = mapOf(ResourceType.STEEL_BEAM to 10, ResourceType.IRON_PLATE to 16, ResourceType.COPPER_WIRE to 14),
        powerMw = -20,
        maxIntegrity = 130,
        accentColor = Color(0xFF10B981),
        requiredTechId = "t2_assembly",
        defaultRecipeId = "circuit_board"
    ),
    QUANTUM_MFG(
        displayName = "Quantum Manufacturer",
        shortCode = "QMF",
        category = BuildingCategory.CRAFTING,
        description = "Tier-5 UEC molecular fab producing Heavy Frames, Cryo-Cores, Fuel Cells, and Quantum Matrices.",
        buildCost = mapOf(ResourceType.HEAVY_FRAME to 8, ResourceType.THERMAL_ALLOY to 12, ResourceType.CIRCUIT_BOARD to 14),
        powerMw = -38,
        maxIntegrity = 180,
        accentColor = Color(0xFFC084FC),
        requiredTechId = "t5_quantum",
        defaultRecipeId = "quantum_matrix"
    ),
    COAL_GENERATOR(
        displayName = "Coal Combustion Gen",
        shortCode = "GEN",
        category = BuildingCategory.POWER,
        description = "Burns stocked Coal to deliver 30 MW of reliable baseline grid power.",
        buildCost = mapOf(ResourceType.IRON_PLATE to 12, ResourceType.COPPER_WIRE to 8),
        powerMw = 30,
        maxIntegrity = 110,
        accentColor = Color(0xFFEAB308),
        requiredTechId = "t1_extraction"
    ),
    SOLAR_COLLECTOR(
        displayName = "Solar Mirror Array",
        shortCode = "SOL",
        category = BuildingCategory.POWER,
        description = "Fuel-free photovoltaic array (24 MW base). Boosted on Arid worlds, penalized by Ash Storms.",
        buildCost = mapOf(ResourceType.CIRCUIT_BOARD to 4, ResourceType.SILICA to 14, ResourceType.STEEL_BEAM to 6),
        powerMw = 24,
        maxIntegrity = 90,
        accentColor = Color(0xFF38BDF8),
        requiredTechId = "t2_solar"
    ),
    GEOTHERMAL_TURBINE(
        displayName = "Geothermal Turbine",
        shortCode = "GEO",
        category = BuildingCategory.POWER,
        description = "Taps planetary crust vents for 65 MW of weather-immune power. Bonus output on Volcanic worlds.",
        buildCost = mapOf(ResourceType.THERMAL_ALLOY to 8, ResourceType.STEEL_BEAM to 14, ResourceType.CIRCUIT_BOARD to 8),
        powerMw = 65,
        maxIntegrity = 160,
        accentColor = Color(0xFFFF5722),
        requiredTechId = "t3_geothermal"
    ),
    FUSION_CORE(
        displayName = "UEC Fusion Reactor",
        shortCode = "FUS",
        category = BuildingCategory.POWER,
        description = "End-game magnetic containment reactor generating 140 MW for mega-factories.",
        buildCost = mapOf(ResourceType.HEAVY_FRAME to 10, ResourceType.CRYO_CORE to 6, ResourceType.FUEL_CELL to 4),
        powerMw = 140,
        maxIntegrity = 220,
        accentColor = Color(0xFF22D3EE),
        requiredTechId = "t4_nuclear"
    ),
    THERMAL_PYLON(
        displayName = "Thermal Shield Pylon",
        shortCode = "SHD",
        category = BuildingCategory.DEFENSE,
        description = "Projects a 3-tile thermal & EM field. Prevents Cryo freezing, Lava overheating, and Solar Flare damage.",
        buildCost = mapOf(ResourceType.STEEL_BEAM to 10, ResourceType.COPPER_WIRE to 14, ResourceType.SILICA to 8),
        powerMw = -12,
        maxIntegrity = 150,
        accentColor = Color(0xFF22D3EE),
        requiredTechId = "t2_hazards",
        protectionRadius = 3
    ),
    ATMOS_SCRUBBER(
        displayName = "Atmospheric Scrubber",
        shortCode = "SCR",
        category = BuildingCategory.DEFENSE,
        description = "Clears Ash Storms, Acid Squalls, and Ionized Sandstorms in a 3-tile radius, restoring 100% drill/belt speed.",
        buildCost = mapOf(ResourceType.STEEL_BEAM to 10, ResourceType.IRON_PLATE to 16, ResourceType.CIRCUIT_BOARD to 4),
        powerMw = -10,
        maxIntegrity = 140,
        accentColor = Color(0xFF10B981),
        requiredTechId = "t2_hazards",
        protectionRadius = 3
    ),
    AUTO_TURRET(
        displayName = "UEC Rail-Turret",
        shortCode = "TRT",
        category = BuildingCategory.DEFENSE,
        description = "Automated point-defense sentry protecting buildings within 4 tiles against aggressive wildlife migrations.",
        buildCost = mapOf(ResourceType.STEEL_BEAM to 12, ResourceType.CIRCUIT_BOARD to 6, ResourceType.REINFORCED_PLATE to 8),
        powerMw = -14,
        maxIntegrity = 180,
        accentColor = Color(0xFFEF4444),
        requiredTechId = "t3_defense",
        protectionRadius = 4
    );

    fun isConveyor(): Boolean =
        this == CONVEYOR_MK1 || this == CONVEYOR_MK2 || this == CONVEYOR_MK3 || this == OMNI_ROUTER

    fun isProducer(): Boolean =
        this == ARC_SMELTER || this == FOUNDRY || this == ASSEMBLER || this == QUANTUM_MFG

    fun isExtractor(): Boolean =
        this == DRILL_MK1 || this == DRILL_MK2 || this == THERMAL_EXTRACTOR

    companion object {
        fun safeValueOf(raw: String?): BuildingType =
            entries.firstOrNull { it.name == raw } ?: CONVEYOR_MK1
    }
}

object RecipeCatalog {
    val allRecipes: List<CraftingRecipe> = listOf(
        CraftingRecipe(
            id = "iron_plate",
            output = ResourceType.IRON_PLATE,
            outputAmount = 2,
            inputs = mapOf(ResourceType.IRON_ORE to 2),
            ticksRequired = 6,
            machineTypes = setOf(BuildingType.ARC_SMELTER),
            requiredTechId = "t1_extraction"
        ),
        CraftingRecipe(
            id = "copper_wire",
            output = ResourceType.COPPER_WIRE,
            outputAmount = 2,
            inputs = mapOf(ResourceType.COPPER_ORE to 2),
            ticksRequired = 6,
            machineTypes = setOf(BuildingType.ARC_SMELTER),
            requiredTechId = "t1_extraction"
        ),
        CraftingRecipe(
            id = "steel_beam",
            output = ResourceType.STEEL_BEAM,
            outputAmount = 2,
            inputs = mapOf(ResourceType.IRON_ORE to 3, ResourceType.COAL to 2),
            ticksRequired = 9,
            machineTypes = setOf(BuildingType.FOUNDRY),
            requiredTechId = "t1_steel"
        ),
        CraftingRecipe(
            id = "circuit_board",
            output = ResourceType.CIRCUIT_BOARD,
            outputAmount = 1,
            inputs = mapOf(ResourceType.COPPER_WIRE to 3, ResourceType.SILICA to 2),
            ticksRequired = 10,
            machineTypes = setOf(BuildingType.ASSEMBLER),
            requiredTechId = "t2_assembly"
        ),
        CraftingRecipe(
            id = "reinforced_plate",
            output = ResourceType.REINFORCED_PLATE,
            outputAmount = 1,
            inputs = mapOf(ResourceType.IRON_PLATE to 4, ResourceType.STEEL_BEAM to 2),
            ticksRequired = 10,
            machineTypes = setOf(BuildingType.ASSEMBLER),
            requiredTechId = "t2_assembly"
        ),
        CraftingRecipe(
            id = "thermal_alloy",
            output = ResourceType.THERMAL_ALLOY,
            outputAmount = 1,
            inputs = mapOf(ResourceType.TITANIUM_ORE to 2, ResourceType.STEEL_BEAM to 2, ResourceType.SILICA to 2),
            ticksRequired = 12,
            machineTypes = setOf(BuildingType.FOUNDRY),
            requiredTechId = "t3_geothermal"
        ),
        CraftingRecipe(
            id = "munitions_crate",
            output = ResourceType.MUNITIONS_CRATE,
            outputAmount = 1,
            inputs = mapOf(ResourceType.STEEL_BEAM to 3, ResourceType.COPPER_WIRE to 2, ResourceType.COAL to 2),
            ticksRequired = 12,
            machineTypes = setOf(BuildingType.ASSEMBLER),
            requiredTechId = "t3_defense"
        ),
        CraftingRecipe(
            id = "colony_med_pod",
            output = ResourceType.COLONY_MED_POD,
            outputAmount = 1,
            inputs = mapOf(ResourceType.CRYO_GEL to 2, ResourceType.CIRCUIT_BOARD to 2, ResourceType.REINFORCED_PLATE to 1),
            ticksRequired = 14,
            machineTypes = setOf(BuildingType.ASSEMBLER),
            requiredTechId = "t4_colonist"
        ),
        CraftingRecipe(
            id = "heavy_frame",
            output = ResourceType.HEAVY_FRAME,
            outputAmount = 1,
            inputs = mapOf(ResourceType.REINFORCED_PLATE to 3, ResourceType.STEEL_BEAM to 4, ResourceType.TITANIUM_ORE to 2),
            ticksRequired = 16,
            machineTypes = setOf(BuildingType.ASSEMBLER, BuildingType.QUANTUM_MFG),
            requiredTechId = "t4_colonist"
        ),
        CraftingRecipe(
            id = "cryo_core",
            output = ResourceType.CRYO_CORE,
            outputAmount = 1,
            inputs = mapOf(ResourceType.CRYO_GEL to 4, ResourceType.THERMAL_ALLOY to 2, ResourceType.CIRCUIT_BOARD to 2),
            ticksRequired = 16,
            machineTypes = setOf(BuildingType.QUANTUM_MFG, BuildingType.ASSEMBLER),
            requiredTechId = "t4_nuclear"
        ),
        CraftingRecipe(
            id = "fuel_cell",
            output = ResourceType.FUEL_CELL,
            outputAmount = 1,
            inputs = mapOf(ResourceType.URANIUM_ORE to 3, ResourceType.THERMAL_ALLOY to 2, ResourceType.STEEL_BEAM to 2),
            ticksRequired = 18,
            machineTypes = setOf(BuildingType.QUANTUM_MFG, BuildingType.ASSEMBLER),
            requiredTechId = "t4_nuclear"
        ),
        CraftingRecipe(
            id = "quantum_matrix",
            output = ResourceType.QUANTUM_MATRIX,
            outputAmount = 1,
            inputs = mapOf(ResourceType.HEAVY_FRAME to 2, ResourceType.CRYO_CORE to 1, ResourceType.CIRCUIT_BOARD to 4),
            ticksRequired = 22,
            machineTypes = setOf(BuildingType.QUANTUM_MFG),
            requiredTechId = "t5_quantum"
        )
    )

    fun byId(id: String): CraftingRecipe? = allRecipes.firstOrNull { it.id == id }

    fun forMachine(buildingType: BuildingType, unlockedTechs: Set<String>): List<CraftingRecipe> =
        allRecipes.filter {
            buildingType in it.machineTypes &&
                (it.requiredTechId == null || it.requiredTechId in unlockedTechs)
        }
}
