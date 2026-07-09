package app.railcast.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalRailcastColors = staticCompositionLocalOf { RailcastLightColors }

/** Access the Railcast palette anywhere under RailcastTheme. */
object RailcastTheme {
    val colors: RailcastColors
        @Composable @ReadOnlyComposable get() = LocalRailcastColors.current
}

/**
 * Dark mode and light mode are both first-class themes (PRD §7). The palette is
 * exposed via RailcastTheme.colors; Material3 is mapped underneath so stock
 * components inherit the brand accent and signal colours.
 */
@Composable
fun RailcastTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) RailcastDarkColors else RailcastLightColors
    val material = if (dark) {
        darkColorScheme(
            primary = colors.brand,
            background = colors.bg,
            surface = colors.surface,
            onPrimary = Color_White,
            onBackground = colors.ink,
            onSurface = colors.ink,
            error = colors.red,
            outline = colors.line,
        )
    } else {
        lightColorScheme(
            primary = colors.brand,
            background = colors.bg,
            surface = colors.surface,
            onPrimary = Color_White,
            onBackground = colors.ink,
            onSurface = colors.ink,
            error = colors.red,
            outline = colors.line,
        )
    }

    CompositionLocalProvider(LocalRailcastColors provides colors) {
        MaterialTheme(colorScheme = material, typography = RailcastTypography, content = content)
    }
}

private val Color_White = androidx.compose.ui.graphics.Color.White
