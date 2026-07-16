package app.railcast.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.railcast.core.design.Radius
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.Spacing

/**
 * A two-plus option segmented control (design review, phase 2). The selected
 * segment lifts to the surface colour with brand text; the rest sit flat on
 * surface-2. Each segment is a ≥48dp Tab target whose label is its accessible
 * name (FR-10.3). Selection colours ease so the switch reads as a move.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RailcastTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.full))
            .background(colors.surface2)
            .padding(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selectedIndex
            val bg by animateColorAsState(
                if (on) colors.surface else Color.Transparent, tween(160), label = "seg-bg",
            )
            val fg by animateColorAsState(
                if (on) colors.brand else colors.ink2, tween(160), label = "seg-fg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(bg)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(index) })
                    .heightIn(min = 48.dp)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}
