package app.railcast.feature.station

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.railcast.R
import app.railcast.ui.PlaceholderScreen

@Composable
fun StationScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_station),
        subtitle = stringResource(R.string.placeholder_station),
        modifier = modifier,
    )
}
