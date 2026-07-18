package app.railcast.feature.station

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
import app.railcast.core.design.Radius
import app.railcast.core.design.RailcastIcons
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.monoNumerals
import app.railcast.core.format.IsoTime
import app.railcast.core.design.StatusChip
import app.railcast.core.net.StationTrain
import app.railcast.directory.SearchResult
import app.railcast.directory.Station
import app.railcast.ui.EmptyState
import app.railcast.ui.ErrorState
import app.railcast.ui.Skeleton
import app.railcast.ui.freshnessLabel

/**
 * Station board (backlog 4.6, FR-5.1). Search a station → live arrivals/
 * departures over a 2/4/8-hr window with delay, platform, destination and
 * cancelled state; destination/class/on-time filters. A cancelled row hands off
 * to Plan for alternatives.
 */
@Composable
fun StationScreen(station: StationViewModel, onAlternatives: () -> Unit, modifier: Modifier = Modifier) {
    val state by station.state.collectAsState()
    if (state.code == null) {
        StationSearch(state, station, modifier)
    } else {
        StationBoard(state, station, onAlternatives, modifier)
    }
}

@Composable
private fun StationSearch(
    state: StationUiState,
    vm: StationViewModel,
    modifier: Modifier,
) {
    val colors = RailcastTheme.colors
    val context = LocalContext.current
    // Location permission asked in context — the tap on "near me" IS the reason
    // (FR-5.2 "permission asked in context, with reason").
    val locate = {
        LocationResolver.resolve(context) { loc ->
            if (loc != null) vm.onLocation(loc.latitude, loc.longitude) else vm.onLocationUnavailable()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else vm.onLocationUnavailable()
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        item { Text(stringResource(R.string.nav_station), style = MaterialTheme.typography.headlineLarge, color = colors.ink) }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.station_search_hint)) },
                leadingIcon = {
                    Icon(RailcastIcons.Search, contentDescription = null, tint = RailcastTheme.colors.ink3, modifier = Modifier.size(20.dp))
                },
            )
        }
        item {
            NearMeRow {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    locate()
                } else {
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }
        if (state.locationFailed) {
            item { Text(stringResource(R.string.station_location_failed), fontSize = 13.sp, color = colors.amber) }
        }
        if (state.nearby.isNotEmpty()) {
            item {
                Text(stringResource(R.string.station_nearby_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.ink2)
            }
            items(state.nearby, key = { "near-" + it.code }) { s -> NearbyRow(s) { vm.open(s.code) } }
        }
        items(state.results, key = { it.entry.query + "|" + it.entry.label }) { result ->
            val entry = result.entry
            // Only stations open a board here.
            if (entry is Station) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { vm.open(entry.code) }.background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                        .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                        Text(entry.subtitle, fontSize = 13.sp, color = colors.ink2)
                    }
                    Text(entry.code, fontSize = 12.sp, color = colors.ink3)
                }
            }
        }
    }
}

@Composable
private fun NearMeRow(onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .background(colors.brandSoft).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(RailcastIcons.Place, contentDescription = null, tint = colors.brand, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.station_near_me),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.brand,
        )
    }
}

@Composable
private fun NearbyRow(station: Station, onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(station.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(station.subtitle, fontSize = 13.sp, color = colors.ink2)
        }
        Text(station.code, fontSize = 12.sp, color = colors.ink3)
    }
}

@Composable
private fun StationBoard(
    state: StationUiState,
    vm: StationViewModel,
    onAlternatives: () -> Unit,
    modifier: Modifier,
) {
    val colors = RailcastTheme.colors
    val screen = state.resource?.value
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { Header(screen?.station?.name ?: state.code.orEmpty(), onBack = vm::back) }
        state.resource?.let { res ->
            if (res.value != null) {
                item {
                    Text(
                        freshnessLabel(res.freshness, res.stale),
                        fontSize = 11.sp, color = colors.ink3,
                    )
                }
            }
        }
        item { WindowToggle(state.windowHrs, vm::setWindow) }
        item { Filters(state, vm) }

        if (screen == null) {
            item {
                if (state.resource?.error != null && state.resource?.loading == false) {
                    ErrorState(onRetry = vm::retry, detail = state.resource?.error?.let { "${it.code}: ${it.message}" })
                } else {
                    Skeleton(label = stringResource(R.string.station_loading), corner = Radius.md, height = 64.dp)
                }
            }
            return@LazyColumn
        }

        val trains = state.visibleTrains
        if (trains.isEmpty()) {
            item { EmptyState(stringResource(R.string.station_no_trains)) }
        } else {
            items(trains, key = { it.no + "|" + (it.departure?.scheduled ?: it.arrival?.scheduled) }) { t ->
                TrainRow(t, onAlternatives)
            }
        }
    }
}

