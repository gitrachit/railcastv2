package app.railcast.feature.track

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.railcast.R
import app.railcast.core.data.Resource
import app.railcast.core.design.BoardHero
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.trainStatusVisual
import app.railcast.core.net.RouteStop
import app.railcast.core.net.TrainScreen
import app.railcast.directory.SearchResult
import app.railcast.directory.Station
import app.railcast.directory.Train

/**
 * Track (backlog 4.3, FR-2.x). Search a train, then a live board + timeline with
 * day labels + an interpolated position honestly labelled "estimated". A
 * cancelled run takes over the screen with a one-tap route to alternatives; a
 * diverted/rescheduled run shows an amber banner (FR-2.4). Run date is a
 * today/yesterday choice, never a raw date (FR-2.3).
 */
@Composable
fun TrackScreen(
    track: TrackViewModel,
    onAlternatives: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by track.state.collectAsState()
    if (state.trainNo == null) {
        TrackSearch(state.query, state.results, track::onQueryChange, track::open, modifier)
    } else {
        TrackContent(state, track, onAlternatives, modifier)
    }
}

@Composable
private fun TrackSearch(
    query: String,
    results: List<SearchResult>,
    onQueryChange: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    val colors = RailcastTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        item {
            Text(stringResource(R.string.nav_track), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.ink)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Text("🔎") },
            )
        }
        items(results, key = { it.entry.query + "|" + it.entry.label }) { result ->
            val entry = result.entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    // Only trains are trackable; station results wait for 4.6.
                    .then(if (entry is Train) Modifier.clickable { onOpen(entry.query) } else Modifier)
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                    Text(entry.subtitle, fontSize = 13.sp, color = colors.ink2)
                }
                Text(
                    stringResource(if (entry is Station) R.string.search_result_station else R.string.search_result_train),
                    fontSize = 12.sp,
                    color = colors.ink3,
                )
            }
        }
    }
}

@Composable
private fun TrackContent(
    state: TrackUiState,
    track: TrackViewModel,
    onAlternatives: () -> Unit,
    modifier: Modifier,
) {
    val colors = RailcastTheme.colors
    val resource = state.resource
    val screen = resource?.value
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { TrackHeader(screen?.name, screen?.trainNo ?: state.trainNo, onBack = track::back) }

        if (screen == null) {
            item {
                Text(
                    stringResource(R.string.home_card_loading, state.trainNo.orEmpty()),
                    color = colors.ink2,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(colors.surface2).heightIn(min = 72.dp).padding(20.dp),
                )
            }
            return@LazyColumn
        }

        if (screen.status.state == "cancelled") {
            item { CancelledState(screen.status.summary, onAlternatives) }
            return@LazyColumn
        }

        item {
            val visual = trainStatusVisual(screen.status.state, screen.status.delayMin)
            BoardHero(
                title = "${screen.name} · ${screen.trainNo}",
                answer = screen.status.summary,
                answerIcon = visual.icon,
                level = visual.level,
                freshness = freshnessLabel(resource),
            )
        }

        if (screen.status.state == "diverted" || screen.status.state == "rescheduled") {
            item { AmberBanner(screen.status.summary) }
        }

        if (RunDateSheet.hasChoice(screen)) {
            item { RunDatePanel(screen, state.showRunSheet, track) }
        }

        screen.position?.let { pos ->
            item { EstimatedPosition(pos.betweenCodes.getOrNull(0), pos.betweenCodes.getOrNull(1), pos.progress) }
        }

        item {
            Text(stringResource(R.string.track_timeline), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.ink2)
        }
        timeline(screen.route)
    }
}

@Composable
private fun TrackHeader(name: String?, trainNo: String?, onBack: () -> Unit) {
    val colors = RailcastTheme.colors
    val backLabel = stringResource(R.string.action_back)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "←",
            fontSize = 22.sp,
            color = colors.ink,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onBack)
                .size(48.dp)
                .padding(12.dp)
                .semantics { contentDescription = backLabel; role = Role.Button },
        )
        Text(
            text = if (name != null) "$name · $trainNo" else trainNo.orEmpty(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
        )
    }
}

private fun LazyListScope.timeline(route: List<RouteStop>) {
    var lastDay = -1
    for (stop in route) {
        if (stop.day != lastDay) {
            lastDay = stop.day
            item(key = "day-${stop.day}") { DayHeader(stop.day) }
        }
        item(key = "stop-${stop.code}-${stop.day}") { StopRow(stop) }
    }
}

