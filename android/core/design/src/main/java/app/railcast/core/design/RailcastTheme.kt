package app.railcast.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.railcast.core.design.token.DarkPalette
import app.railcast.core.design.token.LightPalette
import app.railcast.core.design.token.RailcastPalette
import app.railcast.core.design.token.RailcastType

val LocalRailcastColors = staticCompositionLocalOf { LightPalette }

/** Extended (non-Material) tokens: soft status fills, board colors, ink levels. */
object RailcastTheme {
    val colors: RailcastPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalRailcastColors.current
}

private fun lightScheme(p: RailcastPalette) = lightColorScheme(
    primary = p.brand,
    onPrimary = Color.White,
    primaryContainer = p.brandSoft,
    onPrimaryContainer = p.brand,
    background = p.bg,
    onBackground = p.ink,
    surface = p.surface,
    onSurface = p.ink,
    surfaceVariant = p.surface2,
    onSurfaceVariant = p.ink2,
    error = p.red,
    errorContainer = p.redSoft,
    onErrorContainer = p.red,
    outline = p.line,
    outlineVariant = p.line,
)

private fun darkScheme(p: RailcastPalette) = darkColorScheme(
    primary = p.brand,
    onPrimary = Color.White,
    primaryContainer = p.brandSoft,
    onPrimaryContainer = p.brand,
    background = p.bg,
    onBackground = p.ink,
    surface = p.surface,
    onSurface = p.ink,
    surfaceVariant = p.surface2,
    onSurfaceVariant = p.ink2,
    error = p.red,
    errorContainer = p.redSoft,
    onErrorContainer = p.red,
    outline = p.line,
    outlineVariant = p.line,
)

private val RailcastTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.merge(RailcastType.Hero),
        titleLarge = titleLarge.merge(RailcastType.StatusBig),
        titleMedium = titleMedium.merge(RailcastType.CardTitle),
        bodyMedium = bodyMedium.merge(RailcastType.Body),
        bodySmall = bodySmall.merge(RailcastType.Meta),
        labelSmall = labelSmall.merge(RailcastType.Eyebrow),
    )
}

@Composable
fun RailcastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalRailcastColors provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme(palette) else lightScheme(palette),
            typography = RailcastTypography,
            content = content,
        )
    }
}
