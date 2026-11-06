package xyz.totemai.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Stable brand ink, used for readable text on lime buttons.
val Ink = Color(0xFF06080A)
val Lime = Color(0xFFC6F24E)
val LimeDim = Color(0xFF8FB733)

internal data class TotemPalette(
    val background: Color,
    val elevated: Color,
    val surface: Color,
    val outline: Color,
    val violet: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val danger: Color
)

internal val DarkTotemPalette = TotemPalette(
    background = Color(0xFF06080A),
    elevated = Color(0xFF0E1312),
    surface = Color(0xFF131A18),
    outline = Color(0xFF243029),
    violet = Color(0xFF8A6BFF),
    textPrimary = Color(0xFFF2F6F0),
    textSecondary = Color(0xFF97A69B),
    danger = Color(0xFFFF6B6B)
)

internal val LightTotemPalette = TotemPalette(
    background = Color(0xFFF4F7F2),
    elevated = Color(0xFFFFFFFF),
    surface = Color(0xFFEBF0E9),
    outline = Color(0xFFD7E0D6),
    violet = Color(0xFF6848D8),
    textPrimary = Color(0xFF17221A),
    textSecondary = Color(0xFF5D6B60),
    danger = Color(0xFFC83F49)
)

internal val LocalTotemPalette = staticCompositionLocalOf { DarkTotemPalette }

val AppBackground: Color
    @Composable get() = LocalTotemPalette.current.background
val InkElevated: Color
    @Composable get() = LocalTotemPalette.current.elevated
val Surface1: Color
    @Composable get() = LocalTotemPalette.current.surface
val Outline: Color
    @Composable get() = LocalTotemPalette.current.outline
val Violet: Color
    @Composable get() = LocalTotemPalette.current.violet
val TextPrimary: Color
    @Composable get() = LocalTotemPalette.current.textPrimary
val TextSecondary: Color
    @Composable get() = LocalTotemPalette.current.textSecondary
val Danger: Color
    @Composable get() = LocalTotemPalette.current.danger
