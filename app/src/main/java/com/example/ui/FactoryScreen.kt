package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storm
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BiomeCatalog
import com.example.data.model.BuildingCategory
import com.example.data.model.BuildingType
import com.example.data.model.PlacedBuildingState
import com.example.data.model.RecipeCatalog
import com.example.data.model.ResourceType
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.PlasmaCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SlateVoid
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.SteelElevated
import com.example.ui.theme.SteelHull
import com.example.ui.theme.SteelPanel
import com.example.ui.theme.TelemetryEmerald
import com.example.ui.theme.WarningOrange
import com.example.viewmodel.SyncStatus
import com.example.viewmodel.VanguardUiState
import com.example.viewmodel.VanguardViewModel

@Composable
fun FactoryScreen(
    uiState: VanguardUiState,
    viewModel: VanguardViewModel,
    onOpenEventsTab: () -> Unit,
    onOpenTechTab: () -> Unit
) {
    val planet = uiState.activePlanet
    val biome = remember(planet.planetId) { BiomeCatalog.byId(planet.planetId) }
    var zoomLevel by remember { mutableFloatStateOf(1.1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateVoid)
    ) {
        // 1. Top Industrial Telemetry Strip
        TelemetryTopBar(
            biomeName = biome.name,
            designation = biome.designation,
            powerGen = planet.powerGeneratedMw,
            powerUse = planet.powerConsumedMw,
            itemsPerMin = planet.itemsPerMinute,
            uecCredits = uiState.profile.uecCredits,
            colonizationPct = planet.colonizationPercent,
            syncStatus = uiState.syncStatus,
            onSyncClick = { viewModel.syncNow(silent = false) }
        )

        // 2. Resource Ticker Strip (Primary materials at a glance)
        ResourceTickerBar(inventory = planet.inventory)

        // 3. Active Hazard / Dynamic Event Alert Banner
        AnimatedVisibility(visible = planet.activeHazard != null || uiState.activeDynamicEvent != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                planet.activeHazard?.let { hazard ->
                    Surface(
                        color = WarningOrange.copy(alpha = 0.18f),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storm,
                                contentDescription = "Active environmental hazard",
                                tint = WarningOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "HAZARD: ${hazard.name.uppercase()} (${planet.hazardTicksRemaining / 5}s)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = WarningOrange
                                )
                                Text(
                                    text = hazard.counterStrategy,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                uiState.activeDynamicEvent?.let { evt ->
                    Surface(
                        color = HazardCrimson.copy(alpha = 0.20f),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, HazardCrimson),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenEventsTab() }
                            .testTag("active_event_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "Dynamic world event",
                                tint = HazardCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${evt.category.label} // ${evt.remainingSeconds}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HazardCrimson
                                )
                                Text(
                                    text = evt.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "RESPOND →",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafetyAmber
                            )
                        }
                    }
                }
            }
        }

        // 4. Main Interactive Factory Grid Viewport + Floating Tool Controls
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            FactoryCanvas(
                planetState = planet,
                inspectedTile = uiState.inspectedTile,
                selectedBuildTool = uiState.selectedBuildTool,
                demolishMode = uiState.demolishMode,
                zoomLevel = zoomLevel,
                panOffset = panOffset,
                onPanZoomChange = { newPan, newZoom ->
                    panOffset = newPan
                    zoomLevel = newZoom
                },
                onTileTapped = { x, y -> viewModel.onTileTapped(x, y) }
            )

            // Top-Left ECS & Fixed-Timestep Game Loop Telemetry & Playback Pill
            val loopStats = uiState.loopStats
            Surface(
                color = SteelHull.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .testTag("ecs_loop_hud")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.toggleSimulationPause() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("sim_pause_toggle")
                    ) {
                        Icon(
                            imageVector = if (loopStats.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (loopStats.paused) "Resume ECS game loop" else "Pause ECS game loop",
                            tint = if (loopStats.paused) SafetyAmber else TelemetryEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Surface(
                        color = SteelElevated,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier
                            .clickable { viewModel.cycleSimulationSpeed() }
                            .testTag("sim_speed_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FastForward,
                                contentDescription = "Cycle simulation speed",
                                tint = PlasmaCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "${loopStats.timeScale.toInt()}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = PlasmaCyan
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "ECS ${loopStats.entityCount} ENT • ${loopStats.fixedHz}Hz",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (loopStats.paused) "PAUSED • T#${loopStats.totalTicks}" else "${loopStats.systemCount} SYS • T#${loopStats.totalTicks}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Right-side Floating Tactile Controls (Rotate, Demolish, Repair, Hazard Test, Zoom)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    color = SteelHull.copy(alpha = 0.92f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, SteelBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FilledTonalIconButton(
                            onClick = { viewModel.rotateBuildDirection() },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = SteelElevated,
                                contentColor = SafetyAmber
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("rotate_direction_button")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.RotateRight,
                                    contentDescription = "Rotate placement direction",
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = uiState.buildDirection.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafetyAmber
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = { viewModel.toggleDemolishMode() },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (uiState.demolishMode) HazardCrimson else SteelElevated,
                                contentColor = if (uiState.demolishMode) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("demolish_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Toggle deconstruct mode",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { viewModel.repairAllBuildings() },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = SteelElevated,
                                contentColor = TelemetryEmerald
                            ),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("repair_grid_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Build,
                                contentDescription = "Repair all damaged structures",
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { viewModel.triggerHazardPulse() },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = SteelElevated,
                                contentColor = WarningOrange
                            ),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("trigger_hazard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storm,
                                contentDescription = "Trigger environmental hazard pulse",
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Zoom Controls
                Surface(
                    color = SteelHull.copy(alpha = 0.90f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, SteelBorder)
                ) {
                    Column(modifier = Modifier.padding(2.dp)) {
                        IconButton(
                            onClick = { zoomLevel = (zoomLevel + 0.25f).coerceAtMost(2.3f) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in", tint = PlasmaCyan)
                        }
                        IconButton(
                            onClick = { zoomLevel = (zoomLevel - 0.25f).coerceAtLeast(0.75f) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out", tint = PlasmaCyan)
                        }
                        IconButton(
                            onClick = {
                                zoomLevel = 1.1f
                                panOffset = Offset.Zero
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Recenter grid", tint = SafetyAmber)
                        }
                    }
                }
            }

            // Selected Building Inspector Overlay (Bottom of Canvas)
            val inspectedPos = uiState.inspectedTile
            val inspectedBuilding = inspectedPos?.let { planet.buildings[it] }
            if (inspectedPos != null && inspectedBuilding != null) {
                BuildingInspectorCard(
                    building = inspectedBuilding,
                    unlockedTechs = uiState.unlockedTechSet,
                    onRotate = { viewModel.rotateBuildingAt(inspectedPos.first, inspectedPos.second) },
                    onDeconstruct = { viewModel.deconstructAt(inspectedPos.first, inspectedPos.second) },
                    onSelectRecipe = { recipeId ->
                        viewModel.setBuildingRecipe(inspectedPos.first, inspectedPos.second, recipeId)
                    },
                    onClose = { viewModel.clearInspectedTile() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp)
                )
            }
        }

        // 5. Bottom Categorized Build Dock
        BuildDockPanel(
            selectedCategory = uiState.selectedCategory,
            selectedTool = uiState.selectedBuildTool,
            demolishMode = uiState.demolishMode,
            unlockedTechs = uiState.unlockedTechSet,
            inventory = planet.inventory,
            onSelectCategory = { viewModel.selectBuildCategory(it) },
            onSelectTool = { viewModel.selectBuildTool(it) },
            onLockedToolClick = { onOpenTechTab() }
        )
    }
}

@Composable
private fun TelemetryTopBar(
    biomeName: String,
    designation: String,
    powerGen: Int,
    powerUse: Int,
    itemsPerMin: Int,
    uecCredits: Long,
    colonizationPct: Int,
    syncStatus: SyncStatus,
    onSyncClick: () -> Unit
) {
    val powerHealthy = powerGen >= powerUse
    Surface(
        color = SteelHull,
        border = BorderStroke(1.dp, SteelBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$designation // $biomeName",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = "Power grid",
                            tint = if (powerHealthy) TelemetryEmerald else HazardCrimson,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${powerUse}/${powerGen}MW",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (powerHealthy) TelemetryEmerald else HazardCrimson
                        )
                    }
                    Text(
                        text = "$itemsPerMin/m",
                        style = MaterialTheme.typography.labelSmall,
                        color = PlasmaCyan
                    )
                    Text(
                        text = "COL ${colonizationPct}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafetyAmber
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // UEC Credits Pill
                Surface(
                    color = SafetyAmber.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "$uecCredits CR",
                        style = MaterialTheme.typography.labelMedium,
                        color = SafetyAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Cloud Sync Chip
                val (syncIcon, syncColor) = when (syncStatus) {
                    SyncStatus.SYNCED -> Icons.Rounded.CloudDone to TelemetryEmerald
                    SyncStatus.SYNCING -> Icons.Rounded.CloudSync to PlasmaCyan
                    SyncStatus.ERROR -> Icons.Rounded.CloudOff to WarningOrange
                }
                Surface(
                    color = syncColor.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, syncColor.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clickable { onSyncClick() }
                        .testTag("cloud_sync_chip")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = syncIcon,
                            contentDescription = "Cloud sync status",
                            tint = syncColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceTickerBar(inventory: Map<ResourceType, Int>) {
    val displayed = remember(inventory) {
        listOf(
            ResourceType.IRON_PLATE,
            ResourceType.COPPER_WIRE,
            ResourceType.STEEL_BEAM,
            ResourceType.CIRCUIT_BOARD,
            ResourceType.REINFORCED_PLATE,
            ResourceType.IRON_ORE,
            ResourceType.COPPER_ORE,
            ResourceType.COAL,
            ResourceType.SILICA,
            ResourceType.TITANIUM_ORE,
            ResourceType.CRYO_GEL,
            ResourceType.URANIUM_ORE,
            ResourceType.THERMAL_ALLOY,
            ResourceType.MUNITIONS_CRATE,
            ResourceType.COLONY_MED_POD
        ).filter { (inventory[it] ?: 0) > 0 || it in setOf(ResourceType.IRON_PLATE, ResourceType.COPPER_WIRE, ResourceType.STEEL_BEAM, ResourceType.COAL) }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(SteelPanel.copy(alpha = 0.72f))
            .padding(vertical = 5.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(displayed, key = { it.name }) { res ->
            val count = inventory[res] ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(SteelHull, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(res.color, MaterialTheme.shapes.extraSmall)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "${res.code} $count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun BuildingInspectorCard(
    building: PlacedBuildingState,
    unlockedTechs: Set<String>,
    onRotate: () -> Unit,
    onDeconstruct: () -> Unit,
    onSelectRecipe: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableRecipes = remember(building.type, unlockedTechs) {
        RecipeCatalog.forMachine(building.type, unlockedTechs)
    }

    Surface(
        color = SteelHull.copy(alpha = 0.96f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, SafetyAmber),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = building.type.displayName.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = SafetyAmber
                    )
                    Text(
                        text = "STATUS: ${building.statusNote}  •  HULL ${building.integrity}/${building.type.maxIntegrity}  •  DIR ${building.dir.shortLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close inspector", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Protection badges
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (building.shieldedThermal) ShieldBadge("THERMAL SHIELD", PlasmaCyan)
                if (building.shieldedScrubber) ShieldBadge("SCRUBBED", TelemetryEmerald)
                if (building.protectedTurret) ShieldBadge("TURRET COVER", HazardCrimson)
            }

            // Recipe selector for Smelters/Foundries/Assemblers/Manufacturers
            if (availableRecipes.isNotEmpty()) {
                Text(
                    text = "ACTIVE FABRICATION RECIPE:",
                    style = MaterialTheme.typography.labelSmall,
                    color = PlasmaCyan
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(availableRecipes, key = { it.id }) { recipe ->
                        val active = building.recipeId == recipe.id
                        Surface(
                            color = if (active) SafetyAmber.copy(alpha = 0.22f) else SteelPanel,
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, if (active) SafetyAmber else SteelBorder),
                            modifier = Modifier.clickable { onSelectRecipe(recipe.id) }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = recipe.output.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) SafetyAmber else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = recipe.inputs.entries.joinToString(" + ") { "${it.value} ${it.key.code}" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRotate,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate building", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ROTATE", style = MaterialTheme.typography.labelMedium)
                }
                if (building.type != BuildingType.ORBITAL_HUB) {
                    Button(
                        onClick = onDeconstruct,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = HazardCrimson),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Deconstruct building", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("REFUND 100%", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShieldBadge(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun BuildDockPanel(
    selectedCategory: BuildingCategory,
    selectedTool: BuildingType?,
    demolishMode: Boolean,
    unlockedTechs: Set<String>,
    inventory: Map<ResourceType, Int>,
    onSelectCategory: (BuildingCategory) -> Unit,
    onSelectTool: (BuildingType) -> Unit,
    onLockedToolClick: () -> Unit
) {
    val buildingsInCat = remember(selectedCategory) {
        BuildingType.entries.filter { it.category == selectedCategory && it != BuildingType.ORBITAL_HUB }
    }

    Surface(
        color = SteelHull,
        border = BorderStroke(1.dp, SteelBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
            // Category tabs
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(BuildingCategory.entries) { cat ->
                    val isSelected = cat == selectedCategory
                    Surface(
                        color = if (isSelected) SafetyAmber else SteelPanel,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier
                            .clickable { onSelectCategory(cat) }
                            .testTag("build_cat_${cat.name.lowercase()}")
                    ) {
                        Text(
                            text = cat.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF0E141B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Buildings in selected category
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(buildingsInCat, key = { it.name }) { bType ->
                    val unlocked = bType.requiredTechId == null || bType.requiredTechId in unlockedTechs
                    val canAfford = bType.buildCost.all { (res, cost) -> (inventory[res] ?: 0) >= cost }
                    val isSelected = !demolishMode && selectedTool == bType
                    val borderColor = when {
                        isSelected -> SafetyAmber
                        !unlocked -> SteelBorder.copy(alpha = 0.5f)
                        canAfford -> bType.accentColor.copy(alpha = 0.7f)
                        else -> HazardCrimson.copy(alpha = 0.55f)
                    }

                    Surface(
                        color = if (isSelected) SafetyAmber.copy(alpha = 0.16f) else SteelPanel,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
                        modifier = Modifier
                            .width(126.dp)
                            .clickable {
                                if (unlocked) onSelectTool(bType) else onLockedToolClick()
                            }
                            .testTag("build_tool_${bType.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bType.shortCode,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (unlocked) bType.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!unlocked) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = "Locked technology",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                } else {
                                    val mwText = if (bType.powerMw >= 0) "+${bType.powerMw}MW" else "${bType.powerMw}MW"
                                    Text(
                                        text = mwText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (bType.powerMw >= 0) TelemetryEmerald else PlasmaCyan
                                    )
                                }
                            }
                            Text(
                                text = bType.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (!unlocked) "Unlock in Tech Tree"
                                else bType.buildCost.entries.joinToString(" ") { "${it.value}${it.key.code}" },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    !unlocked -> SafetyAmber.copy(alpha = 0.8f)
                                    canAfford -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> HazardCrimson
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
