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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BuildCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ActiveDynamicEvent
import com.example.data.model.BiomeCatalog
import com.example.data.model.BuildingType
import com.example.data.model.MissionEventLog
import com.example.data.model.RecipeCatalog
import com.example.data.model.ResourceCategory
import com.example.data.model.ResourceType
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.PlasmaCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SlateVoid
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.SteelHull
import com.example.ui.theme.SteelPanel
import com.example.ui.theme.TelemetryEmerald
import com.example.ui.theme.WarningOrange
import com.example.viewmodel.SyncStatus
import com.example.viewmodel.VanguardUiState
import com.example.viewmodel.VanguardViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun OperationsScreen(
    uiState: VanguardUiState,
    viewModel: VanguardViewModel,
    onSignOutClick: () -> Unit
) {
    val planet = uiState.activePlanet
    val unlocked = uiState.unlockedTechSet
    val unlockedRecipes = remember(unlocked) {
        RecipeCatalog.allRecipes.filter { it.requiredTechId == null || it.requiredTechId in unlocked }
    }
    val exportableStock = remember(planet.inventory) {
        ResourceType.entries.filter {
            (planet.inventory[it] ?: 0) >= 5 && it.category != ResourceCategory.RAW
        }
    }
    val hasTurret = remember(planet.buildings) {
        planet.buildings.values.any { it.type == BuildingType.AUTO_TURRET }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateVoid),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Commander Cloud Sync & Profile Card
        item {
            CommanderCloudCard(
                uiState = uiState,
                onSyncNow = { viewModel.syncNow(silent = false) },
                onSignOut = onSignOutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            )
        }

        // 2. Dynamic Event Tactical Command Console
        item {
            DynamicEventCommandSection(
                activeEvent = uiState.activeDynamicEvent,
                inventory = planet.inventory,
                uecCredits = uiState.profile.uecCredits,
                hasTurretOnline = hasTurret,
                onScanAnomaly = { viewModel.triggerAnomalyScan() },
                onRespond = { viewModel.respondToDynamicEvent(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            )
        }

        // 3. UEC Orbital Supply Shipments (Convert surplus goods into UEC Credits & Colonization Score)
        item {
            SectionTitle(
                icon = Icons.Rounded.RocketLaunch,
                title = "UEC ORBITAL SUPPLY LAUNCHPAD",
                tint = SafetyAmber,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            )
        }

        if (exportableStock.isEmpty()) {
            item {
                Surface(
                    color = SteelHull,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, SteelBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                ) {
                    Text(
                        text = "Produce at least 5 refined plates, beams, munitions, or med-pods to authorize an Orbital Supply Launch for UEC Credits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(exportableStock, key = { "ship_${it.name}" }) { res ->
                val count = planet.inventory[res] ?: 0
                val batchSize = minOf(10, count)
                val payout = batchSize * res.creditValue
                Surface(
                    color = SteelHull,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, SteelBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(res.color, MaterialTheme.shapes.extraSmall)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${res.displayName} ($count held)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Launch $batchSize units -> +$payout UEC Credits",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafetyAmber
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.launchOrbitalShipment(res, batchSize) },
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafetyAmber,
                                contentColor = Color(0xFF0E141B)
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("ship_btn_${res.name.lowercase()}")
                        ) {
                            Text("LAUNCH", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. Manual Field Workbench Crafting (Satisfactory/Factorio Pocket Crafting)
        item {
            SectionTitle(
                icon = Icons.Rounded.BuildCircle,
                title = "UEC FIELD WORKBENCH (MANUAL CRAFT)",
                tint = PlasmaCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            )
        }

        items(unlockedRecipes, key = { "craft_${it.id}" }) { recipe ->
            val canCraft = recipe.inputs.all { (res, qty) -> (planet.inventory[res] ?: 0) >= qty }
            Surface(
                color = SteelHull,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, if (canCraft) PlasmaCyan.copy(alpha = 0.5f) else SteelBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${recipe.output.displayName} (+${recipe.outputAmount})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "COST: " + recipe.inputs.entries.joinToString(" + ") { (r, q) ->
                                "${r.code} ${planet.inventory[r] ?: 0}/$q"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (canCraft) TelemetryEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.manualCraft(recipe.id) },
                        enabled = canCraft,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("craft_btn_${recipe.id}")
                    ) {
                        Text("CRAFT", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 5. Cloud-Synced Mission Event Log
        item {
            SectionTitle(
                icon = Icons.Rounded.History,
                title = "CLOUD-SYNCED MISSION LOGS",
                tint = TelemetryEmerald,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            )
        }

        if (uiState.eventLogs.isEmpty()) {
            item {
                Surface(
                    color = SteelHull,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, SteelBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                ) {
                    Text(
                        text = stringResource(R.string.empty_event_logs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(uiState.eventLogs, key = { it.id.ifBlank { "${it.title}_${it.createdAt}" } }) { log ->
                EventLogCard(
                    log = log,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                )
            }
        }
    }
}

@Composable
private fun CommanderCloudCard(
    uiState: VanguardUiState,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = uiState.profile
    Surface(
        color = SteelHull,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, SteelBorder),
        modifier = modifier
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
                        text = "CMDR. ${profile.callsign.uppercase()}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "COLONIZATION SCORE: ${profile.colonizationScore}  •  CONTRACTS: ${profile.contractsCompleted}  •  HAZARDS: ${profile.hazardsSurvived}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PlasmaCyan
                    )
                }
                Icon(
                    imageVector = if (uiState.syncStatus == SyncStatus.SYNCED) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                    contentDescription = "Cloud sync state",
                    tint = if (uiState.syncStatus == SyncStatus.SYNCED) TelemetryEmerald else SafetyAmber,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = "SYNC NOTICE: ${uiState.errorMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningOrange
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSyncNow,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PlasmaCyan,
                        contentColor = Color(0xFF061A22)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sync_now_button")
                ) {
                    Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.sync_now).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSignOut,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.sign_out).uppercase(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DynamicEventCommandSection(
    activeEvent: ActiveDynamicEvent?,
    inventory: Map<ResourceType, Int>,
    uecCredits: Long,
    hasTurretOnline: Boolean,
    onScanAnomaly: () -> Unit,
    onRespond: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SteelHull,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            if (activeEvent != null) 1.5.dp else 1.dp,
            if (activeEvent != null) HazardCrimson else SteelBorder
        ),
        modifier = modifier
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (activeEvent != null) Icons.Rounded.WarningAmber else Icons.Rounded.Radar,
                        contentDescription = null,
                        tint = if (activeEvent != null) HazardCrimson else PlasmaCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (activeEvent != null) "${activeEvent.category.label} // ${activeEvent.remainingSeconds}s"
                        else "DYNAMIC EVENT & ANOMALY SCANNER",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (activeEvent != null) HazardCrimson else PlasmaCyan
                    )
                }
            }

            if (activeEvent == null) {
                Text(
                    text = stringResource(R.string.no_active_events),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onScanAnomaly,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyAmber,
                        contentColor = Color(0xFF0E141B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("scan_anomaly_button")
                ) {
                    Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.trigger_event_scan).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = activeEvent.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = activeEvent.briefing,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                activeEvent.options.forEach { opt ->
                    val canAffordCredits = uecCredits >= opt.creditCost
                    val canAffordRes = opt.resourceCost.all { (r, q) -> (inventory[r] ?: 0) >= q }
                    val turretOk = !opt.requiresTurretOnline || hasTurretOnline
                    val enabled = canAffordCredits && canAffordRes && turretOk

                    Surface(
                        color = SteelPanel,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, if (enabled) SafetyAmber.copy(alpha = 0.65f) else SteelBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = opt.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (enabled) SafetyAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = opt.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { onRespond(opt.id) },
                                enabled = enabled,
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyAmber,
                                    contentColor = Color(0xFF0E141B),
                                    disabledContainerColor = SteelHull
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("event_opt_${opt.id}")
                            ) {
                                Text(
                                    text = when {
                                        !turretOk -> "REQUIRES RAIL-TURRET ON GRID"
                                        !canAffordCredits -> "NEED ${opt.creditCost} CREDITS"
                                        !canAffordRes -> "INSUFFICIENT SECTOR RESOURCES"
                                        else -> "EXECUTE RESPONSE"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventLogCard(log: MissionEventLog, modifier: Modifier = Modifier) {
    val planetName = BiomeCatalog.byId(log.planetId).name
    val timeText = remember(log.createdAt) {
        val ts = log.createdAt ?: Timestamp.now()
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(ts.toDate())
    }
    Surface(
        color = SteelHull,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, SteelBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${planetName.uppercase()} // ${log.eventType.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PlasmaCyan
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = log.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = log.outcome,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (log.creditsDelta != 0L) {
                val sign = if (log.creditsDelta > 0) "+" else ""
                Text(
                    text = "${sign}${log.creditsDelta} UEC CREDITS",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (log.creditsDelta > 0) TelemetryEmerald else WarningOrange
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}
