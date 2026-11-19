package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.data.model.BiomeCatalog
import com.example.data.model.BuildingType
import com.example.data.model.Direction
import com.example.data.model.HazardVisualType
import com.example.data.model.PlacedBuildingState
import com.example.simulation.GRID_SIZE
import com.example.simulation.PlanetSimState
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PlasmaCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SlateVoid
import com.example.ui.theme.TelemetryEmerald
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

@Composable
fun FactoryCanvas(
    planetState: PlanetSimState,
    inspectedTile: Pair<Int, Int>?,
    selectedBuildTool: BuildingType?,
    demolishMode: Boolean,
    zoomLevel: Float,
    panOffset: Offset,
    onPanZoomChange: (Offset, Float) -> Unit,
    onTileTapped: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val biome = remember(planetState.planetId) { BiomeCatalog.byId(planetState.planetId) }
    val textMeasurer = rememberTextMeasurer()

    val infiniteTransition = rememberInfiniteTransition(label = "factory_anim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "belt_phase"
    )
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hazard_pulse"
    )

    var lastCanvasSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateVoid)
            .testTag("factory_grid_canvas")
            .pointerInput(zoomLevel, panOffset) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newZoom = (zoomLevel * zoom).coerceIn(0.75f, 2.3f)
                    val maxPan = 420f * newZoom
                    val newPan = Offset(
                        x = (panOffset.x + pan.x).coerceIn(-maxPan, maxPan),
                        y = (panOffset.y + pan.y).coerceIn(-maxPan, maxPan)
                    )
                    onPanZoomChange(newPan, newZoom)
                }
            }
            .pointerInput(zoomLevel, panOffset, planetState.planetId, selectedBuildTool, demolishMode) {
                detectTapGestures { tapPos ->
                    if (lastCanvasSize == Size.Zero) return@detectTapGestures
                    val baseBoardSize = min(lastCanvasSize.width, lastCanvasSize.height) * 0.94f
                    val boardSize = baseBoardSize * zoomLevel
                    val cellSize = boardSize / GRID_SIZE
                    val originX = (lastCanvasSize.width - boardSize) / 2f + panOffset.x
                    val originY = (lastCanvasSize.height - boardSize) / 2f + panOffset.y
                    val gx = floor((tapPos.x - originX) / cellSize).toInt()
                    val gy = floor((tapPos.y - originY) / cellSize).toInt()
                    if (gx in 0 until GRID_SIZE && gy in 0 until GRID_SIZE) {
                        onTileTapped(gx, gy)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            lastCanvasSize = size
            val baseBoardSize = min(size.width, size.height) * 0.94f
            val boardSize = baseBoardSize * zoomLevel
            val cellSize = boardSize / GRID_SIZE
            val originX = (size.width - boardSize) / 2f + panOffset.x
            val originY = (size.height - boardSize) / 2f + panOffset.y

            // 1. Subtle Blueprint Frame Glow
            drawRect(
                color = biome.primaryColor.copy(alpha = 0.08f),
                topLeft = Offset(originX - 4f, originY - 4f),
                size = Size(boardSize + 8f, boardSize + 8f)
            )
            drawRect(
                color = biome.primaryColor.copy(alpha = 0.35f),
                topLeft = Offset(originX - 2f, originY - 2f),
                size = Size(boardSize + 4f, boardSize + 4f),
                style = Stroke(width = 2f)
            )

            // 2. Terrain Tiles, Ore Veins, Geothermal Vents, Hazard Channels
            for (tile in planetState.tiles) {
                val left = originX + tile.x * cellSize
                val top = originY + tile.y * cellSize
                val isAlt = (tile.x + tile.y) % 2 == 0
                val baseColor = when {
                    tile.hazardTerrain -> biome.hazardTileColor.copy(alpha = 0.35f + 0.30f * pulsePhase)
                    isAlt -> biome.groundDark
                    else -> biome.groundLight
                }
                drawRect(
                    color = baseColor,
                    topLeft = Offset(left, top),
                    size = Size(cellSize, cellSize)
                )

                // Crisp blueprint hairline
                drawRect(
                    color = Color.White.copy(alpha = 0.05f),
                    topLeft = Offset(left, top),
                    size = Size(cellSize, cellSize),
                    style = Stroke(width = 1f)
                )

                // Hazard terrain glowing fissures
                if (tile.hazardTerrain) {
                    drawLine(
                        color = biome.hazardTileColor.copy(alpha = 0.75f * pulsePhase),
                        start = Offset(left + cellSize * 0.15f, top + cellSize * 0.25f),
                        end = Offset(left + cellSize * 0.85f, top + cellSize * 0.75f),
                        strokeWidth = cellSize * 0.10f,
                        cap = StrokeCap.Round
                    )
                }

                // Geothermal Vent Steam Ring
                if (tile.geothermalVent) {
                    val center = Offset(left + cellSize / 2f, top + cellSize / 2f)
                    drawCircle(
                        color = Color(0xFFFF5722).copy(alpha = 0.28f + 0.2f * pulsePhase),
                        radius = cellSize * 0.36f,
                        center = center
                    )
                    drawCircle(
                        color = SafetyAmber,
                        radius = cellSize * 0.24f,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                }

                // Mineral Ore Vein crystals + code tag
                val ore = tile.ore
                if (ore != null) {
                    val cx = left + cellSize / 2f
                    val cy = top + cellSize / 2f
                    val r = cellSize * 0.30f
                    drawCircle(
                        color = ore.color.copy(alpha = 0.24f),
                        radius = cellSize * 0.42f,
                        center = Offset(cx, cy)
                    )
                    val hexPath = Path().apply {
                        for (i in 0..5) {
                            val angle = (i * 60.0 - 30.0) * PI / 180.0
                            val px = cx + (r * cos(angle)).toFloat()
                            val py = cy + (r * sin(angle)).toFloat()
                            if (i == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                    }
                    drawPath(hexPath, color = ore.color.copy(alpha = 0.78f))
                    drawPath(hexPath, color = Color.White.copy(alpha = 0.4f), style = Stroke(width = 1.2f))

                    // Draw ore symbol only if no building covers it and tile is large enough
                    if (!planetState.buildings.containsKey(tile.x to tile.y) && cellSize >= 26f) {
                        drawTileTag(
                            textMeasurer = textMeasurer,
                            text = ore.code,
                            center = Offset(cx, cy),
                            color = Color(0xFF0A0F14),
                            fontSizeSp = (cellSize * 0.24f).coerceIn(7f, 12f)
                        )
                    }
                }
            }

            // 3. Shield / Scrubber / Turret Protection Radii
            for (b in planetState.buildings.values) {
                if (b.type.protectionRadius > 0) {
                    val cx = originX + (b.x + 0.5f) * cellSize
                    val cy = originY + (b.y + 0.5f) * cellSize
                    val radiusPx = (b.type.protectionRadius + 0.5f) * cellSize
                    val fieldColor = when (b.type) {
                        BuildingType.THERMAL_PYLON -> PlasmaCyan
                        BuildingType.ATMOS_SCRUBBER -> TelemetryEmerald
                        else -> HazardCrimson
                    }
                    drawCircle(
                        color = fieldColor.copy(alpha = 0.06f + 0.03f * pulsePhase),
                        radius = radiusPx,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = fieldColor.copy(alpha = 0.35f),
                        radius = radiusPx,
                        center = Offset(cx, cy),
                        style = Stroke(
                            width = 1.6f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), animPhase * 36f)
                        )
                    )
                }
            }

            // 4. Placed Buildings & Directional Conveyor Belts
            for (b in planetState.buildings.values) {
                val left = originX + b.x * cellSize
                val top = originY + b.y * cellSize
                drawBuilding(
                    building = b,
                    left = left,
                    top = top,
                    cellSize = cellSize,
                    animPhase = animPhase,
                    pulsePhase = pulsePhase,
                    textMeasurer = textMeasurer
                )
            }

            // 5. Animated Cargo Packets flowing along Belts
            for (packet in planetState.cargoPackets) {
                val ix = packet.fromX + (packet.x - packet.fromX) * packet.progress
                val iy = packet.fromY + (packet.y - packet.fromY) * packet.progress
                val px = originX + (ix + 0.5f) * cellSize
                val py = originY + (iy + 0.5f) * cellSize
                drawCircle(
                    color = packet.resource.color.copy(alpha = 0.35f),
                    radius = cellSize * 0.24f,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = packet.resource.color,
                    radius = cellSize * 0.15f,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f),
                    radius = cellSize * 0.06f,
                    center = Offset(px - cellSize * 0.04f, py - cellSize * 0.04f)
                )
            }

            // 6. Auto-Turret Defense Beams during Wildlife Migrations
            for (beam in planetState.turretBeams) {
                val start = Offset(
                    originX + (beam.fromX + 0.5f) * cellSize,
                    originY + (beam.fromY + 0.5f) * cellSize
                )
                val end = Offset(
                    originX + (beam.targetX + 0.5f) * cellSize,
                    originY + (beam.targetY + 0.5f) * cellSize
                )
                drawLine(
                    color = HazardCrimson.copy(alpha = 0.45f),
                    start = start,
                    end = end,
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = start,
                    end = end,
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
                drawCircle(color = SafetyAmber, radius = cellSize * 0.16f, center = end)
            }

            // 6b. Live Unit Combat & Nano-Repair Beams
            for (beam in planetState.combatBeams) {
                val start = Offset(
                    originX + (beam.fromX + 0.5f) * cellSize,
                    originY + (beam.fromY + 0.5f) * cellSize
                )
                val end = Offset(
                    originX + (beam.targetX + 0.5f) * cellSize,
                    originY + (beam.targetY + 0.5f) * cellSize
                )
                val beamColor = when {
                    beam.isRepair -> TelemetryEmerald
                    beam.isPlayer -> PlasmaCyan
                    else -> HazardCrimson
                }
                drawLine(
                    color = beamColor.copy(alpha = 0.50f),
                    start = start,
                    end = end,
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = start,
                    end = end,
                    strokeWidth = 1.8f,
                    cap = StrokeCap.Round
                )
                drawCircle(color = beamColor, radius = cellSize * 0.12f, center = end)
            }

            // 6c. Live Mobile ECS Player (UEC) & Enemy (Xeno) Units
            for (unit in planetState.units) {
                val ux = originX + (unit.x + 0.5f) * cellSize
                val uy = originY + (unit.y + 0.5f) * cellSize
                val unitColor = com.example.data.model.UnitCatalog.byId(unit.archetypeId)?.accentColor
                    ?: if (unit.isPlayer) PlasmaCyan else HazardCrimson
                val radius = cellSize * if (unit.shortCode == "LEV" || unit.shortCode == "GNS") 0.36f else 0.28f

                // Ambient tactical halo
                drawCircle(
                    color = unitColor.copy(alpha = 0.25f + 0.12f * pulsePhase),
                    radius = radius * 1.45f,
                    center = Offset(ux, uy)
                )

                if (unit.isPlayer) {
                    // UEC Unit: crisp armored hexagonal/diamond chassis
                    val diamond = Path().apply {
                        moveTo(ux, uy - radius)
                        lineTo(ux + radius, uy)
                        lineTo(ux, uy + radius)
                        lineTo(ux - radius, uy)
                        close()
                    }
                    drawPath(diamond, color = Color(0xFF0B1926))
                    drawPath(diamond, color = unitColor, style = Stroke(width = 2.4f))
                    drawCircle(color = unitColor, radius = radius * 0.35f, center = Offset(ux, uy))
                } else {
                    // Hostile Xeno Unit: jagged triangular predator silhouette
                    val fang = Path().apply {
                        moveTo(ux, uy - radius * 1.1f)
                        lineTo(ux + radius * 1.05f, uy + radius * 0.85f)
                        lineTo(ux, uy + radius * 0.4f)
                        lineTo(ux - radius * 1.05f, uy + radius * 0.85f)
                        close()
                    }
                    drawPath(fang, color = Color(0xFF280B10))
                    drawPath(fang, color = unitColor, style = Stroke(width = 2.4f))
                }

                // Compact unit HP bar above unit
                val hpRatio = (unit.hp.toFloat() / unit.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                val barW = cellSize * 0.62f
                val barLeft = ux - barW / 2f
                val barTop = uy - radius - 6f
                drawRect(
                    color = Color.Black.copy(alpha = 0.78f),
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barW, 3.5f)
                )
                drawRect(
                    color = if (unit.isPlayer) TelemetryEmerald else HazardCrimson,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barW * hpRatio, 3.5f)
                )
            }

            // 7. Inspected Tile Highlight Reticle
            if (inspectedTile != null) {
                val (ix, iy) = inspectedTile
                val left = originX + ix * cellSize
                val top = originY + iy * cellSize
                drawRect(
                    color = SafetyAmber.copy(alpha = 0.22f),
                    topLeft = Offset(left, top),
                    size = Size(cellSize, cellSize)
                )
                drawRect(
                    color = SafetyAmber,
                    topLeft = Offset(left, top),
                    size = Size(cellSize, cellSize),
                    style = Stroke(width = 3f)
                )
            }

            // 8. Dynamic Environmental Weather Overlay
            val activeHazard = planetState.activeHazard
            if (activeHazard != null) {
                drawHazardAtmosphere(
                    visualType = activeHazard.visualType,
                    animPhase = animPhase,
                    pulsePhase = pulsePhase
                )
            }
        }
    }
}

