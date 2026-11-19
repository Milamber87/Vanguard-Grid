package com.example.data.model

enum class DynamicEventCategory(val firestoreValue: String, val label: String) {
    SCARCITY("scarcity", "RESOURCE SCARCITY"),
    WILDLIFE("wildlife", "WILDLIFE MIGRATION"),
    SUPPLY_CONTRACT("supply_contract", "UEC SUPPLY CONTRACT"),
    COSMIC("cosmic", "COSMIC PHENOMENON")
}

data class EventResponseOption(
    val id: String,
    val label: String,
    val detail: String,
    val resourceCost: Map<ResourceType, Int> = emptyMap(),
    val creditCost: Int = 0,
    val creditReward: Int = 0,
    val colonizationBonus: Int = 0,
    val resourceReward: Map<ResourceType, Int> = emptyMap(),
    val requiresTurretOnline: Boolean = false,
    val outcomeSummary: String
)

data class ActiveDynamicEvent(
    val instanceId: String,
    val category: DynamicEventCategory,
    val title: String,
    val briefing: String,
    val planetId: String,
    val remainingSeconds: Int,
    val totalSeconds: Int,
    val scarcityResource: ResourceType? = null,
    val drillYieldMultiplier: Float = 1.0f,
    val solarMultiplier: Float = 1.0f,
    val factorySpeedMultiplier: Float = 1.0f,
    val wildlifeRaidActive: Boolean = false,
    val options: List<EventResponseOption>
)

object DynamicEventFactory {
    fun createRandomEvent(
        planetId: String,
        unlockedTechs: Set<String>,
        seed: Int
    ): ActiveDynamicEvent {
        val roll = kotlin.math.abs(seed) % 4
        return when (roll) {
            0 -> createScarcityEvent(planetId, seed)
            1 -> createWildlifeEvent(planetId, seed)
            2 -> createSupplyContractEvent(planetId, unlockedTechs, seed)
            else -> createCosmicEvent(planetId, seed)
        }
    }

    private fun createScarcityEvent(planetId: String, seed: Int): ActiveDynamicEvent {
        val targetOre = when (planetId) {
            "volcanic" -> if (seed % 2 == 0) ResourceType.TITANIUM_ORE else ResourceType.IRON_ORE
            "cryo" -> if (seed % 2 == 0) ResourceType.CRYO_GEL else ResourceType.COPPER_ORE
            "arid" -> if (seed % 2 == 0) ResourceType.SILICA else ResourceType.URANIUM_ORE
            else -> if (seed % 2 == 0) ResourceType.IRON_ORE else ResourceType.COPPER_ORE
        }
        return ActiveDynamicEvent(
            instanceId = "evt_scarcity_$seed",
            category = DynamicEventCategory.SCARCITY,
            title = "Seismic Vein Collapse: ${targetOre.displayName}",
            briefing = "Subsurface crust shift fractured primary ${targetOre.displayName} seams. Regional drill yield dropped to 35% until stabilized.",
            planetId = planetId,
            remainingSeconds = 75,
            totalSeconds = 75,
            scarcityResource = targetOre,
            drillYieldMultiplier = 0.35f,
            options = listOf(
                EventResponseOption(
                    id = "brace_shafts",
                    label = "Reinforce Bore Shafts",
                    detail = "Spend 10 Iron Plates to stabilize crust fracture immediately (+60 Credits).",
                    resourceCost = mapOf(ResourceType.IRON_PLATE to 10),
                    creditReward = 60,
                    colonizationBonus = 6,
                    outcomeSummary = "Bore shafts braced; ${targetOre.displayName} extraction stabilized."
                ),
                EventResponseOption(
                    id = "orbital_cache",
                    label = "UEC Emergency Ore Drop",
                    detail = "Pay 70 UEC Credits for an orbital cargo drop of +40 ${targetOre.displayName}.",
                    creditCost = 70,
                    resourceReward = mapOf(targetOre to 40),
                    colonizationBonus = 4,
                    outcomeSummary = "Received orbital drop pod with 40 ${targetOre.displayName}."
                ),
                EventResponseOption(
                    id = "ride_out",
                    label = "Rely on Silo Buffers",
                    detail = "Conserve stock and endure reduced extraction until seismic waves subside.",
                    outcomeSummary = "Factory switched to Pressurized Silo reserves during vein collapse."
                )
            )
        )
    }

