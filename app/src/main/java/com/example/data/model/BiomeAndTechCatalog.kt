package com.example.data.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R

enum class HazardVisualType { NONE, ACID_RAIN, LAVA_SURGE, ASH_STORM, CRYO_BLIZZARD, ION_SANDSTORM, SOLAR_FLARE }

data class EnvironmentalHazard(
    val id: String,
    val name: String,
    val biomeId: String,
    val visualType: HazardVisualType,
    val description: String,
    val counterStrategy: String,
    val beltSpeedMultiplier: Float = 1.0f,
    val drillOutputMultiplier: Float = 1.0f,
    val solarOutputMultiplier: Float = 1.0f,
    val powerDemandMultiplier: Float = 1.0f,
    val integrityDamagePerCycle: Int = 0,
    val mitigatedBy: BuildingType
)

data class PlanetBiome(
    val id: String,
    val designation: String,
    val name: String,
    val climateTag: String,
    val ambientTempC: Int,
    val solarMultiplier: Float,
    val geothermalBonusMw: Int,
    val description: String,
    val primaryColor: Color,
    val groundDark: Color,
    val groundLight: Color,
    val hazardTileColor: Color,
    val hazardTileLabel: String,
    @DrawableRes val bannerRes: Int?,
    val resourceRichness: Map<ResourceType, Int>, // 1 to 5 rating
    val hazards: List<EnvironmentalHazard>,
    val unlockColonizationScore: Int = 0
)

object BiomeCatalog {
    val temperate = PlanetBiome(
        id = "temperate",
        designation = "KEPLER-452B",
        name = "Aethelgard Basin",
        climateTag = "TEMPERATE FRONTIER",
        ambientTempC = 19,
        solarMultiplier = 1.0f,
        geothermalBonusMw = 0,
        description = "Balanced starter basin selected for UEC Phase-1 landing. Abundant Iron, Copper, and Coal seams with periodic acidic squalls.",
        primaryColor = Color(0xFF10B981),
        groundDark = Color(0xFF15221C),
        groundLight = Color(0xFF1C2E26),
        hazardTileColor = Color(0xFF285E61),
        hazardTileLabel = "Acidic Marsh",
        bannerRes = null,
        resourceRichness = mapOf(
            ResourceType.IRON_ORE to 5,
            ResourceType.COPPER_ORE to 4,
            ResourceType.COAL to 4,
            ResourceType.SILICA to 3,
            ResourceType.TITANIUM_ORE to 2
        ),
        hazards = listOf(
            EnvironmentalHazard(
                id = "acid_squall",
                name = "Acidic Squall",
                biomeId = "temperate",
                visualType = HazardVisualType.ACID_RAIN,
                description = "Corrosive precipitation degrades unshielded conveyors and exposed drills over time.",
                counterStrategy = "Cover production clusters with Atmospheric Scrubbers to neutralize rainfall.",
                drillOutputMultiplier = 0.85f,
                integrityDamagePerCycle = 2,
                mitigatedBy = BuildingType.ATMOS_SCRUBBER
            )
        )
    )

