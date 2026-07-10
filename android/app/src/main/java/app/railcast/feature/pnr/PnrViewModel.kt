package app.railcast.feature.pnr

import app.railcast.core.data.Resource
import app.railcast.core.net.PnrScreen
import app.railcast.core.poll.PollController
import app.railcast.directory.FormatValidation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveState { Idle, Saving, Saved, Failed }

data class PnrUiState(
    val input: String = "",
    val hint: String? = null, // FormatValidation message key, or null
    val maskedInput: String? = null, // masked echo once a lookup starts
    val resource: Resource<PnrScreen>? = null,
    val chartJustPrepared: Boolean = false, // one-shot celebration trigger (FR-4.2)
    val saveState: SaveState = SaveState.Idle,
)

/**
 * PNR (backlog 4.5, FR-4.x). Look up a PNR and render it MASKED everywhere
 * (FR-4.3, invariant 2) — the raw value lives only in memory here, long enough
 * to make the TLS request or create the server watch, and is never placed in
 * UI state, the cache key, logs, or nav args. Live status refreshes through the
 * ONE PollController; saving creates a server-side chart watch (FR-4.2).
 * Android-free so it's unit-tested on the JVM.
 */
class PnrViewModel(
    private val pnrScreen: (pnr: String) -> Flow<Resource<PnrScreen>>,
    private val createChartWatch: suspend (pnr: String) -> Boolean,
    private val poller: PollController,
    private val scope: CoroutineScope,
    private val cadenceMs: Long = 120_000L, // PNR 2–5 min; server tightens near chart
) {
    private val _state = MutableStateFlow(PnrUiState())
    val state: StateFlow<PnrUiState> = _state.asStateFlow()

    // In-memory only. Never persisted, logged, or exposed in [state].
    private var rawPnr: String? = null
    private var loopKey: String? = null

    fun onInputChange(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(10)
        _state.update { it.copy(input = digits, hint = validate(digits)) }
    }

    /** Look up the entered PNR if it is a valid 10-digit value. */
    fun lookup() {
        val entered = _state.value.input
        if (FormatValidation.validate(FormatValidation.Field.PNR, entered) !is FormatValidation.Result.Valid) {
            _state.update { it.copy(hint = FormatValidation.Msg.PNR_LENGTH) }
            return
        }
        rawPnr = entered
        _state.update {
            it.copy(maskedInput = maskPnr(entered), resource = null, saveState = SaveState.Idle, chartJustPrepared = false)
        }
        startWatchingLive(entered)
    }

    fun save() {
        val pnr = rawPnr ?: return
        if (_state.value.saveState == SaveState.Saving) return
        _state.update { it.copy(saveState = SaveState.Saving) }
        scope.launch {
            val ok = createChartWatch(pnr)
            _state.update { it.copy(saveState = if (ok) SaveState.Saved else SaveState.Failed) }
        }
    }

    fun dismissCelebration() { _state.update { it.copy(chartJustPrepared = false) } }

    /** Re-fetch the current PNR after an error (PRD §7 "next step"). */
    fun retry() {
        rawPnr?.let { startWatchingLive(it) }
    }

    /** Back to the input field; drops the in-memory PNR and stops polling. */
    fun clear() {
        loopKey?.let { poller.unregister(it) }
        loopKey = null
        rawPnr = null
        _state.value = PnrUiState()
    }

    private fun validate(digits: String): String? =
        when (val r = FormatValidation.validate(FormatValidation.Field.PNR, digits)) {
            is FormatValidation.Result.Invalid -> r.messageKey
            is FormatValidation.Result.Valid -> null
        }

    private fun startWatchingLive(pnr: String) {
        loopKey?.let { poller.unregister(it) }
        // Keyed by the SAME hash the cache uses — no raw PNR in the loop key.
        val key = app.railcast.core.data.pnrScreenKey(pnr)
        loopKey = key
        poller.register(key, cadenceMs) {
            var signature: String? = null
            pnrScreen(pnr).collect { res ->
                val wasPrepared = _state.value.resource?.value?.chart?.prepared == true
                _state.update { it.copy(resource = res) }
                val nowPrepared = res.value?.chart?.prepared == true
                if (!wasPrepared && nowPrepared) _state.update { it.copy(chartJustPrepared = true) }
                if (!res.loading) {
                    signature = (res.value?.chart?.prepared?.toString() ?: res.error?.code) +
                        res.value?.passengers?.joinToString { it.currentStatus }
                }
            }
            signature
        }
    }
}
