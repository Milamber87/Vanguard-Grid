package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BiomeCatalog
import com.example.data.model.PlanetBiome
import com.example.simulation.PlanetSimState
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.PlasmaCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SlateVoid
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.SteelHull
import com.example.ui.theme.SteelPanel
import com.example.ui.theme.TelemetryEmerald
import com.example.ui.theme.WarningOrange
import com.example.viewmodel.VanguardUiState

@Composable
fun PlanetsScreen(
    uiState: VanguardUiState,
    onDeployToPlanet: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateVoid),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "UEC PLANETARY SECTORS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Each world features distinct ore veins, climate modifiers, and dynamic environmental hazards. Progress on all 4 planets syncs seamlessly to the cloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(BiomeCatalog.allBiomes, key = { it.id }) { biome ->
            val simState = uiState.planets[biome.id]
            val isActive = uiState.activePlanetId == biome.id
            PlanetBiomeCard(
                biome = biome,
                simState = simState,
                isActive = isActive,
                onDeploy = { onDeployToPlanet(biome.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            )
        }
    }
}

@Composable
private fun PlanetBiomeCard(
    biome: PlanetBiome,
    simState: PlanetSimState?,
    isActive: Boolean,
    onDeploy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SteelHull,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (isActive) 2.dp else 1.dp, if (isActive) SafetyAmber else SteelBorder),
        modifier = modifier.testTag("planet_card_${biome.id}")
    ) {
        Column {
            // Hero Banner (Generated Biome Art or Orbital Telemetry Canvas for Starter World)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
            ) {
                if (biome.bannerRes != null) {
                    Image(
                        painter = painterResource(id = biome.bannerRes),
                        contentDescription = "${biome.name} planetary landscape",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    TemperateOrbitalBanner(
                        accent = biome.primaryColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.55f to SlateVoid.copy(alpha = 0.5f),
                                1.0f to SteelHull
                            )
                        )
                )

                // Designation & Active Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        color = SlateVoid.copy(alpha = 0.82f),
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, biome.primaryColor.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = "${biome.designation} // ${biome.climateTag}",
                            style = MaterialTheme.typography.labelSmall,
                            color = biome.primaryColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (isActive) {
                        Surface(
                            color = SafetyAmber,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Active sector",
                                    tint = Color(0xFF0E141B),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.active_sector).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0E141B)
                                )
                            }
                        }
                    }
                }

                // Planet Name anchored at bottom-left of banner
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = biome.name.uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = biome.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Climate & Colony Telemetry Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricChip(
                        icon = Icons.Rounded.Thermostat,
                        label = "${biome.ambientTempC}°C",
                        tint = biome.primaryColor,
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        icon = Icons.Rounded.WbSunny,
                        label = "SOLAR ${(biome.solarMultiplier * 100).toInt()}%",
                        tint = SafetyAmber,
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        icon = Icons.Rounded.Shield,
                        label = "GEO +${biome.geothermalBonusMw}MW",
                        tint = PlasmaCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Colonization Readiness Progress
                val colPct = simState?.colonizationPercent ?: 5
                val structCount = simState?.buildings?.size ?: 1
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "COLONY READINESS ($structCount STRUCTURES)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$colPct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryEmerald
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (colPct / 100f).coerceIn(0f, 1f) },
                        color = TelemetryEmerald,
                        trackColor = SteelPanel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }

                // Resource Vein Distribution
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "PLANETARY MINERAL DISTRIBUTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = PlasmaCyan
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        biome.resourceRichness.entries.take(5).forEach { (res, rating) ->
                            Surface(
                                color = SteelPanel,
                                shape = MaterialTheme.shapes.extraSmall,
                                border = BorderStroke(1.dp, res.color.copy(alpha = 0.45f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = res.code,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = res.color
                                    )
                                    Text(
                                        text = "★".repeat(rating),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SafetyAmber
                                    )
                                }
                            }
                        }
                    }
                }

                // Environmental Hazards & Counter-Strategies
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DYNAMIC ENVIRONMENTAL HAZARDS",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningOrange
                    )
                    biome.hazards.forEach { hazard ->
                        Surface(
                            color = SteelPanel,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = hazard.name,
                                        tint = HazardCrimson,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = hazard.name.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = hazard.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "COUNTER: ${hazard.counterStrategy}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelemetryEmerald
                                )
                            }
                        }
                    }
                }

                // Deploy Action Button
                Button(
                    onClick = onDeploy,
                    enabled = !isActive,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyAmber,
                        contentColor = Color(0xFF0E141B),
                        disabledContainerColor = SteelPanel,
                        disabledContentColor = TelemetryEmerald
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("deploy_planet_${biome.id}")
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.RocketLaunch,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isActive) "CURRENTLY DEPLOYED IN SECTOR" else stringResource(R.string.deploy_to_sector).uppercase(),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SteelPanel,
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, SteelBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TemperateOrbitalBanner(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF0F1D18))) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.35f), Color(0xFF0B1411)),
                center = Offset(size.width * 0.72f, size.height * 0.45f),
                radius = size.width * 0.6f
            )
        )
        // Orbital planetary sphere + telemetry rings
        val planetCenter = Offset(size.width * 0.76f, size.height * 0.52f)
        drawCircle(color = Color(0xFF1A4336), radius = size.height * 0.36f, center = planetCenter)
        drawCircle(color = accent.copy(alpha = 0.7f), radius = size.height * 0.36f, center = planetCenter, style = Stroke(2f))
        drawCircle(color = PlasmaCyan.copy(alpha = 0.35f), radius = size.height * 0.52f, center = planetCenter, style = Stroke(1.5f))
        // Subtle telemetry grid lines
        val step = 36f
        var x = 0f
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = 0.04f), Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
    }
}