    val volcanic = PlanetBiome(
        id = "volcanic",
        designation = "VULCANA-IV",
        name = "Caldera Prime",
        climateTag = "VOLCANIC FOUNDRY",
        ambientTempC = 340,
        solarMultiplier = 0.7f,
        geothermalBonusMw = 30,
        description = "Tectonically violent world rich in Titanium, Iron, and Geothermal fissures. Active magma channels and dense ash clouds challenge solar and belt lines.",
        primaryColor = Color(0xFFFF5722),
        groundDark = Color(0xFF1D1313),
        groundLight = Color(0xFF281918),
        hazardTileColor = Color(0xFFFF4500),
        hazardTileLabel = "Active Magma Flow",
        bannerRes = R.drawable.img_biome_volcanic,
        resourceRichness = mapOf(
            ResourceType.IRON_ORE to 5,
            ResourceType.TITANIUM_ORE to 5,
            ResourceType.COAL to 5,
            ResourceType.COPPER_ORE to 3,
            ResourceType.SILICA to 3
        ),
        hazards = listOf(
            EnvironmentalHazard(
                id = "lava_surge",
                name = "Magma Surge",
                biomeId = "volcanic",
                visualType = HazardVisualType.LAVA_SURGE,
                description = "Subterranean pressure spikes superheat terrain around lava channels, rapidly damaging uncooled factories.",
                counterStrategy = "Place Thermal Shield Pylons near lava corridors to actively dissipate heat.",
                powerDemandMultiplier = 1.25f,
                integrityDamagePerCycle = 4,
                mitigatedBy = BuildingType.THERMAL_PYLON
            ),
            EnvironmentalHazard(
                id = "ash_storm",
                name = "Volcanic Ash Storm",
                biomeId = "volcanic",
                visualType = HazardVisualType.ASH_STORM,
                description = "Pyroclastic ash blots out sunlight (-75% Solar output) and clogs unguarded extractors & belts (-40%).",
                counterStrategy = "Switch to Geothermal Turbines and deploy Atmospheric Scrubbers across drill arrays.",
                beltSpeedMultiplier = 0.65f,
                drillOutputMultiplier = 0.60f,
                solarOutputMultiplier = 0.25f,
                integrityDamagePerCycle = 1,
                mitigatedBy = BuildingType.ATMOS_SCRUBBER
            )
        )
    )

    val cryo = PlanetBiome(
        id = "cryo",
        designation = "GLACIES-IX",
        name = "Borealis Drift",
        climateTag = "CRYO FRONTIER",
        ambientTempC = -118,
        solarMultiplier = 0.65f,
        geothermalBonusMw = 10,
        description = "Sub-zero glacial frontier holding vast Cryo-Gel reserves, Copper veins, and Quartz crystals. Severe blizzards freeze exposed conveyor lines.",
        primaryColor = Color(0xFF38BDF8),
        groundDark = Color(0xFF101E2B),
        groundLight = Color(0xFF172C3E),
        hazardTileColor = Color(0xFF0EA5E9),
        hazardTileLabel = "Glacial Fissure",
        bannerRes = R.drawable.img_biome_cryo,
        resourceRichness = mapOf(
            ResourceType.CRYO_GEL to 5,
            ResourceType.COPPER_ORE to 5,
            ResourceType.SILICA to 4,
            ResourceType.TITANIUM_ORE to 4,
            ResourceType.IRON_ORE to 3
        ),
        hazards = listOf(
            EnvironmentalHazard(
                id = "cryo_blizzard",
                name = "Cryo Blizzard",
                biomeId = "cryo",
                visualType = HazardVisualType.CRYO_BLIZZARD,
                description = "Whiteout gale drops ambient temps to -160°C, halving unheated conveyor speed and icing mechanical joints.",
                counterStrategy = "Cluster layouts within Thermal Shield Pylons or upgrade belts to Mag-Lev Mk.3.",
                beltSpeedMultiplier = 0.50f,
                drillOutputMultiplier = 0.70f,
                powerDemandMultiplier = 1.45f,
                integrityDamagePerCycle = 2,
                mitigatedBy = BuildingType.THERMAL_PYLON
            ),
            EnvironmentalHazard(
                id = "thermal_snap",
                name = "Sub-Zero Thermal Snap",
                biomeId = "cryo",
                visualType = HazardVisualType.CRYO_BLIZZARD,
                description = "Sudden atmospheric plunge spikes grid heating demand by +60%, risking cascading brownouts.",
                counterStrategy = "Maintain reserve Coal/Fusion MW headroom and insulate factories with Thermal Pylons.",
                powerDemandMultiplier = 1.60f,
                integrityDamagePerCycle = 1,
                mitigatedBy = BuildingType.THERMAL_PYLON
            )
        )
    )

