package app.railcast.feature.track

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.railcast.R
import app.railcast.ui.PlaceholderScreen

@Composable
fun TrackScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_track),
        subtitle = stringResource(R.string.placeholder_track),
        modifier = modifier,
    )
}
