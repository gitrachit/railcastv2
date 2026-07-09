package app.railcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.railcast.core.design.RailcastTheme

/**
 * The bottom tab bar (ported from the prototype's .rc-tab). Selected tab uses
 * the brand accent; each tab is a ≥48dp tap target with the label as its
 * accessibility name (icons always labelled — FR-10.3, PRD §7).
 */
@Composable
fun RailcastBottomBar(
    selected: (Destination) -> Boolean,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RailcastTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        for (dest in Destination.entries) {
            val isOn = selected(dest)
            val label = stringResource(dest.label)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = isOn,
                        role = Role.Tab,
                        onClick = { onSelect(dest) },
                    )
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = dest.icon, fontSize = 18.sp)
                Text(
                    text = label,
                    color = if (isOn) colors.brand else colors.ink3,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5f.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
