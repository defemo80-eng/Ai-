package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ColorTokenHighestDark = Color(0xFF1A2F5A)
private val ColorTokenOutlineVariantDark = Color(0xFF172952)
private val ColorTokenHighLight = Color(0xFFD9E7FC)
private val ColorTokenOutlineVariantLight = Color(0xFFDCE7F7)

private val ChatDepDarkColorScheme = darkColorScheme(
    primary = ElectricBlueBright,
    onPrimary = CrispWhite,
    primaryContainer = DeepElectricBlue,
    onPrimaryContainer = CrispWhite,
    secondary = CyanGlow,
    onSecondary = MidnightNavy,
    secondaryContainer = NavySurfaceVariant,
    onSecondaryContainer = SoftIceWhite,
    tertiary = ElectricBlue,
    onTertiary = CrispWhite,
    background = MidnightNavy,
    onBackground = CrispWhite,
    surface = DeepNavy,
    onSurface = CrispWhite,
    surfaceVariant = NavySurface,
    onSurfaceVariant = MutedBlueGray,
    surfaceContainerLowest = MidnightNavy,
    surfaceContainerLow = DeepNavy,
    surfaceContainer = NavySurface,
    surfaceContainerHigh = NavySurfaceVariant,
    surfaceContainerHighest = ColorTokenHighestDark,
    outline = NavyBorder,
    outlineVariant = ColorTokenOutlineVariantDark,
    error = ErrorRedDark,
    onError = MidnightNavy,
    errorContainer = ErrorContainerDark,
    onErrorContainer = CrispWhite,
)

private val ChatDepLightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = CrispWhite,
    primaryContainer = IceSurface,
    onPrimaryContainer = DeepNavy,
    secondary = DeepElectricBlue,
    onSecondary = CrispWhite,
    secondaryContainer = IceSurface,
    onSecondaryContainer = DeepNavy,
    tertiary = CyanGlow,
    onTertiary = MidnightNavy,
    background = SoftIceWhite,
    onBackground = DeepNavy,
    surface = CrispWhite,
    onSurface = DeepNavy,
    surfaceVariant = IceSurface,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = CrispWhite,
    surfaceContainerLow = SoftIceWhite,
    surfaceContainer = IceSurface,
    surfaceContainerHigh = ColorTokenHighLight,
    surfaceContainerHighest = IceBorder,
    outline = IceBorder,
    outlineVariant = ColorTokenOutlineVariantLight,
    error = ErrorRedLight,
    onError = CrispWhite,
    errorContainer = ErrorContainerLight,
    onErrorContainer = ErrorRedLight,
)

val ChatDepShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ChatDepTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ChatDepDarkColorScheme else ChatDepLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChatDepTypography,
        shapes = ChatDepShapes,
        content = content
    )
}
