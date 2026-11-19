package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class UnitFaction {
    PLAYER,
    ENEMY
}

enum class UnitRole(val label: String) {
    RECON("RECON"),
    SUPPORT("REPAIR"),
    INFANTRY("ASSAULT"),
    HEAVY("SIEGE"),
    AERIAL("GUNSHIP"),
    SWARM("SWARM"),
    RANGED("ARTILLERY"),
    BOSS("APEX BOSS")
}

data class UnitArchetype(
    val id: String,
    val name: String,
    val shortCode: String,
    val faction: UnitFaction,
    val role: UnitRole,
    val tier: Int,
    val maxHp: Int,
    val attackDamage: Int,
    val attackRangeTiles: Float,
    val attackCooldownSec: Float,
    val moveSpeedTilesPerSec: Float,
    val repairPerSecond: Float = 0f,
    val buildCost: Map<ResourceType, Int> = emptyMap(),
    val requiredTechId: String? = null,
    val creditBounty: Long = 0L,
    val nativeBiomes: List<String> = emptyList(),
    val accentColor: Color,
    val description: String,
    val tacticalNote: String
) {
    val dps: Float
        get() = if (attackCooldownSec > 0f) attackDamage / attackCooldownSec else 0f
}

object UnitCatalog {
    // ========================================================================
    // Player (UEC Colonial Defense Corps) Unit Roster
    // ========================================================================
    val playerUnits: List<UnitArchetype> = listOf(
        UnitArchetype(
            id = "uec_scout_drone",
            name = "Aegis Recon Drone",
            shortCode = "DRN",
            faction = UnitFaction.PLAYER,
            role = UnitRole.RECON,
            tier = 1,
            maxHp = 85,
            attackDamage = 11,
            attackRangeTiles = 3.2f,
            attackCooldownSec = 0.65f,
            moveSpeedTilesPerSec = 2.4f,
            buildCost = mapOf(
                ResourceType.IRON_PLATE to 12,
                ResourceType.COPPER_WIRE to 8
            ),
            accentColor = Color(0xFF38BDF8),
            description = "High-speed hovering perimeter drone armed with a rapid micro-pulse emitter.",
            tacticalNote = "Excels at intercepting Xeno Skitterer packs before they chew through outer conveyor belts."
        ),
        UnitArchetype(
            id = "uec_repair_mech",
            name = "Valkyrie Repair Mech",
            shortCode = "REP",
            faction = UnitFaction.PLAYER,
            role = UnitRole.SUPPORT,
            tier = 1,
            maxHp = 130,
            attackDamage = 7,
            attackRangeTiles = 2.8f,
            attackCooldownSec = 0.9f,
            moveSpeedTilesPerSec = 1.65f,
            repairPerSecond = 14f,
            buildCost = mapOf(
                ResourceType.IRON_PLATE to 14,
                ResourceType.COPPER_WIRE to 10,
                ResourceType.STEEL_BEAM to 4
            ),
            accentColor = Color(0xFF10B981),
            description = "Autonomous field-engineering walker equipped with a continuous nano-weld repair beam.",
            tacticalNote = "Automatically seeks out damaged infrastructure and wounded UEC units during hazards and raids."
        ),
        UnitArchetype(
            id = "uec_heavy_trooper",
            name = "Bastion Exosuit",
            shortCode = "EXO",
            faction = UnitFaction.PLAYER,
            role = UnitRole.INFANTRY,
            tier = 2,
            maxHp = 220,
            attackDamage = 24,
            attackRangeTiles = 3.8f,
            attackCooldownSec = 0.85f,
            moveSpeedTilesPerSec = 1.45f,
            buildCost = mapOf(
                ResourceType.STEEL_BEAM to 10,
                ResourceType.CIRCUIT_BOARD to 4
            ),
            requiredTechId = "t2_hazards",
            accentColor = Color(0xFF60A5FA),
            description = "Armored UEC marine exosuit wielding a stabilized heavy plasma carbine.",
            tacticalNote = "Reliable frontline anchor capable of holding chokepoints against Spore Spitters and Stalkers."
        ),
        UnitArchetype(
            id = "uec_siege_walker",
            name = "Vanguard Siege Walker",
            shortCode = "SGW",
            faction = UnitFaction.PLAYER,
            role = UnitRole.HEAVY,
            tier = 3,
            maxHp = 420,
            attackDamage = 52,
            attackRangeTiles = 5.0f,
            attackCooldownSec = 1.35f,
            moveSpeedTilesPerSec = 1.05f,
            buildCost = mapOf(
                ResourceType.THERMAL_ALLOY to 6,
                ResourceType.REINFORCED_PLATE to 8,
                ResourceType.CIRCUIT_BOARD to 6
            ),
            requiredTechId = "t3_defense",
            accentColor = Color(0xFFF59E0B),
            description = "Bipedal heavy artillery walker firing hyper-velocity armor-piercing rail slugs.",
            tacticalNote = "Outranges Spore Spitters and shreds armored Magma Ravagers and Leviathans."
        ),
        UnitArchetype(
            id = "uec_gunship",
            name = "Hyperion Gunship",
            shortCode = "GNS",
            faction = UnitFaction.PLAYER,
            role = UnitRole.AERIAL,
            tier = 4,
            maxHp = 540,
            attackDamage = 68,
            attackRangeTiles = 5.5f,
            attackCooldownSec = 0.95f,
            moveSpeedTilesPerSec = 2.25f,
            buildCost = mapOf(
                ResourceType.HEAVY_FRAME to 6,
                ResourceType.QUANTUM_MATRIX to 3,
                ResourceType.FUEL_CELL to 4
            ),
            requiredTechId = "t4_nuclear",
            accentColor = Color(0xFFA855F7),
            description = "Orbital-drop VTOL strike craft with twin plasma lances and reactive shielding.",
            tacticalNote = "Rapid-response apex defender capable of patrolling the entire 14x14 sector in seconds."
        )
    )

