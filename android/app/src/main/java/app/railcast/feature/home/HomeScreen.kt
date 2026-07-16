package app.railcast.feature.home

import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import app.railcast.core.design.RailcastIcons
import app.railcast.core.design.Spacing
import app.railcast.core.design.RailcastTheme
import app.railcast.core.design.trainStatusVisual
import app.railcast.core.net.TrainScreen
import app.railcast.directory.FormatValidation
import app.railcast.directory.SearchResult
import app.railcast.directory.Station
import app.railcast.directory.Train
import app.railcast.directory.VoiceSearchContract
import app.railcast.ui.Skeleton

/**
 * Home (backlog 4.2): search by name/number with voice, and saved trains as
 * live cards. Answer-first layout (PRD §7): search entry → results → saved
 * cards, each refreshed by the shared PollController and rendered cached-first
 * so nothing blanks offline (FR-9.1).
 */
@Composable
fun HomeScreen(home: HomeViewModel, onCheckPnr: () -> Unit, modifier: Modifier = Modifier) {
    val state by home.state.collectAsState()
    val context = LocalContext.current
    val voiceLabel = stringResource(R.string.search_voice)
    val voicePrompt = stringResource(R.string.search_voice_prompt)
    val voiceLauncher = rememberLauncherForActivityResult(VoiceSearchContract()) { spoken ->
        if (!spoken.isNullOrBlank()) home.onQueryChange(spoken)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        // Trimmed from a 28sp hero to a calm eyebrow so saved live boards and
        // the search rise up the fold (design review, phase 1).
        item {
            Text(
                text = stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.titleMedium,
                color = RailcastTheme.colors.ink2,
            )
        }
        item {
            SearchField(
                query = state.query,
                onQueryChange = home::onQueryChange,
                voiceLabel = voiceLabel,
                onVoice = { if (VoiceSearchContract.isAvailable(context)) voiceLauncher.launch(voicePrompt) },
            )
        }
        state.validationHint?.let { key ->
            item {
                Text(stringResource(validationRes(key)), color = RailcastTheme.colors.amber, fontSize = 13.sp)
            }
        }

        if (state.query.isNotBlank()) {
            items(state.results, key = { it.entry.query + "|" + it.entry.label }) { result ->
                ResultRow(result, onClick = {
                    when (result.entry) {
                        is Train -> { home.onSaveTrain(result.entry.query); home.onQueryChange("") }
                        is Station -> Unit // Station screen lands in 4.6
                    }
                })
            }
        } else {
            item { CheckPnrRow(onCheckPnr) }
            if (state.saved.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.home_saved_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RailcastTheme.colors.ink2,
                    )
                }
                items(state.saved, key = { it.trainNo }) { card ->
                    SavedCardView(card.trainNo, card.resource, onRemove = { home.onRemoveTrain(card.trainNo) })
                }
            }
        }
    }
}

@Composable
private fun CheckPnrRow(onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(RailcastIcons.Ticket, contentDescription = null, tint = colors.brand, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.home_check_pnr), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    voiceLabel: String,
    onVoice: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            Icon(RailcastIcons.Search, contentDescription = null, tint = RailcastTheme.colors.ink3, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            Icon(
                imageVector = RailcastIcons.Mic,
                // Named via the modifier semantics below (FR-10.3).
                contentDescription = null,
                tint = RailcastTheme.colors.brand,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onVoice)
                    .heightIn(min = 48.dp)
                    .padding(12.dp)
                    .semantics { contentDescription = voiceLabel; role = Role.Button },
            )
        },
    )
}

@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    val colors = RailcastTheme.colors
    val typeLabel = when (result.entry) {
        is Train -> stringResource(R.string.search_result_train)
        is Station -> stringResource(R.string.search_result_station)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(result.entry.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(result.entry.subtitle, fontSize = 13.sp, color = colors.ink2)
        }
        Text(typeLabel, fontSize = 12.sp, color = colors.ink3)
    }
}

@Composable
private fun SavedCardView(trainNo: String, resource: Resource<TrainScreen>?, onRemove: () -> Unit) {
    val screen = resource?.value
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (screen != null) {
            val visual = trainStatusVisual(screen.status.state, screen.status.delayMin)
            BoardHero(
                title = "${screen.name} · ${screen.trainNo}",
                answer = screen.status.summary,
                answerIcon = visual.icon,
                level = visual.level,
                freshness = freshnessLabel(resource),
            )
        } else {
            // No cached value yet — a labelled skeleton, never a blank (FR-9.1).
            Skeleton(label = stringResource(R.string.home_card_loading, trainNo))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
        }
    }
}

@Composable
private fun freshnessLabel(resource: Resource<TrainScreen>): String {
    val base = resource.freshness ?: stringResource(R.string.freshness_demo)
    return if (resource.stale) stringResource(R.string.freshness_offline, base) else base
}

@StringRes
private fun validationRes(key: String): Int = when (key) {
    FormatValidation.Msg.TRAIN_LENGTH -> R.string.validation_train_length
    FormatValidation.Msg.PNR_LENGTH -> R.string.validation_pnr_length
    else -> R.string.validation_digits_only
}
