package xyz.totemai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun TotemTheme(
    themeMode: String = "dark",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val palette = if (darkTheme) DarkTotemPalette else LightTotemPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = Lime,
            onPrimary = Ink,
            primaryContainer = LimeDim,
            onPrimaryContainer = Ink,
            secondary = palette.violet,
            onSecondary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.elevated,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.outline,
            outlineVariant = palette.outline,
            error = palette.danger,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = palette.violet,
            onPrimary = Color.White,
            primaryContainer = Lime,
            onPrimaryContainer = Ink,
            secondary = palette.violet,
            onSecondary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.elevated,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.outline,
            outlineVariant = palette.outline,
            error = palette.danger,
            onError = Color.White
        )
    }

    CompositionLocalProvider(
        LocalTotemPalette provides palette,
        LocalIsDarkTotemTheme provides darkTheme
    ) {
        MaterialTheme(colorScheme = scheme, typography = TotemTypography, content = content)
    }
}