    val arid = PlanetBiome(
        id = "arid",
        designation = "SOLIS-VII",
        name = "Kalahari Rift",
        climateTag = "IRRADIATED BADLANDS",
        ambientTempC = 64,
        solarMultiplier = 1.50f,
        geothermalBonusMw = 0,
        description = "Sun-scorched sandstone canyons rich in Uranium, Silica, and Copper. Extreme solar yield (+50%) offset by ionized dust squalls and flares.",
        primaryColor = Color(0xFFEAB308),
        groundDark = Color(0xFF261A12),
        groundLight = Color(0xFF332318),
        hazardTileColor = Color(0xFFD97706),
        hazardTileLabel = "Ionized Chasm",
        bannerRes = R.drawable.img_biome_arid,
        resourceRichness = mapOf(
            ResourceType.URANIUM_ORE to 5,
            ResourceType.SILICA to 5,
            ResourceType.COPPER_ORE to 5,
            ResourceType.TITANIUM_ORE to 3,
            ResourceType.IRON_ORE to 3
        ),
        hazards = listOf(
            EnvironmentalHazard(
                id = "ion_sandstorm",
                name = "Ionized Sandstorm",
                biomeId = "arid",
                visualType = HazardVisualType.ION_SANDSTORM,
                description = "Electrostatically charged dust abrades machinery, spikes power draw, and degrades exposed electronics.",
                counterStrategy = "Install Atmospheric Scrubbers to ground static fields and preserve belt velocity.",
                beltSpeedMultiplier = 0.75f,
                solarOutputMultiplier = 0.45f,
                powerDemandMultiplier = 1.35f,
                integrityDamagePerCycle = 3,
                mitigatedBy = BuildingType.ATMOS_SCRUBBER
            ),
            EnvironmentalHazard(
                id = "solar_flare",
                name = "Orbital Solar Flare",
                biomeId = "arid",
                visualType = HazardVisualType.SOLAR_FLARE,
                description = "Massive radiation pulse supercharges Solar Mirrors (+80%) while scorching unshielded infrastructure.",
                counterStrategy = "Protect core assemblers under Thermal Shield Pylons to harness free surplus power safely.",
                solarOutputMultiplier = 1.80f,
                integrityDamagePerCycle = 4,
                mitigatedBy = BuildingType.THERMAL_PYLON
            )
        )
    )

    val allBiomes: List<PlanetBiome> = listOf(temperate, volcanic, cryo, arid)

    fun byId(id: String): PlanetBiome = allBiomes.firstOrNull { it.id == id } ?: temperate
}

data class TechNode(
    val id: String,
    val tier: Int,
    val branch: String,
    val title: String,
    val description: String,
    val prerequisites: List<String>,
    val resourceCosts: Map<ResourceType, Int>,
    val creditCost: Int,
    val unlocksBuildings: List<BuildingType> = emptyList(),
    val unlocksRecipes: List<String> = emptyList(),
    val globalBonusText: String? = null
)