private fun DrawScope.drawBuilding(
    building: PlacedBuildingState,
    left: Float,
    top: Float,
    cellSize: Float,
    animPhase: Float,
    pulsePhase: Float,
    textMeasurer: TextMeasurer
) {
    val cx = left + cellSize / 2f
    val cy = top + cellSize / 2f
    val pad = cellSize * 0.08f
    val inner = cellSize - pad * 2f

    if (building.type.isConveyor()) {
        // Draw conveyor rails and animated directional chevrons
        val trackColor = when (building.type) {
            BuildingType.CONVEYOR_MK3 -> Color(0xFF3B1E54)
            BuildingType.CONVEYOR_MK2 -> Color(0xFF16354D)
            BuildingType.OMNI_ROUTER -> Color(0xFF1E3A5F)
            else -> Color(0xFF253241)
        }
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(left + pad, top + pad),
            size = Size(inner, inner),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = building.type.accentColor.copy(alpha = 0.65f),
            topLeft = Offset(left + pad, top + pad),
            size = Size(inner, inner),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1.5f)
        )
        rotate(degrees = building.dir.degrees, pivot = Offset(cx, cy)) {
            val chevronColor = building.type.accentColor.copy(alpha = 0.85f)
            val offsetShift = (animPhase * cellSize * 0.45f) % (cellSize * 0.45f)
            for (k in 0..1) {
                val yPos = top + cellSize * 0.72f - offsetShift - (k * cellSize * 0.34f)
                if (yPos in (top + pad * 1.5f)..(top + cellSize - pad * 1.5f)) {
                    val chevron = Path().apply {
                        moveTo(cx - cellSize * 0.22f, yPos + cellSize * 0.10f)
                        lineTo(cx, yPos - cellSize * 0.08f)
                        lineTo(cx + cellSize * 0.22f, yPos + cellSize * 0.10f)
                    }
                    drawPath(chevron, color = chevronColor, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
                }
            }
        }
        return
    }

    // Industrial Machine Hull
    val hullBg = if (building.type == BuildingType.ORBITAL_HUB) {
        Color(0xFF0E2938)
    } else {
        Color(0xFF16202C)
    }
    drawRoundRect(
        color = hullBg,
        topLeft = Offset(left + pad, top + pad),
        size = Size(inner, inner),
        cornerRadius = CornerRadius(cellSize * 0.14f, cellSize * 0.14f)
    )
    drawRoundRect(
        color = building.type.accentColor,
        topLeft = Offset(left + pad, top + pad),
        size = Size(inner, inner),
        cornerRadius = CornerRadius(cellSize * 0.14f, cellSize * 0.14f),
        style = Stroke(width = if (building.type == BuildingType.ORBITAL_HUB) 3f else 2f)
    )

    // Direction nozzle indicator for extractors & producers
    if (building.type.isExtractor() || building.type.isProducer()) {
        rotate(degrees = building.dir.degrees, pivot = Offset(cx, cy)) {
            val arrow = Path().apply {
                moveTo(cx - cellSize * 0.13f, top + pad + 2f)
                lineTo(cx, top + 1f)
                lineTo(cx + cellSize * 0.13f, top + pad + 2f)
                close()
            }
            drawPath(arrow, color = SafetyAmber)
        }
    }

    // Orbital Hub Pulsing Core Ring
    if (building.type == BuildingType.ORBITAL_HUB) {
        drawCircle(
            color = PlasmaCyan.copy(alpha = 0.25f * pulsePhase),
            radius = cellSize * 0.36f,
            center = Offset(cx, cy)
        )
    }

    // Short industrial building code
    if (cellSize >= 24f) {
        drawTileTag(
            textMeasurer = textMeasurer,
            text = building.type.shortCode,
            center = Offset(cx, cy),
            color = Color(0xFFEAF0F6),
            fontSizeSp = (cellSize * 0.24f).coerceIn(7f, 11.5f)
        )
    }

    // Status LED (top-right corner)
    val ledColor = when {
        building.statusNote.contains("HAZARD") || building.statusNote.contains("ATTACK") -> HazardCrimson
        building.statusNote == "STARVED" || building.statusNote == "NO VEIN" || building.statusNote == "LOW PWR" -> SafetyAmber
        else -> TelemetryEmerald
    }
    drawCircle(
        color = ledColor,
        radius = cellSize * 0.08f,
        center = Offset(left + cellSize - pad * 2.1f, top + pad * 2.1f)
    )

    // Integrity bar when damaged
    if (building.integrity < building.type.maxIntegrity) {
        val ratio = (building.integrity.toFloat() / building.type.maxIntegrity.toFloat()).coerceIn(0f, 1f)
        val barWidth = inner * 0.8f
        val barLeft = left + (cellSize - barWidth) / 2f
        val barTop = top + cellSize - pad - 4f
        drawRect(
            color = Color.Black.copy(alpha = 0.7f),
            topLeft = Offset(barLeft, barTop),
            size = Size(barWidth, 3.5f)
        )
        drawRect(
            color = if (ratio > 0.5f) SafetyAmber else HazardCrimson,
            topLeft = Offset(barLeft, barTop),
            size = Size(barWidth * ratio, 3.5f)
        )
    }
}