@Composable
private fun Header(name: String, onBack: () -> Unit) {
    val colors = RailcastTheme.colors
    val backLabel = stringResource(R.string.action_back)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "←", fontSize = 22.sp, color = colors.ink,
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onBack).size(48.dp)
                .padding(12.dp).semantics { contentDescription = backLabel; role = Role.Button },
        )
        Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.ink)
    }
}

@Composable
private fun WindowToggle(current: Int, onSelect: (Int) -> Unit) {
    val colors = RailcastTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (hrs in listOf(2, 4, 8)) {
            val on = hrs == current
            Text(
                text = stringResource(R.string.station_window_hours, hrs),
                fontSize = 14.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) colors.brand else colors.ink2,
                modifier = Modifier.clip(RoundedCornerShape(999.dp))
                    .selectableChip()
                    .clickable { onSelect(hrs) }
                    .background(if (on) colors.brandSoft else colors.surface2)
                    .heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun Filters(state: StationUiState, vm: StationViewModel) {
    val colors = RailcastTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(stringResource(R.string.station_filter_ontime), state.filters.onTimeOnly) { vm.toggleOnTimeOnly() }
            for (cls in state.availableClasses) {
                FilterChip(cls, state.filters.cls == cls) { vm.setClassFilter(if (state.filters.cls == cls) null else cls) }
            }
        }
        OutlinedTextField(
            value = state.filters.dest,
            onValueChange = vm::setDestFilter,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.station_dest_filter)) },
            // A leading glyph so this reads as a filter, not a mystery empty box.
            leadingIcon = {
                Icon(RailcastIcons.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(20.dp))
            },
        )
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        color = if (on) colors.brand else colors.ink2,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).selectableChip().clickable(onClick = onClick)
            .background(if (on) colors.brandSoft else colors.surface2)
            .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

private fun Modifier.selectableChip(): Modifier = this.semantics { role = Role.Tab }

@Composable
private fun TrainRow(t: StationTrain, onAlternatives: () -> Unit) {
    val colors = RailcastTheme.colors
    val cancelled = t.status == "cancelled"
    val time = t.departure ?: t.arrival
    val (level, icon) = stationStatusVisual(t.status, time?.delayMin)
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)
            .border(1.dp, if (cancelled) colors.red else colors.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${t.name} · ${t.no}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                Text("${t.source.name} → ${t.dest.name}", fontSize = 12.sp, color = colors.ink2)
                val line = buildString {
                    if (time != null) {
                        append(IsoTime.clock(time.scheduled))
                        val actual = IsoTime.clock(time.actual)
                        if (actual.isNotEmpty() && actual != IsoTime.clock(time.scheduled)) append("  →  $actual")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (line.isNotBlank()) Text(monoNumerals(line), fontSize = 12.sp, color = colors.ink2, maxLines = 1)
                    // maxLines=1 + weight-free short strings: the platform label can
                    // never be crushed into a one-char-per-line column again.
                    t.platform?.let {
                        Text(
                            stringResource(R.string.track_platform, it),
                            fontSize = 12.sp, color = colors.ink3, maxLines = 1,
                        )
                    }
                }
            }
            StatusChip(icon = icon, label = statusWord(t.status, time?.delayMin), level = level)
        }
        if (cancelled) {
            TextButton(onClick = onAlternatives, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.station_cancelled_cta), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun statusWord(status: String, delayMin: Int?): String = when (status) {
    "cancelled" -> stringResource(R.string.track_cancelled_title)
    "ontime" -> stringResource(R.string.track_on_time)
    "late" -> stringResource(R.string.track_delay_late, delayMin ?: 0)
    else -> status
}
