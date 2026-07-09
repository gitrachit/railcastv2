package app.railcast.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Railway-signal status level. The colour is ALWAYS paired with an icon and a
 * word — colour is never the only signal (FR-10.2, invariant 4). Colour-blind
 * users read the icon + word; the palette is colour-blind-safe.
 */
enum class StatusLevel { GOOD, WARN, BAD, NEUTRAL }

/** Foreground signal colour for a level. Pure — unit-tested without Compose UI. */
fun statusColor(level: StatusLevel, colors: RailcastColors): Color = when (level) {
    StatusLevel.GOOD -> colors.green
    StatusLevel.WARN -> colors.amber
    StatusLevel.BAD -> colors.red
    StatusLevel.NEUTRAL -> colors.ink2
}

/** Soft background tint for a level. */
fun statusSoftColor(level: StatusLevel, colors: RailcastColors): Color = when (level) {
    StatusLevel.GOOD -> colors.greenSoft
    StatusLevel.WARN -> colors.amberSoft
    StatusLevel.BAD -> colors.redSoft
    StatusLevel.NEUTRAL -> colors.surface2
}

/**
 * icon + word + colour, together. `icon` is an emoji glyph (matches the
 * prototype, keeps the APK lean — no icon font). The whole chip carries a
 * single content description for screen readers (FR-10.3).
 */
@Composable
fun StatusChip(
    icon: String,
    label: String,
    level: StatusLevel,
    modifier: Modifier = Modifier,
) {
    val colors = RailcastTheme.colors
    Row(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = label }
            .background(statusSoftColor(level, colors), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = "$icon ", fontSize = 13.sp)
        Text(
            text = label,
            color = statusColor(level, colors),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}
