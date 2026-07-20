package app.railcast.feature.plan

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.railcast.R
import app.railcast.core.design.Confidence
import app.railcast.core.design.ConfidenceValue
import app.railcast.core.design.RailcastIcons
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.monoNumerals
import app.railcast.core.format.IsoTime
import app.railcast.core.net.AvailabilityCell
import app.railcast.core.net.FareCell
import app.railcast.core.net.PlanRow
import app.railcast.core.net.RowFareBreakdown
import app.railcast.directory.Station
import app.railcast.ui.EmptyState
import app.railcast.ui.ErrorState

/**
 * Plan (backlog 4.7, FR-6.1–6.3). Pick from/to + date + quota → the train list
 * loads fast and each row's seats/fare fill in as they arrive (never blocking).
 * Sort by departure/price/seats; expand a row for the full fare breakdown.
 */
@Composable
fun PlanScreen(plan: PlanViewModel, modifier: Modifier = Modifier) {
    val state by plan.state.collectAsState()
    val colors = RailcastTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { Text(stringResource(R.string.nav_plan), style = MaterialTheme.typography.headlineLarge, color = colors.ink) }

        item {
            OutlinedTextField(
                value = state.fromQuery, onValueChange = plan::onFromQuery, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.plan_from_hint)) },
            )
        }
        items(state.fromResults, key = { "from-" + it.code }) { s -> Suggestion(s) { plan.selectFrom(s) } }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SwapButton(onClick = plan::swap)
            }
        }

        item {
            OutlinedTextField(
                value = state.toQuery, onValueChange = plan::onToQuery, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.plan_to_hint)) },
            )
        }
        items(state.toResults, key = { "to-" + it.code }) { s -> Suggestion(s) { plan.selectTo(s) } }

        item { DateStepper(state.date, plan::stepDate) }
        item { QuotaRow(state.quota, plan::setQuota) }
        if (state.quota.isTatkal) {
            item { Text(stringResource(R.string.plan_tatkal_hint), fontSize = 12.sp, color = colors.amber) }
        }
        item {
            Button(
                onClick = plan::search,
                enabled = state.canSearch,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.brand),
            ) { Text(stringResource(R.string.plan_search), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }

        val res = state.resource
        if (res != null) {
            val rows = state.visibleRows
            if (rows.isEmpty() && res.loading.not()) {
                // Error (no cached rows) → retry; genuinely empty → directive.
                if (res.error != null && res.value == null) {
                    item { ErrorState(onRetry = plan::retry, detail = res.error.let { "${it.code}: ${it.message}" }) }
                } else {
                    item { EmptyState(stringResource(R.string.plan_no_trains)) }
                }
            } else {
                item { SortRow(state.sort, plan::setSort) }
                items(rows, key = { it.no }) { row ->
                    // Offer the Tatkal reminder only for a Tatkal search whose
                    // band hasn't opened yet (FR-6.4) — never after the fact.
                    val band = TatkalTiming.bandFor(row.classes)
                    val offerRemind = state.quota.isTatkal &&
                        !TatkalTiming.isOpen(state.date, band, System.currentTimeMillis())
                    PlanRowCard(
                        row,
                        expanded = state.expanded == row.no,
                        onToggle = { plan.toggleExpand(row.no) },
                        tatkal = if (!offerRemind) null else TatkalChipState(
                            reminded = row.no in state.tatkalReminded,
                            failed = row.no in state.tatkalFailed,
                            onRemind = { plan.remindTatkal(row) },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Suggestion(station: Station, onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .background(colors.surface2).heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${station.name} · ${station.code}", fontSize = 14.sp, color = colors.ink)
    }
}

@Composable
private fun DateStepper(date: String, onStep: (Int) -> Unit) {
    val colors = RailcastTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StepArrow("‹", stringResource(R.string.plan_prev_day)) { onStep(-1) }
        Text(
            IsoTime.friendlyDate(date),
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        StepArrow("›", stringResource(R.string.plan_next_day)) { onStep(1) }
    }
}

@Composable
private fun StepArrow(glyph: String, label: String, onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    Text(
        glyph, fontSize = 22.sp, color = colors.brand, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick).size(48.dp)
            .background(colors.brandSoft).padding(12.dp)
            .semantics { contentDescription = label; role = Role.Button },
    )
}

@Composable
private fun QuotaRow(current: PlanQuota, onSelect: (PlanQuota) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (q in PlanQuota.entries) {
            Chip(stringResource(quotaLabel(q)), q == current) { onSelect(q) }
        }
    }
}

@Composable
private fun SortRow(current: PlanSort, onSelect: (PlanSort) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.plan_sort_by), fontSize = 12.sp, color = RailcastTheme.colors.ink2)
        Chip(stringResource(R.string.plan_sort_departure), current == PlanSort.DEPARTURE) { onSelect(PlanSort.DEPARTURE) }
        Chip(stringResource(R.string.plan_sort_price), current == PlanSort.PRICE) { onSelect(PlanSort.PRICE) }
        Chip(stringResource(R.string.plan_sort_seats), current == PlanSort.SEATS) { onSelect(PlanSort.SEATS) }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    Text(
        text = label, fontSize = 13.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        color = if (on) colors.brand else colors.ink2,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).semantics { role = Role.Tab }
            .clickable(onClick = onClick).background(if (on) colors.brandSoft else colors.surface2)
            .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** Per-row Tatkal-reminder chip state (null = don't offer). */
data class TatkalChipState(val reminded: Boolean, val failed: Boolean, val onRemind: () -> Unit)

@Composable
private fun PlanRowCard(row: PlanRow, expanded: Boolean, onToggle: () -> Unit, tatkal: TatkalChipState? = null) {
    val colors = RailcastTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle)
            .background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${row.name} · ${row.no}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                Text(
                    monoNumerals("${IsoTime.clock(row.dep)} – ${IsoTime.clock(row.arr)}  ·  ${durationText(row.durationMin)}"),
                    fontSize = 12.sp, color = colors.ink2, maxLines = 1,
                )
                Text(runsOnText(row.runsOn), fontSize = 11.sp, color = colors.ink3)
            }
            Column(horizontalAlignment = Alignment.End) {
                AvailabilityText(row.availability)
                FareText(row.fare)
            }
        }
        tatkal?.let { chip ->
            val chipColor = when {
                chip.reminded -> colors.green
                chip.failed -> colors.amber
                else -> colors.brand
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(enabled = !chip.reminded, onClick = chip.onRemind)
                    .background(if (chip.reminded) colors.greenSoft else colors.brandSoft)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!chip.failed) {
                    Icon(
                        imageVector = if (chip.reminded) RailcastIcons.Check else RailcastIcons.Bell,
                        contentDescription = null, // label carries the meaning (FR-10.3)
                        tint = chipColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = when {
                        chip.reminded -> stringResource(R.string.plan_tatkal_reminded)
                        chip.failed -> stringResource(R.string.plan_tatkal_failed)
                        else -> stringResource(R.string.plan_tatkal_remind)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = chipColor,
                )
            }
        }
        if (expanded) {
            val fare = row.fare
            if (fare is FareCell.Ready) FareBreakdown(fare.value.breakdown, fare.value.total)
            else Text(stringResource(R.string.plan_checking), fontSize = 12.sp, color = colors.ink3)
        }
    }
}

