package xyz.totemai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TotemColors = darkColorScheme(
    primary = Lime,
    onPrimary = Ink,
    primaryContainer = LimeDim,
    onPrimaryContainer = Ink,
    secondary = Violet,
    onSecondary = TextPrimary,
    background = Ink,
    onBackground = TextPrimary,
    surface = InkElevated,
    onSurface = TextPrimary,
    surfaceVariant = Surface1,
    onSurfaceVariant = TextSecondary,
    outline = Outline,
    outlineVariant = Outline,
    error = Danger,
    onError = Ink
)

@Composable
fun TotemTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TotemColors,
        typography = TotemTypography,
        content = content
    )
}
