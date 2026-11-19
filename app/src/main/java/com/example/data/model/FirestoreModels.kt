package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class CommanderProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val callsign: String = "Commander",
    val activePlanetId: String = "temperate",
    val uecCredits: Long = 180L,
    val colonizationScore: Long = 10L,
    val contractsCompleted: Long = 0L,
    val hazardsSurvived: Long = 0L,
    val unlockedTechIds: List<String> = listOf("t1_extraction"),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "callsign" to callsign.trim().ifEmpty { "Commander" }.take(40),
        "activePlanetId" to activePlanetId,
        "uecCredits" to uecCredits.coerceIn(0L, 1_000_000_000L),
        "colonizationScore" to colonizationScore.coerceIn(0L, 1_000_000_000L),
        "contractsCompleted" to contractsCompleted.coerceIn(0L, 1_000_000L),
        "hazardsSurvived" to hazardsSurvived.coerceIn(0L, 1_000_000L),
        "unlockedTechIds" to unlockedTechIds.distinct().take(32),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "callsign" to callsign.trim().ifEmpty { "Commander" }.take(40),
        "activePlanetId" to activePlanetId,
        "uecCredits" to uecCredits.coerceIn(0L, 1_000_000_000L),
        "colonizationScore" to colonizationScore.coerceIn(0L, 1_000_000_000L),
        "contractsCompleted" to contractsCompleted.coerceIn(0L, 1_000_000L),
        "hazardsSurvived" to hazardsSurvived.coerceIn(0L, 1_000_000L),
        "unlockedTechIds" to unlockedTechIds.distinct().take(32),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PlacedBuildingState(
    val x: Int = 0,
    val y: Int = 0,
    val type: BuildingType = BuildingType.CONVEYOR_MK1,
    val dir: Direction = Direction.EAST,
    val integrity: Int = 100,
    val recipeId: String = "NONE",
    val progressTicks: Int = 0,
    val statusNote: String = "ONLINE",
    val shieldedThermal: Boolean = false,
    val shieldedScrubber: Boolean = false,
    val protectedTurret: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "x" to x.toLong(),
        "y" to y.toLong(),
        "type" to type.name,
        "dir" to dir.name,
        "integrity" to integrity.coerceIn(1, type.maxIntegrity).toLong(),
        "recipe" to recipeId.ifBlank { "NONE" }
    )

    companion object {
        fun fromFirestoreMap(raw: Map<*, *>): PlacedBuildingState? {
            val x = (raw["x"] as? Number)?.toInt() ?: return null
            val y = (raw["y"] as? Number)?.toInt() ?: return null
            val type = BuildingType.safeValueOf(raw["type"] as? String)
            val dir = Direction.safeValueOf(raw["dir"] as? String)
            val integrity = (raw["integrity"] as? Number)?.toInt()?.coerceIn(1, type.maxIntegrity)
                ?: type.maxIntegrity
            val recipe = (raw["recipe"] as? String)?.ifBlank { type.defaultRecipeId }
                ?: type.defaultRecipeId
            return PlacedBuildingState(
                x = x,
                y = y,
                type = type,
                dir = dir,
                integrity = integrity,
                recipeId = recipe
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class PlanetColonyDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val planetId: String = "temperate",
    val biomeName: String = "Aethelgard Basin",
    val colonizationPercent: Long = 0L,
    val powerGeneratedMw: Long = 0L,
    val powerConsumedMw: Long = 0L,
    val buildings: List<Map<String, Any>> = emptyList(),
    val inventory: Map<String, Long> = emptyMap(),
    val activeHazardId: String = "none",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "planetId" to planetId,
        "biomeName" to biomeName.trim().ifEmpty { "Unknown Sector" }.take(60),
        "colonizationPercent" to colonizationPercent.coerceIn(0L, 100L),
        "powerGeneratedMw" to powerGeneratedMw.coerceIn(0L, 1_000_000L),
        "powerConsumedMw" to powerConsumedMw.coerceIn(0L, 1_000_000L),
        "buildings" to buildings.take(200),
        "inventory" to inventory.entries.take(40).associate { it.key to it.value.coerceAtLeast(0L) },
        "activeHazardId" to activeHazardId.ifBlank { "none" }.take(40),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "biomeName" to biomeName.trim().ifEmpty { "Unknown Sector" }.take(60),
        "colonizationPercent" to colonizationPercent.coerceIn(0L, 100L),
        "powerGeneratedMw" to powerGeneratedMw.coerceIn(0L, 1_000_000L),
        "powerConsumedMw" to powerConsumedMw.coerceIn(0L, 1_000_000L),
        "buildings" to buildings.take(200),
        "inventory" to inventory.entries.take(40).associate { it.key to it.value.coerceAtLeast(0L) },
        "activeHazardId" to activeHazardId.ifBlank { "none" }.take(40),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class MissionEventLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val planetId: String = "temperate",
    val eventType: String = "supply_contract",
    val title: String = "",
    val outcome: String = "",
    val creditsDelta: Long = 0L,
    val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "planetId" to planetId,
        "eventType" to eventType,
        "title" to title.trim().ifEmpty { "Sector Incident" }.take(80),
        "outcome" to outcome.trim().ifEmpty { "Resolved." }.take(160),
        "creditsDelta" to creditsDelta.coerceIn(-1_000_000L, 1_000_000L),
        "createdAt" to FieldValue.serverTimestamp()
    )
}
