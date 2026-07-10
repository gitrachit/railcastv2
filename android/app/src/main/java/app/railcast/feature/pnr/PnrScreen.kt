package app.railcast.feature.pnr

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import app.railcast.R
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.StatusChip
import app.railcast.core.design.trainStatusVisual
import app.railcast.core.net.PnrPassenger
import app.railcast.core.net.PnrScreen as PnrScreenModel
import app.railcast.ui.ErrorState

/**
 * PNR screen (backlog 4.5). The PNR is masked everywhere it appears (FR-4.3):
 * the header shows the server's masked form, and "save" creates a server-side
 * chart watch for the FR-4.2 push. A plain-language privacy note is linked here.
 */
@Composable
fun PnrScreen(pnr: PnrViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by pnr.state.collectAsState()
    val screen = state.resource?.value

    // System back must also drop the in-memory PNR and stop its poll loop —
    // otherwise the app keeps polling /screen/pnr/<raw> after leaving (FR-4.3).
    BackHandler { pnr.clear(); onBack() }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { Header(state.maskedInput ?: screen?.pnrMasked, onBack = { pnr.clear(); onBack() }) }

        if (screen == null) {
            if (state.maskedInput == null) {
                item { PnrInput(state.input, state.hint, pnr::onInputChange, pnr::lookup) }
                item { PrivacyNote() }
            } else if (state.resource?.error != null && state.resource?.loading == false) {
                item { ErrorState(onRetry = pnr::retry) }
            } else {
                item { LoadingRow() }
            }
            return@LazyColumn
        }

        item {
            AnimatedVisibility(visible = state.chartJustPrepared) {
                CelebrationBanner(onDismiss = pnr::dismissCelebration)
            }
        }
        item { ChartStatus(screen.chart.prepared) }
        item { JourneyCard(screen) }
        screen.live?.let { live ->
            item {
                val v = trainStatusVisual(live.state, live.delayMin)
                StatusChip(icon = v.icon, label = live.summary, level = v.level)
            }
        }
        item { Text(stringResource(R.string.pnr_passengers), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RailcastTheme.colors.ink2) }
        items(screen.passengers, key = { it.idx }) { p -> PassengerRow(p) }
        screen.fare?.let { fare ->
            item { Text(stringResource(R.string.pnr_fare, fare.total.toInt().toString()), fontSize = 14.sp, color = RailcastTheme.colors.ink) }
        }
        item { SaveButton(state.saveState, onSave = pnr::save) }
        item { PrivacyNote() }
    }
}

@Composable
private fun Header(masked: String?, onBack: () -> Unit) {
    val colors = RailcastTheme.colors
    val backLabel = stringResource(R.string.action_back)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "←",
            fontSize = 22.sp,
            color = colors.ink,
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onBack).size(48.dp)
                .padding(12.dp).semantics { contentDescription = backLabel; role = Role.Button },
        )
        Text(
            text = masked ?: stringResource(R.string.pnr_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
        )
    }
}

@Composable
private fun PnrInput(input: String, hint: String?, onChange: (String) -> Unit, onCheck: () -> Unit) {
    val colors = RailcastTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.pnr_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        hint?.let { Text(stringResource(validationRes(it)), color = colors.amber, fontSize = 13.sp) }
        Button(
            onClick = onCheck,
            enabled = input.length == 10,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brand),
        ) {
            Text(stringResource(R.string.pnr_check), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoadingRow() {
    val colors = RailcastTheme.colors
    Text(
        text = stringResource(R.string.pnr_loading),
        color = colors.ink2,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface2)
            .heightIn(min = 64.dp).padding(20.dp),
    )
}

@Composable
private fun CelebrationBanner(onDismiss: () -> Unit) {
    val colors = RailcastTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.greenSoft)
            .clickable(onClick = onDismiss).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎉", fontSize = 22.sp)
        Text(stringResource(R.string.pnr_chart_celebration), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.green)
    }
}

@Composable
private fun ChartStatus(prepared: Boolean) {
    if (prepared) {
        StatusChip(icon = "✓", label = stringResource(R.string.pnr_chart_prepared), level = app.railcast.core.design.StatusLevel.GOOD)
    } else {
        StatusChip(icon = "🕓", label = stringResource(R.string.pnr_chart_waiting), level = app.railcast.core.design.StatusLevel.NEUTRAL)
    }
}

@Composable
private fun JourneyCard(screen: PnrScreenModel) {
    val colors = RailcastTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${screen.train.name} · ${screen.train.no}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
        Text(
            stringResource(R.string.pnr_route, screen.journey.from.name, screen.journey.to.name),
            fontSize = 14.sp,
            color = colors.ink,
        )
        Text(
            "${screen.journey.date} · ${stringResource(R.string.pnr_class_quota, screen.journey.cls, screen.journey.quota)}",
            fontSize = 13.sp,
            color = colors.ink2,
        )
        Text(stringResource(R.string.pnr_boarding, screen.journey.boardingPoint.name), fontSize = 13.sp, color = colors.ink2)
    }
}

@Composable
private fun PassengerRow(p: PnrPassenger) {
    val colors = RailcastTheme.colors
    val berth = listOfNotNull(p.coach, p.berth?.toString(), p.berthType).joinToString(" ")
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pnr_passenger, p.idx), fontSize = 13.sp, color = colors.ink2)
            if (berth.isNotBlank()) Text(berth, fontSize = 13.sp, color = colors.ink)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(p.currentStatus, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            if (p.bookingStatus != p.currentStatus) {
                Text(p.bookingStatus, fontSize = 11.sp, color = colors.ink3)
            }
        }
    }
}

@Composable
private fun SaveButton(saveState: SaveState, onSave: () -> Unit) {
    val colors = RailcastTheme.colors
    val label = when (saveState) {
        SaveState.Idle -> stringResource(R.string.pnr_save)
        SaveState.Saving -> stringResource(R.string.pnr_saving)
        SaveState.Saved -> stringResource(R.string.pnr_saved)
        SaveState.Failed -> stringResource(R.string.pnr_save_failed)
    }
    Button(
        onClick = onSave,
        enabled = saveState == SaveState.Idle || saveState == SaveState.Failed,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (saveState == SaveState.Saved) colors.greenSoft else colors.brand,
        ),
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PrivacyNote() {
    val colors = RailcastTheme.colors
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.pnr_privacy_link),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.brand,
            modifier = Modifier.clickable { open = !open }.heightIn(min = 48.dp).padding(vertical = 10.dp),
        )
        if (open) {
            Text(stringResource(R.string.pnr_privacy_body), fontSize = 12.sp, color = colors.ink2)
        }
    }
}

private fun validationRes(key: String): Int = when (key) {
    "validation_digits_only" -> R.string.validation_digits_only
    else -> R.string.validation_pnr_length
}
