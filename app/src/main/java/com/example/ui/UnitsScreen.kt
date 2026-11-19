package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BiomeCatalog
import com.example.data.model.TechTreeCatalog
import com.example.data.model.UnitArchetype
import com.example.data.model.UnitCatalog
import com.example.data.model.UnitFaction
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.PlasmaCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SlateVoid
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.SteelElevated
import com.example.ui.theme.SteelHull
import com.example.ui.theme.SteelPanel
import com.example.ui.theme.TelemetryEmerald
import com.example.viewmodel.VanguardUiState
import com.example.viewmodel.VanguardViewModel
import kotlin.math.roundToInt

@Composable
fun UnitsScreen(
    uiState: VanguardUiState,
    viewModel: VanguardViewModel,
    onJumpToGrid: () -> Unit
) {
    val planet = uiState.activePlanet
    val biome = remember(planet.planetId) { BiomeCatalog.byId(planet.planetId) }
    var selectedFaction by remember { mutableStateOf(UnitFaction.PLAYER) }

    val activePlayerUnits = remember(planet.units) { planet.units.filter { it.isPlayer } }
    val activeEnemyUnits = remember(planet.units) { planet.units.filter { !it.isPlayer } }
    val countByArchetype = remember(planet.units) { planet.units.groupingBy { it.archetypeId }.eachCount() }

    val roster = if (selectedFaction == UnitFaction.PLAYER) {
        UnitCatalog.playerUnits
    } else {
        UnitCatalog.enemyUnits
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateVoid)
            .testTag("units_roster_screen"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Tactical Garrison Overview Header
        item {
            Surface(
                color = SteelPanel,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TACTICAL UNIT ROSTER",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${biome.name} (${biome.designation}) // Live ECS Garrison",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = if (activeEnemyUnits.isNotEmpty()) HazardCrimson.copy(alpha = 0.2f)
                            else TelemetryEmerald.copy(alpha = 0.16f),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(
                                1.dp,
                                if (activeEnemyUnits.isNotEmpty()) HazardCrimson else TelemetryEmerald
                            )
                        ) {
                            Text(
                                text = if (activeEnemyUnits.isNotEmpty()) "COMBAT ALERT" else "SECTOR CLEAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeEnemyUnits.isNotEmpty()) HazardCrimson else TelemetryEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GarrisonMetricChip(
                            label = "UEC UNITS",
                            value = "${activePlayerUnits.size}",
                            color = PlasmaCyan,
                            modifier = Modifier.weight(1f)
                        )
                        GarrisonMetricChip(
                            label = "HOSTILES",
                            value = "${activeEnemyUnits.size}",
                            color = if (activeEnemyUnits.isNotEmpty()) HazardCrimson else TelemetryEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        GarrisonMetricChip(
                            label = "CREDITS",
                            value = "${uiState.profile.uecCredits} CR",
                            color = SafetyAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.rallyGarrison() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("rally_garrison_button"),
                            border = BorderStroke(1.dp, PlasmaCyan.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PlasmaCyan)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = "Rally UEC units to Orbital Hub",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Rally Hub", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = {
                                viewModel.spawnHostileWave(waveTier = 1)
                                onJumpToGrid()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("spawn_hostile_wave_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HazardCrimson,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "Launch hostile enemy wave",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Test Wave", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // 2. Player vs Enemy Roster Switcher
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FactionFilterButton(
                    title = "UEC Forces (${UnitCatalog.playerUnits.size})",
                    icon = Icons.Rounded.Shield,
                    selected = selectedFaction == UnitFaction.PLAYER,
                    accent = PlasmaCyan,
                    onClick = { selectedFaction = UnitFaction.PLAYER },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("roster_tab_player")
                )
                FactionFilterButton(
                    title = "Hostile Intel (${UnitCatalog.enemyUnits.size})",
                    icon = Icons.Rounded.BugReport,
                    selected = selectedFaction == UnitFaction.ENEMY,
                    accent = HazardCrimson,
                    onClick = { selectedFaction = UnitFaction.ENEMY },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("roster_tab_enemy")
                )
            }
        }

        // 3. Roster Unit Cards
        items(roster, key = { it.id }) { archetype ->
            val deployedCount = countByArchetype[archetype.id] ?: 0
            val unlocked = archetype.requiredTechId == null || archetype.requiredTechId in uiState.unlockedTechSet
            val canAfford = archetype.buildCost.all { (res, qty) -> (planet.inventory[res] ?: 0) >= qty }
            UnitRosterCard(
                archetype = archetype,
                deployedCount = deployedCount,
                unlocked = unlocked,
                canAfford = canAfford,
                inventory = planet.inventory,
                onDeployPlayer = {
                    viewModel.deployPlayerUnit(archetype.id)
                },
                onSpawnEnemy = {
                    viewModel.spawnHostileSkirmish(archetype.id)
                    onJumpToGrid()
                }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun GarrisonMetricChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SteelHull,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun FactionFilterButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) accent.copy(alpha = 0.2f) else SteelPanel,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.5.dp, if (selected) accent else SteelBorder),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnitRosterCard(
    archetype: UnitArchetype,
    deployedCount: Int,
    unlocked: Boolean,
    canAfford: Boolean,
    inventory: Map<com.example.data.model.ResourceType, Int>,
    onDeployPlayer: () -> Unit,
    onSpawnEnemy: () -> Unit
) {
    val isPlayer = archetype.faction == UnitFaction.PLAYER
    Surface(
        color = SteelPanel,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, archetype.accentColor.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unit_card_${archetype.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = archetype.accentColor.copy(alpha = 0.18f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.5.dp, archetype.accentColor),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = archetype.shortCode,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = archetype.accentColor
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = archetype.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = SteelElevated,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "T${archetype.tier} ${archetype.role.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = archetype.accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = archetype.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (deployedCount > 0) {
                    Surface(
                        color = archetype.accentColor.copy(alpha = 0.22f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "x$deployedCount LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = archetype.accentColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Combat & Mobility Telemetry Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatPill(Icons.Rounded.Favorite, "${archetype.maxHp} HP", TelemetryEmerald, Modifier.weight(1f))
                StatPill(Icons.Rounded.Bolt, "${archetype.dps.roundToInt()} DPS", SafetyAmber, Modifier.weight(1f))
                StatPill(Icons.Rounded.GpsFixed, "${archetype.attackRangeTiles} RNG", PlasmaCyan, Modifier.weight(1f))
                StatPill(Icons.Rounded.Speed, "${archetype.moveSpeedTilesPerSec} SPD", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }

            if (archetype.repairPerSecond > 0f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Build,
                        contentDescription = "Repair output",
                        tint = TelemetryEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Auto-Repair Beam: +${archetype.repairPerSecond.toInt()} HP/s to damaged structures & allies",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryEmerald
                    )
                }
            }

            // Tactical Counter / Role Guidance
            Surface(
                color = SteelHull,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = archetype.tacticalNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }

            if (isPlayer) {
                // Cost Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    archetype.buildCost.forEach { (res, qty) ->
                        val have = inventory[res] ?: 0
                        val ok = have >= qty
                        Surface(
                            color = if (ok) TelemetryEmerald.copy(alpha = 0.14f) else HazardCrimson.copy(alpha = 0.14f),
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, if (ok) TelemetryEmerald.copy(alpha = 0.5f) else HazardCrimson.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${res.code} $have/$qty",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ok) TelemetryEmerald else HazardCrimson,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Deploy Action Button
                val reqTechName = archetype.requiredTechId?.let { TechTreeCatalog.byId(it)?.title ?: it }
                Button(
                    onClick = onDeployPlayer,
                    enabled = unlocked && canAfford,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("deploy_unit_${archetype.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = archetype.accentColor,
                        contentColor = Color(0xFF0A1016)
                    )
                ) {
                    Icon(
                        imageVector = if (unlocked) Icons.Rounded.RocketLaunch else Icons.Rounded.Lock,
                        contentDescription = "Deploy ${archetype.name}",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            !unlocked -> "Requires Tech: $reqTechName"
                            !canAfford -> "Insufficient Sector Materials"
                            else -> "Fabricate & Deploy ${archetype.name}"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                // Enemy Bounty, Native Biomes & Skirmish Test Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bounty: +${archetype.creditBounty} UEC CR",
                            style = MaterialTheme.typography.labelMedium,
                            color = SafetyAmber
                        )
                        val biomeNames = archetype.nativeBiomes.joinToString(", ") {
                            BiomeCatalog.byId(it).name.substringBefore(" ")
                        }
                        Text(
                            text = "Native Sectors: $biomeNames",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onSpawnEnemy,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("spawn_enemy_${archetype.id}"),
                        border = BorderStroke(1.dp, archetype.accentColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = archetype.accentColor)
                    ) {
                        Text("Spawn Skirmish", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    icon: ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SteelHull,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1
            )
        }
    }
}
