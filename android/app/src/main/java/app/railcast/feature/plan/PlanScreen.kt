package app.railcast.feature.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.railcast.R
import app.railcast.ui.PlaceholderScreen

@Composable
fun PlanScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_plan),
        subtitle = stringResource(R.string.placeholder_plan),
        modifier = modifier,
    )
}
