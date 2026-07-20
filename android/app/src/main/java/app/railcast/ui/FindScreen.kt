package app.railcast.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.railcast.R
import app.railcast.feature.plan.PlanScreen
import app.railcast.feature.plan.PlanViewModel
import app.railcast.feature.station.StationScreen
import app.railcast.feature.station.StationViewModel

/**
 * Find (PRD §7, amended July 2026) — one destination for "show me something I
 * don't have yet".
 *
 * Station and Plan used to be two of five tabs. They are not two intents: both
 * are the user looking for a train they have no relationship with, one by place
 * and one by route. Collapsing them frees a third of the bottom bar, which is
 * what lets tab labels stay legible at 200% text (FR-10.3).
 *
 * The mode is `rememberSaveable`, so returning to this tab — or rotating —
 * lands the user back where they were rather than silently resetting their
 * work. Both screens keep their own ViewModel state regardless, so switching
 * modes never discards a search.
 *
 * The wireframed omni-input (one field that classifies train / PNR / station /
 * route by shape) is the intended end state and is NOT built here: it needs the
 * directory classifier that Track and Station currently each own, and folding
 * those together is its own change. This consolidates the destination without
 * pretending the classifier exists.
 */
@Composable
fun FindScreen(
    station: StationViewModel,
    plan: PlanViewModel,
    onAlternatives: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(
        stringResource(R.string.find_mode_station),
        stringResource(R.string.find_mode_plan),
    )

    Column(modifier.fillMaxSize()) {
        SegmentedControl(
            options = labels,
            selectedIndex = mode,
            onSelect = { mode = it },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        when (mode) {
            0 -> StationScreen(station = station, onAlternatives = onAlternatives, modifier = Modifier.fillMaxSize())
            else -> PlanScreen(plan, modifier = Modifier.fillMaxSize())
        }
    }
}
