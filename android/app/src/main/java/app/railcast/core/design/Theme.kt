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

/**
 * Palette selection, pure so the precedence rule is testable without Compose.
 *
 * Sunlight (FR-5.3) outranks dark: a user standing on a platform who has asked
 * for the high-contrast theme wants it whatever the system says about night
 * mode. Getting this backwards would silently disable the accessibility theme
 * for every user with system dark mode on — which is most of them.
 */
fun paletteFor(dark: Boolean, sunlight: Boolean): RailcastColors = when {
    sunlight -> RailcastSunlightColors
    dark -> RailcastDarkColors
    else -> RailcastLightColors
}

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
    val colors = paletteFor(dark = dark, sunlight = sunlight)
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

    // OS font scale is honoured in full (FR-10.3, WCAG 1.4.4). This used to be
    // clamped to 1.3x because fact rows clipped past it — but capping the scale
    // denies large text to the users who most need it, and WCAG requires 200%
    // without loss of content. Layouts reflow instead; see Reflow.kt.
    CompositionLocalProvider(
        LocalRailcastColors provides colors,
    ) {
        MaterialTheme(colorScheme = material, typography = RailcastTypography, content = content)
    }
}


private val Color_White = androidx.compose.ui.graphics.Color.White