@Composable
private fun DayHeader(day: Int) {
    Text(
        text = stringResource(R.string.track_day, day),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = RailcastTheme.colors.ink3,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun StopRow(stop: RouteStop) {
    val colors = RailcastTheme.colors
    val isNext = stop.state == "next"
    val sched = listOfNotNull(stop.scheduled.arr, stop.scheduled.dep).firstOrNull()
    val actual = listOfNotNull(stop.actual.arr, stop.actual.dep).firstOrNull()
    val platformText = stop.platform?.let { stringResource(R.string.track_platform, it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isNext) colors.brandSoft else colors.surface)
            .border(1.dp, if (isNext) colors.brand else colors.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stop.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(
                text = buildString {
                    if (sched != null) append(sched)
                    if (actual != null && actual != sched) append("  →  $actual")
                    if (platformText != null) append("   $platformText")
                },
                fontSize = 12.sp,
                color = colors.ink2,
            )
        }
        DelayTag(stop.delayMin)
    }
}

@Composable
private fun DelayTag(delayMin: Int?) {
    val colors = RailcastTheme.colors
    when {
        delayMin == null -> Unit
        delayMin <= 0 -> Text(stringResource(R.string.track_on_time), fontSize = 12.sp, color = colors.green, fontWeight = FontWeight.Bold)
        else -> Text(
            stringResource(R.string.track_delay_late, delayMin),
            fontSize = 12.sp,
            color = if (delayMin > 15) colors.red else colors.amber,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EstimatedPosition(from: String?, to: String?, progress: Double) {
    val colors = RailcastTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface2).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Honesty: interpolated, never implied as GPS (FR-2.2, invariant).
        Text(stringResource(R.string.track_estimated_position), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.ink2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(from.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            ProgressLine(progress.coerceIn(0.0, 1.0), Modifier.weight(1f))
            Text(to.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
        }
    }
}

@Composable
private fun ProgressLine(progress: Double, modifier: Modifier) {
    val colors = RailcastTheme.colors
    Box(modifier.height(6.dp).clip(RoundedCornerShape(999.dp)).background(colors.line)) {
        Box(Modifier.fillMaxWidth(progress.toFloat()).height(6.dp).clip(RoundedCornerShape(999.dp)).background(colors.brand))
    }
}

@Composable
private fun RunDatePanel(screen: TrainScreen, expanded: Boolean, track: TrackViewModel) {
    val colors = RailcastTheme.colors
    val options = RunDateSheet.options(screen)
    val current = options.firstOrNull { it.selected }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(runLabelRes(current?.label)),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.brand,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { if (expanded) track.dismissRunSheet() else track.openRunSheet() }
                .background(colors.brandSoft)
                .heightIn(min = 40.dp)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
        if (expanded) {
            Text(stringResource(R.string.track_run_sheet_title), fontSize = 12.sp, color = colors.ink2)
            for (opt in options) {
                Text(
                    text = stringResource(runLabelRes(opt.label)),
                    fontSize = 15.sp,
                    fontWeight = if (opt.selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (opt.selected) colors.brand else colors.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { track.selectRun(opt.runDate) }
                        .background(if (opt.selected) colors.brandSoft else colors.surface2)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AmberBanner(text: String) {
    val colors = RailcastTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.amberSoft).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("⚠", fontSize = 15.sp)
        Text(text, fontSize = 14.sp, color = colors.amber, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CancelledState(summary: String, onAlternatives: () -> Unit) {
    val colors = RailcastTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("✕", fontSize = 40.sp, color = colors.red)
        Text(stringResource(R.string.track_cancelled_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.red)
        Text(summary, fontSize = 15.sp, color = colors.ink2)
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onAlternatives,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brand),
        ) {
            Text(stringResource(R.string.track_alternatives_cta), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun freshnessLabel(resource: Resource<TrainScreen>): String {
    val base = resource.freshness ?: stringResource(R.string.freshness_demo)
    return if (resource.stale) stringResource(R.string.freshness_offline, base) else base
}

@StringRes
private fun runLabelRes(label: RunLabel?): Int =
    if (label == RunLabel.YESTERDAY) R.string.run_yesterday else R.string.run_today