    private fun createWildlifeEvent(planetId: String, seed: Int): ActiveDynamicEvent {
        val species = when (planetId) {
            "volcanic" -> "Obsidian Magma-Striders"
            "cryo" -> "Glacial Frost-Stalkers"
            "arid" -> "Dune Chitin-Swarm"
            else -> "Armored Spore-Horns"
        }
        return ActiveDynamicEvent(
            instanceId = "evt_wildlife_$seed",
            category = DynamicEventCategory.WILDLIFE,
            title = "Aggressive Migration: $species",
            briefing = "A migrating herd of $species is drawn to factory vibrations! Unprotected perimeter structures take sustained hull damage.",
            planetId = planetId,
            remainingSeconds = 65,
            totalSeconds = 65,
            wildlifeRaidActive = true,
            options = listOf(
                EventResponseOption(
                    id = "turret_intercept",
                    label = "Engage UEC Rail-Turrets",
                    detail = "Requires an active Rail-Turret on the grid. Cleanly repels migration (+150 Credits).",
                    requiresTurretOnline = true,
                    creditReward = 150,
                    colonizationBonus = 10,
                    outcomeSummary = "Rail-Turrets intercepted $species perimeter incursion without casualties."
                ),
                EventResponseOption(
                    id = "sonic_deterrent",
                    label = "Fire Acoustic Deterrent",
                    detail = "Spend 8 Copper Wire + 6 Iron Plates to redirect the herd away from belts.",
                    resourceCost = mapOf(ResourceType.COPPER_WIRE to 8, ResourceType.IRON_PLATE to 6),
                    creditReward = 55,
                    colonizationBonus = 5,
                    outcomeSummary = "Acoustic pulse safely diverted $species away from logistics lines."
                ),
                EventResponseOption(
                    id = "emergency_evac",
                    label = "Throttle Drills & Wait",
                    detail = "Minimize seismic noise while accepting minor perimeter wear.",
                    outcomeSummary = "Reduced acoustic signature until $species migration passed."
                )
            )
        )
    }

    private fun createSupplyContractEvent(
        planetId: String,
        unlockedTechs: Set<String>,
        seed: Int
    ): ActiveDynamicEvent {
        val hasDefense = "t3_defense" in unlockedTechs
        val hasColonist = "t4_colonist" in unlockedTechs
        val hasSteel = "t1_steel" in unlockedTechs

        return when {
            hasColonist && seed % 2 == 0 -> ActiveDynamicEvent(
                instanceId = "evt_contract_$seed",
                category = DynamicEventCategory.SUPPLY_CONTRACT,
                title = "Urgent Airlift: New Horizon Colony",
                briefing = "Civilian transport convoy in high orbit requests immediate Colony Med-Pods and Reinforced Plates before atmospheric entry.",
                planetId = planetId,
                remainingSeconds = 90,
                totalSeconds = 90,
                options = listOf(
                    EventResponseOption(
                        id = "fulfill_colony",
                        label = "Launch Full Orbital Pod",
                        detail = "Deliver 4 Colony Med-Pods + 6 Reinforced Plates for +420 UEC Credits & +15 Readiness.",
                        resourceCost = mapOf(ResourceType.COLONY_MED_POD to 4, ResourceType.REINFORCED_PLATE to 6),
                        creditReward = 420,
                        colonizationBonus = 15,
                        outcomeSummary = "Orbital Hub launched full Colony Med-Pod consignment to New Horizon."
                    ),
                    EventResponseOption(
                        id = "partial_colony",
                        label = "Send Emergency Iron Cache",
                        detail = "Deliver 20 Iron Plates for partial credit (+90 UEC Credits).",
                        resourceCost = mapOf(ResourceType.IRON_PLATE to 20),
                        creditReward = 90,
                        colonizationBonus = 4,
                        outcomeSummary = "Sent partial structural supply drop to New Horizon convoy."
                    )
                )
            )
            hasDefense -> ActiveDynamicEvent(
                instanceId = "evt_contract_$seed",
                category = DynamicEventCategory.SUPPLY_CONTRACT,
                title = "UEC 7th Orbital Division Requisition",
                briefing = "Forward military garrison requires immediate resupply of Munitions Crates and Steel Beams to secure orbital landing zones.",
                planetId = planetId,
                remainingSeconds = 90,
                totalSeconds = 90,
                options = listOf(
                    EventResponseOption(
                        id = "fulfill_mil",
                        label = "Launch Military Supply Drop",
                        detail = "Deliver 4 Munitions Crates + 8 Steel Beams for +320 UEC Credits & +14 Readiness.",
                        resourceCost = mapOf(ResourceType.MUNITIONS_CRATE to 4, ResourceType.STEEL_BEAM to 8),
                        creditReward = 320,
                        colonizationBonus = 14,
                        outcomeSummary = "7th Orbital Division received full Munitions Crate shipment."
                    ),
                    EventResponseOption(
                        id = "partial_mil",
                        label = "Dispatch Steel Reserves",
                        detail = "Deliver 10 Steel Beams for +110 UEC Credits.",
                        resourceCost = mapOf(ResourceType.STEEL_BEAM to 10),
                        creditReward = 110,
                        colonizationBonus = 5,
                        outcomeSummary = "Dispatched emergency Steel Beam shipment to UEC garrison."
                    )
                )
            )
            hasSteel -> ActiveDynamicEvent(
                instanceId = "evt_contract_$seed",
                category = DynamicEventCategory.SUPPLY_CONTRACT,
                title = "UEC Pioneer Habitat Frame Order",
                briefing = "Orbital construction rig needs fabricated Steel Beams and Copper Wire to assemble initial colonist shelter modules.",
                planetId = planetId,
                remainingSeconds = 90,
                totalSeconds = 90,
                options = listOf(
                    EventResponseOption(
                        id = "fulfill_steel",
                        label = "Launch Habitat Kit",
                        detail = "Deliver 10 Steel Beams + 12 Copper Wire for +210 UEC Credits & +12 Readiness.",
                        resourceCost = mapOf(ResourceType.STEEL_BEAM to 10, ResourceType.COPPER_WIRE to 12),
                        creditReward = 210,
                        colonizationBonus = 12,
                        outcomeSummary = "Delivered habitat framing kit to UEC orbital construction rig."
                    ),
                    EventResponseOption(
                        id = "partial_plates",
                        label = "Send Basic Plate Bundle",
                        detail = "Deliver 16 Iron Plates for +80 UEC Credits.",
                        resourceCost = mapOf(ResourceType.IRON_PLATE to 16),
                        creditReward = 80,
                        colonizationBonus = 4,
                        outcomeSummary = "Launched basic Iron Plate shipment to orbital rig."
                    )
                )
            )
            else -> ActiveDynamicEvent(
                instanceId = "evt_contract_$seed",
                category = DynamicEventCategory.SUPPLY_CONTRACT,
                title = "UEC Vanguard Drop-Beacon Resupply",
                briefing = "Incoming scout drop-ships need refined Iron Plates and Copper Wire to erect orbital telemetry beacons.",
                planetId = planetId,
                remainingSeconds = 90,
                totalSeconds = 90,
                options = listOf(
                    EventResponseOption(
                        id = "fulfill_starter",
                        label = "Launch Beacon Components",
                        detail = "Deliver 14 Iron Plates + 10 Copper Wire for +140 UEC Credits & +10 Readiness.",
                        resourceCost = mapOf(ResourceType.IRON_PLATE to 14, ResourceType.COPPER_WIRE to 10),
                        creditReward = 140,
                        colonizationBonus = 10,
                        outcomeSummary = "Vanguard drop-beacon telemetry array deployed in orbit."
                    ),
                    EventResponseOption(
                        id = "raw_shipment",
                        label = "Send Raw Ore Ballast",
                        detail = "Deliver 20 Iron Ore for +50 UEC Credits.",
                        resourceCost = mapOf(ResourceType.IRON_ORE to 20),
                        creditReward = 50,
                        colonizationBonus = 3,
                        outcomeSummary = "Uploaded raw Iron Ore consignment to orbital tender."
                    )
                )
            )
        }
    }