object TechTreeCatalog {
    val nodes: List<TechNode> = listOf(
        // TIER 1: PLANETARY DROP & BASIC EXTRACTION
        TechNode(
            id = "t1_extraction",
            tier = 1,
            branch = "EXTRACTION",
            title = "Orbital Drop Automation",
            description = "Standard UEC starter kit: kinetic drills, Mk.1 conveyor belts, arc smelting, and baseline coal power.",
            prerequisites = emptyList(),
            resourceCosts = emptyMap(),
            creditCost = 0,
            unlocksBuildings = listOf(
                BuildingType.DRILL_MK1,
                BuildingType.CONVEYOR_MK1,
                BuildingType.ARC_SMELTER,
                BuildingType.COAL_GENERATOR
            ),
            unlocksRecipes = listOf("Iron Plate", "Copper Wire")
        ),
        TechNode(
            id = "t1_logistics",
            tier = 1,
            branch = "LOGISTICS",
            title = "Belt Routing & Buffering",
            description = "Split single mining veins across multiple smelters with Omni-Routers and buffer output in Pressurized Silos.",
            prerequisites = listOf("t1_extraction"),
            resourceCosts = mapOf(ResourceType.IRON_PLATE to 20, ResourceType.COPPER_WIRE to 12),
            creditCost = 40,
            unlocksBuildings = listOf(BuildingType.OMNI_ROUTER, BuildingType.STORAGE_SILO)
        ),
        TechNode(
            id = "t1_steel",
            tier = 1,
            branch = "METALLURGY",
            title = "Foundry Metallurgy",
            description = "Unlock high-heat Industrial Foundries capable of smelting carbon-infused Steel Beams.",
            prerequisites = listOf("t1_extraction"),
            resourceCosts = mapOf(ResourceType.IRON_PLATE to 25, ResourceType.COAL to 20),
            creditCost = 60,
            unlocksBuildings = listOf(BuildingType.FOUNDRY),
            unlocksRecipes = listOf("Steel Beam")
        ),

        // TIER 2: PRECISION AUTOMATION & ENVIRONMENTAL HARDENING
        TechNode(
            id = "t2_assembly",
            tier = 2,
            branch = "CRAFTING",
            title = "Modular Assembly Lines",
            description = "Multi-input Precision Assemblers unlock Circuit Boards and Reinforced Plates for advanced infrastructure.",
            prerequisites = listOf("t1_logistics", "t1_steel"),
            resourceCosts = mapOf(ResourceType.STEEL_BEAM to 18, ResourceType.COPPER_WIRE to 24, ResourceType.SILICA to 14),
            creditCost = 100,
            unlocksBuildings = listOf(BuildingType.ASSEMBLER),
            unlocksRecipes = listOf("Circuit Board", "Reinforced Plate")
        ),
        TechNode(
            id = "t2_hazards",
            tier = 2,
            branch = "PLANETARY MGMT",
            title = "Environmental Hardening",
            description = "Deploy Thermal Shield Pylons and Atmospheric Scrubbers to withstand Volcanic Ash, Lava Surges, and Cryo Blizzards.",
            prerequisites = listOf("t1_steel"),
            resourceCosts = mapOf(ResourceType.STEEL_BEAM to 16, ResourceType.IRON_PLATE to 25, ResourceType.SILICA to 12),
            creditCost = 110,
            unlocksBuildings = listOf(BuildingType.THERMAL_PYLON, BuildingType.ATMOS_SCRUBBER),
            globalBonusText = "+20% Base Structural Armor"
        ),
        TechNode(
            id = "t2_solar",
            tier = 2,
            branch = "POWER",
            title = "Photovoltaic Mirror Arrays",
            description = "Fuel-free renewable energy arrays ideal for Arid Badlands and low-coal planetary sectors.",
            prerequisites = listOf("t2_assembly"),
            resourceCosts = mapOf(ResourceType.CIRCUIT_BOARD to 8, ResourceType.SILICA to 24, ResourceType.STEEL_BEAM to 12),
            creditCost = 120,
            unlocksBuildings = listOf(BuildingType.SOLAR_COLLECTOR)
        ),

        // TIER 3: HIGH-SPEED LOGISTICS, GEOTHERMAL & DEFENSE
        TechNode(
            id = "t3_mk2_logistics",
            tier = 3,
            branch = "LOGISTICS",
            title = "Mk.2 Logistics & Deep Drilling",
            description = "Doubles extraction and transport velocity with High-Speed Belts Mk.2 and Deep Core Drills.",
            prerequisites = listOf("t2_assembly"),
            resourceCosts = mapOf(ResourceType.REINFORCED_PLATE to 16, ResourceType.CIRCUIT_BOARD to 14, ResourceType.STEEL_BEAM to 24),
            creditCost = 180,
            unlocksBuildings = listOf(BuildingType.CONVEYOR_MK2, BuildingType.DRILL_MK2)
        ),
        TechNode(
            id = "t3_defense",
            tier = 3,
            branch = "DEFENSE",
            title = "UEC Perimeter Defense",
            description = "Automated Rail-Turrets and Munitions Crate assembly to repel aggressive wildlife migrations and fulfill military drops.",
            prerequisites = listOf("t2_assembly", "t2_hazards"),
            resourceCosts = mapOf(ResourceType.REINFORCED_PLATE to 14, ResourceType.STEEL_BEAM to 20, ResourceType.CIRCUIT_BOARD to 10),
            creditCost = 200,
            unlocksBuildings = listOf(BuildingType.AUTO_TURRET),
            unlocksRecipes = listOf("UEC Munitions Crate")
        ),
        TechNode(
            id = "t3_geothermal",
            tier = 3,
            branch = "POWER",
            title = "Geothermal Crust Tapping",
            description = "Weather-proof 65 MW Geothermal Turbines and Thermal Alloy smelting for extreme Volcanic & Cryo worlds.",
            prerequisites = listOf("t2_solar", "t2_hazards"),
            resourceCosts = mapOf(ResourceType.STEEL_BEAM to 24, ResourceType.TITANIUM_ORE to 18, ResourceType.CIRCUIT_BOARD to 12),
            creditCost = 220,
            unlocksBuildings = listOf(BuildingType.GEOTHERMAL_TURBINE),
            unlocksRecipes = listOf("Thermal Alloy")
        ),

        // TIER 4: PLANETARY COLONIZATION & FUSION POWER
        TechNode(
            id = "t4_colonist",
            tier = 4,
            branch = "PLANETARY MGMT",
            title = "UEC Colony Life-Support",
            description = "Cryo/Thermal Pumps, Heavy Modular Frames, and Colony Med-Pods required for civilian orbital settlements.",
            prerequisites = listOf("t3_mk2_logistics", "t3_geothermal"),
            resourceCosts = mapOf(ResourceType.THERMAL_ALLOY to 14, ResourceType.REINFORCED_PLATE to 20, ResourceType.CRYO_GEL to 14),
            creditCost = 320,
            unlocksBuildings = listOf(BuildingType.THERMAL_EXTRACTOR),
            unlocksRecipes = listOf("Colony Med-Pod", "Heavy Modular Frame")
        ),
        TechNode(
            id = "t4_maglev",
            tier = 4,
            branch = "LOGISTICS",
            title = "Mag-Lev Hyper-Logistics",
            description = "Tier-3 frictionless magnetic belts moving cargo at maximum tick rate while resisting Cryo Blizzard freezing.",
            prerequisites = listOf("t3_mk2_logistics"),
            resourceCosts = mapOf(ResourceType.THERMAL_ALLOY to 16, ResourceType.CIRCUIT_BOARD to 20, ResourceType.TITANIUM_ORE to 22),
            creditCost = 340,
            unlocksBuildings = listOf(BuildingType.CONVEYOR_MK3)
        ),
        TechNode(
            id = "t4_nuclear",
            tier = 4,
            branch = "POWER",
            title = "Magnetic Fusion Containment",
            description = "Massive 140 MW UEC Fusion Reactors fueled by Uranium Fuel Cells and stabilized with Cryo-Cores.",
            prerequisites = listOf("t3_geothermal", "t4_colonist"),
            resourceCosts = mapOf(ResourceType.HEAVY_FRAME to 10, ResourceType.URANIUM_ORE to 18, ResourceType.THERMAL_ALLOY to 16),
            creditCost = 450,
            unlocksBuildings = listOf(BuildingType.FUSION_CORE),
            unlocksRecipes = listOf("Cryo-Core", "Fusion Fuel Cell")
        ),

        // TIER 5: ORBITAL SUPREMACY & PLANETARY MASTERY
        TechNode(
            id = "t5_quantum",
            tier = 5,
            branch = "CRAFTING",
            title = "Quantum Molecular Fabrication",
            description = "Unlocks the Quantum Manufacturer to synthesize Orbital Quantum Matrices for deep-space UEC relays.",
            prerequisites = listOf("t4_maglev", "t4_nuclear"),
            resourceCosts = mapOf(ResourceType.HEAVY_FRAME to 14, ResourceType.CRYO_CORE to 10, ResourceType.FUEL_CELL to 8),
            creditCost = 650,
            unlocksBuildings = listOf(BuildingType.QUANTUM_MFG),
            unlocksRecipes = listOf("Orbital Quantum Matrix")
        ),
        TechNode(
            id = "t5_vanguard",
            tier = 5,
            branch = "UEC COMMAND",
            title = "Vanguard Fleet Readiness",
            description = "Final planetary certification. Overclocks every extractor & assembler by +35% and boosts UEC Contract payouts by +50%.",
            prerequisites = listOf("t5_quantum", "t3_defense"),
            resourceCosts = mapOf(ResourceType.QUANTUM_MATRIX to 6, ResourceType.MUNITIONS_CRATE to 12, ResourceType.COLONY_MED_POD to 10),
            creditCost = 1000,
            globalBonusText = "+35% Factory Output & +50% UEC Contract Rewards"
        )
    )

    fun byId(id: String): TechNode? = nodes.firstOrNull { it.id == id }
}
