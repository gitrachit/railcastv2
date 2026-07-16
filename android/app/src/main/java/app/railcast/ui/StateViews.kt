package app.railcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.railcast.R
import app.railcast.core.design.RailcastIcons
import app.railcast.core.design.RailcastTheme

/**
 * Shared "every state designed" surfaces (PRD §7): a plain-language error with a
 * next step (retry), and a directive empty state. Screens keep their skeletons
 * for loading and their cached data for offline; these two close the error/empty
 * gaps consistently.
 */
@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    val colors = RailcastTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface2).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(RailcastIcons.Warning, contentDescription = null, tint = colors.amber, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.state_error_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.ink)
        Text(stringResource(R.string.state_error_body), fontSize = 13.sp, color = colors.ink2)
        // The server error code / network failure reason. Not localized —
        // diagnostic detail, shown small so support/screenshots can pinpoint
        // the cause instead of everyone guessing behind "couldn't load".
        if (detail != null) {
            Text(detail, fontSize = 11.sp, color = colors.ink3, textAlign = TextAlign.Center)
        }
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_retry), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 14.sp, color = RailcastTheme.colors.ink2, modifier = modifier.padding(8.dp))
}
