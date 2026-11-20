package com.aliasadi.tamdid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.aliasadi.tamdid.core.ThemePreference

/** Design tokens shared with iOS; see docs/DESIGN.md. */
object TamdidColors {
    val Brand = Color(0xFF5B4BDB)
    val BrandDark = Color(0xFF9D92FF)
    val Positive = Color(0xFF12A594)
    val Warning = Color(0xFFF76B15)
    val Danger = Color(0xFFE5484D)

    /** Palette offered for subscriptions; also used for category charts. */
    val Palette = listOf(
        "#5B4BDB", "#E5484D", "#12A594", "#F76B15", "#3E63DD", "#D6409F",
        "#30A46C", "#8E4EC6", "#0090FF", "#AD7F58", "#E54666", "#46A758",
    )

    fun parse(hex: String): Color = runCatching {
        Color(hex.toColorInt())
    }.getOrDefault(Brand)
}

private val LightColors = lightColorScheme(
    primary = TamdidColors.Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E6FF),
    onPrimaryContainer = Color(0xFF1D1659),
    secondary = Color(0xFF5E5C71),
    secondaryContainer = Color(0xFFE6E4F2),
    onSecondaryContainer = Color(0xFF1B1A28),
    tertiary = TamdidColors.Positive,
    error = TamdidColors.Danger,
    background = Color(0xFFF6F6FA),
    onBackground = Color(0xFF16161D),
    surface = Color(0xFFF6F6FA),
    onSurface = Color(0xFF16161D),
    surfaceVariant = Color(0xFFE7E6EF),
    onSurfaceVariant = Color(0xFF5B5A6B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0EFF6),
    surfaceContainerHigh = Color(0xFFEAE9F2),
    outline = Color(0xFFC9C7D6),
    outlineVariant = Color(0xFFE2E0EB),
)

private val DarkColors = darkColorScheme(
    primary = TamdidColors.BrandDark,
    onPrimary = Color(0xFF1D1659),
    primaryContainer = Color(0xFF3A2FA0),
    onPrimaryContainer = Color(0xFFE9E6FF),
    secondary = Color(0xFFC8C5D9),
    secondaryContainer = Color(0xFF34333F),
    onSecondaryContainer = Color(0xFFE6E4F2),
    tertiary = Color(0xFF3DD9C1),
    error = Color(0xFFFF8589),
    background = Color(0xFF111116),
    onBackground = Color(0xFFEDECF4),
    surface = Color(0xFF111116),
    onSurface = Color(0xFFEDECF4),
    surfaceVariant = Color(0xFF2A2A33),
    onSurfaceVariant = Color(0xFFA9A7B8),
    surfaceContainerLowest = Color(0xFF0C0C10),
    surfaceContainerLow = Color(0xFF1B1B22),
    surfaceContainer = Color(0xFF202028),
    surfaceContainerHigh = Color(0xFF2A2A33),
    outline = Color(0xFF4A4957),
    outlineVariant = Color(0xFF2E2D37),
)

private val TamdidShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun TamdidTheme(preference: ThemePreference = ThemePreference.System, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.System -> isSystemInDarkTheme()
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, shapes = TamdidShapes, content = content)
}
