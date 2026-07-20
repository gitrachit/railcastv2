package app.railcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.railcast.R
import app.railcast.core.design.RailcastIcons
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.Spacing
import app.railcast.core.design.reflowMaxLines
import app.railcast.directory.QueryClassifier
import app.railcast.directory.Station
import app.railcast.directory.Train
import app.railcast.feature.find.FindMode
import app.railcast.feature.find.FindViewModel
import app.railcast.feature.plan.PlanScreen
import app.railcast.feature.plan.PlanViewModel
import app.railcast.feature.station.StationScreen
import app.railcast.feature.station.StationViewModel

/**
 * Find (PRD §7, amended July 2026) — one destination, one input.
 *
 * Station and Plan were two of five tabs. They are not two intents: both are
 * the user looking for a train they have no relationship with, one by place and
 * one by route. And the user should never have to declare which — the shape of
 * what they type decides it (wireframe W8, [QueryClassifier]).
 *
 * That is why there is no mode toggle here any more. Toggles are where errors
 * live: picking the wrong one turns a valid query into an error that blames the
 * user for the app's filing system.
 */
@Composable
fun FindScreen(
    find: FindViewModel,
    station: StationViewModel,
    plan: PlanViewModel,
    onOpenTrain: (String) -> Unit,
    onOpenPnr: () -> Unit,
    onAlternatives: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by find.state.collectAsState()
    val colors = RailcastTheme.colors

    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = find::onQueryChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.find_hint)) },
                leadingIcon = {
                    Icon(
                        RailcastIcons.Search,
                        contentDescription = null,
                        tint = colors.ink3,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            // Guidance while typing, never an error after submitting (FR-1.5).
            state.hint?.let { key ->
                Text(
                    stringResource(hintRes(key)),
                    color = colors.amber,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        when {
            // A PNR is recognised by shape and handed straight to the PNR
            // screen — the user never had to say "this is a PNR".
            state.intent is QueryClassifier.Intent.Pnr -> {
                ActionRow(stringResource(R.string.find_open_pnr)) {
                    find.clear()
                    onOpenPnr()
                }
            }
            // A route needs the planner, not a directory lookup.
            state.intent is QueryClassifier.Intent.Route || state.mode == FindMode.PLAN -> {
                PlanScreen(plan, modifier = Modifier.fillMaxSize())
            }
            state.mode == FindMode.STATION -> {
                StationScreen(
                    station = station,
                    onAlternatives = onAlternatives,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            state.query.isBlank() -> ZeroState(
                onNearMe = { find.showStation() },
                onPlan = { find.showPlan() },
            )
            else -> ResultList(
                results = state.results,
                onTrain = { no -> find.clear(); onOpenTrain(no) },
                onStation = { code -> station.open(code); find.showStation() },
            )
        }
    }
}

/**
 * The two intents an empty field cannot express. Everything else is typed;
 * these need a tap because "near me" is a sensor question and "plan a trip"
 * is a form.
 */
@Composable
private fun ZeroState(onNearMe: () -> Unit, onPlan: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ActionRow(stringResource(R.string.station_near_me), onNearMe)
        ActionRow(stringResource(R.string.find_mode_plan), onPlan)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(colors.brandSoft)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.brand)
    }
}

@Composable
private fun ResultList(
    results: List<app.railcast.directory.SearchResult>,
    onTrain: (String) -> Unit,
    onStation: (String) -> Unit,
) {
    val colors = RailcastTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        items(results, key = { it.entry.query + "|" + it.entry.label }) { result ->
            val entry = result.entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        when (entry) {
                            is Train -> onTrain(entry.query)
                            is Station -> onStation(entry.code)
                            else -> Unit
                        }
                    }
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                        maxLines = reflowMaxLines(),
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entry.subtitle,
                        fontSize = 13.sp,
                        color = colors.ink2,
                        maxLines = reflowMaxLines(),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    stringResource(
                        if (entry is Station) R.string.search_result_station
                        else R.string.search_result_train,
                    ),
                    fontSize = 12.sp,
                    color = colors.ink3,
                )
            }
        }
    }
}

private fun hintRes(key: String): Int = when (key) {
    app.railcast.directory.FormatValidation.Msg.TRAIN_LENGTH -> R.string.validation_train_length
    app.railcast.directory.FormatValidation.Msg.PNR_LENGTH -> R.string.validation_pnr_length
    else -> R.string.validation_digits_only
}
