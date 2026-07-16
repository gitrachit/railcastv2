package app.railcast.feature.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.railcast.R
import app.railcast.core.design.RailcastIcons
import app.railcast.core.design.RailcastTheme

/**
 * One-tap mute-this-journey (FR-7.4), shown on Track and PNR. Muting silences
 * pushes for THIS entity only (NotificationPolicy checks the mute set); the
 * per-type opt-ins in Alerts are untouched. Icon + word, never colour alone.
 */
@Composable
fun MuteJourneyChip(muted: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RailcastTheme.colors
    val color = if (muted) colors.amber else colors.ink2
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onToggle)
            .background(if (muted) colors.amberSoft else colors.surface2)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .semantics { role = Role.Switch },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (muted) RailcastIcons.BellOff else RailcastIcons.Bell,
            contentDescription = null, // the label carries the meaning (FR-10.3)
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(if (muted) R.string.alert_journey_muted else R.string.alert_mute_journey),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