    // ========================================================================
    // Enemy (Hostile Planetary Fauna & Apex Threats) Unit Roster
    // ========================================================================
    val enemyUnits: List<UnitArchetype> = listOf(
        UnitArchetype(
            id = "enemy_skitterer",
            name = "Xeno Skitterer",
            shortCode = "SKT",
            faction = UnitFaction.ENEMY,
            role = UnitRole.SWARM,
            tier = 1,
            maxHp = 55,
            attackDamage = 8,
            attackRangeTiles = 1.2f,
            attackCooldownSec = 0.75f,
            moveSpeedTilesPerSec = 2.05f,
            creditBounty = 15L,
            nativeBiomes = listOf("temperate", "arid"),
            accentColor = Color(0xFFEF4444),
            description = "Agile chitinous pack hunter attracted by the seismic vibration of kinetic drills.",
            tacticalNote = "Counter with Aegis Recon Drones or perimeter Rail-Turrets before packs overwhelm belts."
        ),
        UnitArchetype(
            id = "enemy_spitter",
            name = "Acid Spore Spitter",
            shortCode = "SPT",
            faction = UnitFaction.ENEMY,
            role = UnitRole.RANGED,
            tier = 2,
            maxHp = 95,
            attackDamage = 16,
            attackRangeTiles = 3.8f,
            attackCooldownSec = 1.25f,
            moveSpeedTilesPerSec = 1.25f,
            creditBounty = 25L,
            nativeBiomes = listOf("temperate", "volcanic"),
            accentColor = Color(0xFF84CC16),
            description = "Bioluminescent stalker that launches pressurized corrosive globules from standoff range.",
            tacticalNote = "Pair Valkyrie Repair Mechs with Bastion Exosuits to neutralize acid siege wear."
        ),
        UnitArchetype(
            id = "enemy_stalker",
            name = "Cryo Phase Stalker",
            shortCode = "STK",
            faction = UnitFaction.ENEMY,
            role = UnitRole.RECON,
            tier = 2,
            maxHp = 135,
            attackDamage = 22,
            attackRangeTiles = 1.6f,
            attackCooldownSec = 0.80f,
            moveSpeedTilesPerSec = 2.20f,
            creditBounty = 35L,
            nativeBiomes = listOf("cryo", "arid"),
            accentColor = Color(0xFF06B6D4),
            description = "Frost-adapted ambush predator that flanks power generators at blistering speed.",
            tacticalNote = "Maintain overlapping turret coverage and keep mobile drones guarding your power grid."
        ),
        UnitArchetype(
            id = "enemy_ravager",
            name = "Magma Ravager",
            shortCode = "RVG",
            faction = UnitFaction.ENEMY,
            role = UnitRole.HEAVY,
            tier = 3,
            maxHp = 280,
            attackDamage = 34,
            attackRangeTiles = 1.5f,
            attackCooldownSec = 1.15f,
            moveSpeedTilesPerSec = 1.10f,
            creditBounty = 55L,
            nativeBiomes = listOf("volcanic", "arid"),
            accentColor = Color(0xFFF97316),
            description = "Obsidian-plated bruiser that rams industrial walls and smelters with molten horns.",
            tacticalNote = "Focus fire with Vanguard Siege Walkers before it reaches core refineries."
        ),
        UnitArchetype(
            id = "enemy_leviathan",
            name = "Apex Leviathan",
            shortCode = "LEV",
            faction = UnitFaction.ENEMY,
            role = UnitRole.BOSS,
            tier = 4,
            maxHp = 640,
            attackDamage = 58,
            attackRangeTiles = 3.2f,
            attackCooldownSec = 1.45f,
            moveSpeedTilesPerSec = 0.78f,
            creditBounty = 140L,
            nativeBiomes = listOf("volcanic", "cryo", "arid", "temperate"),
            accentColor = Color(0xFFEC4899),
            description = "Colossal planetary apex juggernaut radiating seismic shockwaves across the sector.",
            tacticalNote = "Deploy Siege Walkers, Hyperion Gunships, and Repair Mechs to survive its assault."
        )
    )

    val allUnits: List<UnitArchetype> = playerUnits + enemyUnits

    fun byId(id: String): UnitArchetype? = allUnits.find { it.id == id }

    fun waveForBiome(planetId: String, waveTier: Int = 1): List<UnitArchetype> {
        val skitterer = byId("enemy_skitterer")!!
        val spitter = byId("enemy_spitter")!!
        val stalker = byId("enemy_stalker")!!
        val ravager = byId("enemy_ravager")!!
        val leviathan = byId("enemy_leviathan")!!

        return when (planetId) {
            "volcanic" -> if (waveTier >= 3) listOf(ravager, spitter, leviathan) else listOf(ravager, spitter, skitterer)
            "cryo" -> if (waveTier >= 3) listOf(stalker, stalker, leviathan) else listOf(stalker, stalker, spitter)
            "arid" -> if (waveTier >= 3) listOf(ravager, stalker, leviathan) else listOf(skitterer, stalker, ravager)
            else -> if (waveTier >= 3) listOf(skitterer, spitter, ravager, leviathan) else listOf(skitterer, skitterer, spitter)
        }
    }
}
