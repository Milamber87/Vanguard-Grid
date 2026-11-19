package com.example.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorBorderSubtle = androidx.compose.ui.graphics.Color(0xFF243244)

private val VanguardDarkColorScheme = darkColorScheme(
    primary = SafetyAmber,
    onPrimary = OnSafetyAmber,
    primaryContainer = SafetyAmberDark,
    onPrimaryContainer = SafetyAmberBright,
    secondary = PlasmaCyan,
    onSecondary = OnPlasmaCyan,
    secondaryContainer = PlasmaCyanDark,
    onSecondaryContainer = PlasmaCyan,
    tertiary = TelemetryEmerald,
    onTertiary = OnSafetyAmber,
    tertiaryContainer = TelemetryEmeraldDark,
    onTertiaryContainer = TelemetryEmerald,
    background = SlateVoid,
    onBackground = TitaniumText,
    surface = SteelHull,
    onSurface = TitaniumText,
    surfaceVariant = SteelPanel,
    onSurfaceVariant = MutedSteelText,
    surfaceContainerLowest = SlateVoid,
    surfaceContainerLow = SteelHull,
    surfaceContainer = SteelPanel,
    surfaceContainerHigh = SteelElevated,
    outline = SteelBorder,
    outlineVariant = ColorBorderSubtle,
    error = HazardCrimson,
    onError = TitaniumText,
    errorContainer = HazardCrimsonDark,
    onErrorContainer = TitaniumText
)

val VanguardShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = CutCornerShape(topEnd = 8.dp, bottomStart = 8.dp, topStart = 2.dp, bottomEnd = 2.dp),
    medium = CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp, topStart = 4.dp, bottomEnd = 4.dp),
    large = CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp, topStart = 4.dp, bottomEnd = 4.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun VanguardGridTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VanguardDarkColorScheme,
        typography = VanguardTypography,
        shapes = VanguardShapes,
        content = content
    )
}