@Composable
private fun AvailabilityText(cell: AvailabilityCell) {
    val colors = RailcastTheme.colors
    when (cell) {
        AvailabilityCell.Pending -> Text(stringResource(R.string.plan_checking), fontSize = 12.sp, color = colors.ink3)
        is AvailabilityCell.Ready -> {
            val a = cell.value
            val color = when (a.status) {
                "available" -> colors.green
                "rac" -> colors.amber
                else -> colors.red
            }
            Text(a.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            // A confirmation chance is a PREDICTION, not an observed fact, and
            // upstream gives us no basis to cite — so it renders as ESTIMATED:
            // `~` prefix, dashed edge, and "estimated ..." to TalkBack (FR-11.1).
            a.predictionPct?.let {
                ConfidenceValue(
                    value = stringResource(R.string.plan_confirm_chance, it),
                    confidence = Confidence.ESTIMATED,
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun FareText(cell: FareCell) {
    val colors = RailcastTheme.colors
    when (cell) {
        FareCell.Pending -> Unit
        is FareCell.Ready -> Text(stringResource(R.string.plan_fare_total, cell.value.total.toInt().toString()), fontSize = 13.sp, color = colors.ink)
    }
}

@Composable
private fun FareBreakdown(b: RowFareBreakdown, total: Double) {
    val colors = RailcastTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
        FareLine(stringResource(R.string.plan_fare_base), b.base)
        FareLine(stringResource(R.string.plan_fare_reservation), b.reservation)
        FareLine(stringResource(R.string.plan_fare_superfast), b.superfast)
        if (b.tatkal > 0) FareLine(stringResource(R.string.plan_fare_tatkal), b.tatkal)
        FareLine(stringResource(R.string.plan_fare_gst), b.gst)
        if (b.dynamic > 0) FareLine(stringResource(R.string.plan_fare_dynamic), b.dynamic)
        if (b.other > 0) FareLine(stringResource(R.string.plan_fare_other), b.other)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.plan_fare_total_label), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            Text(stringResource(R.string.plan_fare_total, total.toInt().toString()), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.ink)
        }
    }
}

@Composable
private fun FareLine(label: String, amount: Double) {
    val colors = RailcastTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = colors.ink2)
        Text(stringResource(R.string.plan_fare_total, amount.toInt().toString()), fontSize = 12.sp, color = colors.ink2)
    }
}

@Composable
private fun durationText(mins: Int): String = stringResource(R.string.plan_duration, mins / 60, mins % 60)

@Composable
private fun runsOnText(runsOn: List<Boolean>): String {
    // Unambiguous day names (Mon, Thu) instead of single letters (M, S T),
    // and "Runs daily" when every day is set.
    val names = stringResource(R.string.plan_day_names).split(",")
    val active = runsOn.mapIndexedNotNull { i, on -> if (on) names.getOrNull(i) else null }
    return when {
        active.isEmpty() -> stringResource(R.string.plan_runs_on, "—")
        active.size >= 7 -> stringResource(R.string.plan_runs_daily)
        else -> stringResource(R.string.plan_runs_on, active.joinToString(", "))
    }
}

@Composable
private fun SwapButton(onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    val label = stringResource(R.string.plan_swap)
    Icon(
        imageVector = RailcastIcons.SwapVert,
        contentDescription = label,
        tint = colors.brand,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .background(colors.brandSoft)
            .size(44.dp)
            .padding(11.dp)
            .semantics { role = Role.Button },
    )
}

private fun quotaLabel(q: PlanQuota): Int = when (q) {
    PlanQuota.GENERAL -> R.string.plan_quota_general
    PlanQuota.TATKAL -> R.string.plan_quota_tatkal
    PlanQuota.LADIES -> R.string.plan_quota_ladies
    PlanQuota.SENIOR -> R.string.plan_quota_senior
}
