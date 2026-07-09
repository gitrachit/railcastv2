package app.railcast.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.railcast.core.design.RailcastTheme

/**
 * Shared placeholder for the five tabs until each feature lands (backlog
 * 4.x). Renders the answer-first skeleton's title so the shell is navigable
 * and the design tokens are visible end-to-end.
 */
@Composable
fun PlaceholderScreen(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val colors = RailcastTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.displaySmall, color = colors.ink)
        Text(text = subtitle, color = colors.ink2)
    }
}
