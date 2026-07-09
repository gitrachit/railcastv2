package app.railcast.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.token.RailcastPalette
import app.railcast.core.design.token.RailcastType

/**
 * Semantic tone of a status. Rendering is always redundant — icon + word +
 * color together, never color alone. [FR-10.2]
 */
enum class StatusTone { Positive, Warning, Critical, Neutral }

@Immutable
data class StatusToneColors(val container: Color, val content: Color)

fun StatusTone.colors(palette: RailcastPalette): StatusToneColors = when (this) {
    StatusTone.Positive -> StatusToneColors(palette.greenSoft, palette.green)
    StatusTone.Warning -> StatusToneColors(palette.amberSoft, palette.amber)
    StatusTone.Critical -> StatusToneColors(palette.redSoft, palette.red)
    StatusTone.Neutral -> StatusToneColors(palette.surface2, palette.ink2)
}

/**
 * The one way user-visible status is rendered. All three encodings are
 * required parameters so no call site can drop down to color-only. [FR-10.2]
 */
@Composable
fun StatusChip(
    tone: StatusTone,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val toneColors = tone.colors(RailcastTheme.colors)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(toneColors.container)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            // One TalkBack stop announcing the status word once. [FR-10.3]
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = toneColors.content,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = RailcastType.ChipLabel,
            color = toneColors.content,
        )
    }
}

@Preview(name = "StatusChip tones", showBackground = true)
@Composable
private fun StatusChipPreview() {
    RailcastTheme(darkTheme = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(StatusTone.Positive, Icons.Filled.CheckCircle, "On time")
            StatusChip(StatusTone.Warning, Icons.Filled.Schedule, "17 min late")
            StatusChip(StatusTone.Critical, Icons.Filled.Cancel, "Cancelled")
        }
    }
}

@Preview(name = "StatusChip dark", showBackground = true, backgroundColor = 0xFF081115)
@Composable
private fun StatusChipDarkPreview() {
    RailcastTheme(darkTheme = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(StatusTone.Positive, Icons.Filled.CheckCircle, "On time")
            StatusChip(StatusTone.Warning, Icons.Filled.Schedule, "17 min late")
            StatusChip(StatusTone.Critical, Icons.Filled.Cancel, "Cancelled")
        }
    }
}
