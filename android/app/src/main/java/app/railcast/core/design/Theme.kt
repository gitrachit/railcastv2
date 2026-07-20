package app.railcast.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

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
    sunlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Sunlight (FR-5.3) outranks dark: a user who has asked for the high-contrast
    // platform theme wants it whatever the system says.
    val colors = when {
        sunlight -> RailcastSunlightColors
        dark -> RailcastDarkColors
        else -> RailcastLightColors
    }
    val material = if (colors.isDark) {
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

    // Respect OS font scaling but cap it (design blueprint §2.3/§8): large system
    // text should enlarge type comfortably, but past ~1.3× it blows through
    // layouts and clips content. Clamp once, app-wide, at the theme root.
    val density = LocalDensity.current
    val clamped =
        if (density.fontScale <= MAX_FONT_SCALE) density
        else Density(density.density, MAX_FONT_SCALE)

    CompositionLocalProvider(
        LocalRailcastColors provides colors,
        LocalDensity provides clamped,
    ) {
        MaterialTheme(colorScheme = material, typography = RailcastTypography, content = content)
    }
}

private const val MAX_FONT_SCALE = 1.3f

private val Color_White = androidx.compose.ui.graphics.Color.White
