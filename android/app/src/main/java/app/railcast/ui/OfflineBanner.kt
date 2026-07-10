package app.railcast.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.railcast.R
import app.railcast.core.design.RailcastTheme

/**
 * The offline strip (FR-9.1). Shown while the device is offline; screens keep
 * rendering their last cached data underneath (never a blank error). Icon + word
 * so it reads without relying on colour (FR-10.2).
 */
@Composable
fun OfflineBanner(visible: Boolean, modifier: Modifier = Modifier) {
    val colors = RailcastTheme.colors
    AnimatedVisibility(visible = visible, modifier = modifier) {
        val label = stringResource(R.string.offline_banner)
        Row(
            modifier = Modifier.fillMaxWidth().background(colors.amberSoft).padding(horizontal = 16.dp, vertical = 8.dp)
                .clearAndSetSemantics { contentDescription = label },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("⚡", fontSize = 13.sp)
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.amber)
        }
    }
}