private fun DrawScope.drawHazardAtmosphere(
    visualType: HazardVisualType,
    animPhase: Float,
    pulsePhase: Float
) {
    when (visualType) {
        HazardVisualType.ASH_STORM -> {
            drawRect(color = Color(0xFF473732).copy(alpha = 0.24f))
            for (i in 0..22) {
                val x = ((i * 89f + animPhase * size.width * 0.8f) % size.width)
                val y = ((i * 53f + animPhase * size.height * 0.35f) % size.height)
                drawCircle(color = Color(0xFFD6CFCB).copy(alpha = 0.35f), radius = 3.5f, center = Offset(x, y))
            }
        }
        HazardVisualType.LAVA_SURGE -> {
            drawRect(color = Color(0xFFFF4500).copy(alpha = 0.08f + 0.08f * pulsePhase))
        }
        HazardVisualType.CRYO_BLIZZARD -> {
            drawRect(color = Color(0xFF38BDF8).copy(alpha = 0.14f))
            for (i in 0..28) {
                val x = ((i * 71f + animPhase * size.width) % size.width)
                val y = ((i * 47f + animPhase * size.height * 0.7f) % size.height)
                drawLine(
                    color = Color(0xFFE0F2FE).copy(alpha = 0.45f),
                    start = Offset(x, y),
                    end = Offset(x - 22f, y + 14f),
                    strokeWidth = 2f
                )
            }
        }
        HazardVisualType.ION_SANDSTORM -> {
            drawRect(color = Color(0xFFD97706).copy(alpha = 0.18f))
            for (i in 0..24) {
                val x = ((i * 67f + animPhase * size.width * 1.1f) % size.width)
                val y = ((i * 41f + animPhase * size.height * 0.2f) % size.height)
                drawLine(
                    color = SafetyAmber.copy(alpha = 0.45f),
                    start = Offset(x, y),
                    end = Offset(x + 28f, y + 4f),
                    strokeWidth = 2f
                )
            }
        }
        HazardVisualType.SOLAR_FLARE -> {
            drawRect(color = Color(0xFFFDE047).copy(alpha = 0.10f + 0.08f * pulsePhase))
        }
        HazardVisualType.ACID_RAIN -> {
            drawRect(color = Color(0xFF10B981).copy(alpha = 0.10f))
            for (i in 0..20) {
                val x = ((i * 73f + animPhase * size.width * 0.3f) % size.width)
                val y = ((i * 61f + animPhase * size.height) % size.height)
                drawLine(
                    color = Color(0xFF34D399).copy(alpha = 0.48f),
                    start = Offset(x, y),
                    end = Offset(x - 6f, y + 18f),
                    strokeWidth = 1.8f
                )
            }
        }
        HazardVisualType.NONE -> Unit
    }
}

private fun DrawScope.drawTileTag(
    textMeasurer: TextMeasurer,
    text: String,
    center: Offset,
    color: Color,
    fontSizeSp: Float
) {
    val result = textMeasurer.measure(
        text = text,
        style = TextStyle(
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSizeSp.sp,
            color = color
        )
    )
    drawText(
        textLayoutResult = result,
        topLeft = Offset(
            x = center.x - result.size.width / 2f,
            y = center.y - result.size.height / 2f
        )
    )
}
