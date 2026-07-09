package app.railcast.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.railcast.R
import app.railcast.core.design.BoardHero
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.StatusLevel

// Home shows a board-hero demo until the real search + saved cards land (4.2).
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_greeting),
            style = MaterialTheme.typography.displaySmall,
            color = RailcastTheme.colors.ink,
        )
        BoardHero(
            title = stringResource(R.string.home_demo_train),
            answer = stringResource(R.string.home_demo_status),
            answerIcon = "▶",
            level = StatusLevel.GOOD,
            freshness = stringResource(R.string.freshness_demo),
        )
    }
}