    private fun createCosmicEvent(planetId: String, seed: Int): ActiveDynamicEvent {
        val isResonance = seed % 2 == 0
        return if (isResonance) {
            ActiveDynamicEvent(
                instanceId = "evt_cosmic_$seed",
                category = DynamicEventCategory.COSMIC,
                title = "Magnetosphere Resonance Window",
                briefing = "Upper-atmosphere ion resonance boosts solar irradiance (+75%) and supercharges electric arc smelters.",
                planetId = planetId,
                remainingSeconds = 70,
                totalSeconds = 70,
                solarMultiplier = 1.75f,
                factorySpeedMultiplier = 1.35f,
                options = listOf(
                    EventResponseOption(
                        id = "harvest_surge",
                        label = "Overclock Arc Conduits",
                        detail = "Spend 8 Copper Wire to capture resonance surge (+120 Credits & +20 Silica).",
                        resourceCost = mapOf(ResourceType.COPPER_WIRE to 8),
                        creditReward = 120,
                        colonizationBonus = 8,
                        resourceReward = mapOf(ResourceType.SILICA to 20),
                        outcomeSummary = "Captured magnetosphere energy pulse for orbital credit & silica crystallization."
                    ),
                    EventResponseOption(
                        id = "passive_boost",
                        label = "Ride Passive Efficiency",
                        detail = "Enjoy +35% factory speed and +75% Solar power safely.",
                        outcomeSummary = "Maintained nominal tolerances during magnetosphere resonance."
                    )
                )
            )
        } else {
            ActiveDynamicEvent(
                instanceId = "evt_cosmic_$seed",
                category = DynamicEventCategory.COSMIC,
                title = "Coronal Mass Ejection Incoming",
                briefing = "Solar wind front destabilizes local power buses and strains exposed conveyor motors across the sector.",
                planetId = planetId,
                remainingSeconds = 60,
                totalSeconds = 60,
                solarMultiplier = 0.40f,
                factorySpeedMultiplier = 0.75f,
                options = listOf(
                    EventResponseOption(
                        id = "ground_grid",
                        label = "Deploy Faraday Grounding",
                        detail = "Spend 6 Iron Plates + 6 Copper Wire to restore 100% grid stability (+95 Credits).",
                        resourceCost = mapOf(ResourceType.IRON_PLATE to 6, ResourceType.COPPER_WIRE to 6),
                        creditReward = 95,
                        colonizationBonus = 7,
                        outcomeSummary = "Faraday grounding neutralized Coronal Mass Ejection interference."
                    ),
                    EventResponseOption(
                        id = "absorb_cme",
                        label = "Throttle Production Lines",
                        detail = "Avoid spending materials; operate at reduced throughput until CME passes.",
                        outcomeSummary = "Factory operated in brownout-safe mode during solar storm."
                    )
                )
            )
        }
    }
}
