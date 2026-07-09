package app.railcast.feature.alerts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.railcast.R
import app.railcast.ui.PlaceholderScreen

@Composable
fun AlertsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_alerts),
        subtitle = stringResource(R.string.placeholder_alerts),
        modifier = modifier,
    )
}
