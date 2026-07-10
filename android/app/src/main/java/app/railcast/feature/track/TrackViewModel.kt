package app.railcast.feature.track

import app.railcast.core.data.Resource
import app.railcast.core.net.TrainScreen
import app.railcast.core.poll.PollController
import app.railcast.directory.SearchResult
import app.railcast.directory.TrainSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrackUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val trainNo: String? = null, // null → search mode; non-null → tracking
    val selectedRun: String = AUTO,
    val resource: Resource<TrainScreen>? = null,
    val showRunSheet: Boolean = false,
) {
    companion object { const val AUTO = "auto" }
}

/**
 * Track (backlog 4.3, FR-2.x). Pick a train (directory search), then watch its
 * live screen through the ONE PollController (PRD §6.4) at the trackTrain
 * cadence, cached-then-fresh so it never blanks offline (FR-9.1). The run-date
 * choice is today/yesterday only — never a raw date (FR-2.3, invariant 3).
 * Android-free (interfaces + a flow factory) so it's unit-tested on the JVM.
 */
class TrackViewModel(
    private val search: TrainSearch,
    private val trainScreen: (trainNo: String, run: String) -> Flow<Resource<TrainScreen>>,
    private val poller: PollController,
    private val scope: CoroutineScope,
    private val cadenceMs: Long = 45_000L, // trackTrain 30–60 s band (PRD §6.4)
    private val debounceMs: Long = 150L,
) {
    private val _state = MutableStateFlow(TrackUiState())
    val state: StateFlow<TrackUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var loopKey: String? = null

    fun onQueryChange(raw: String) {
        _state.update { it.copy(query = raw) }
        searchJob?.cancel()
        if (raw.isBlank()) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        searchJob = scope.launch {
            delay(debounceMs)
            val hits = search.search(raw, 20)
            _state.update { it.copy(results = hits) }
        }
    }

    /** Start tracking a train (run=auto; the server picks the active run). */
    fun open(trainNo: String) {
        _state.update {
            it.copy(trainNo = trainNo, selectedRun = TrackUiState.AUTO, resource = null, query = "", results = emptyList())
        }
        startTracking(trainNo, TrackUiState.AUTO)
    }

    /** Override the run from the today/yesterday sheet — never a raw date. */
    fun selectRun(runDate: String) {
        val trainNo = _state.value.trainNo ?: return
        _state.update { it.copy(selectedRun = runDate, resource = null, showRunSheet = false) }
        startTracking(trainNo, runDate)
    }

    fun openRunSheet() { _state.update { it.copy(showRunSheet = true) } }
    fun dismissRunSheet() { _state.update { it.copy(showRunSheet = false) } }

    /** Re-fetch the current train after an error (PRD §7 "next step"). */
    fun retry() {
        val s = _state.value
        s.trainNo?.let { startTracking(it, s.selectedRun) }
    }

    /** Leave the tracked train, back to search. */
    fun back() {
        loopKey?.let { poller.unregister(it) }
        loopKey = null
        _state.update { TrackUiState() }
    }

    private fun startTracking(trainNo: String, run: String) {
        loopKey?.let { poller.unregister(it) }
        val key = "track:$trainNo:$run"
        loopKey = key
        poller.register(key, cadenceMs) {
            var signature: String? = null
            trainScreen(trainNo, run).collect { res ->
                _state.update { if (it.trainNo == trainNo && it.selectedRun == run) it.copy(resource = res) else it }
                if (!res.loading) signature = res.value?.status?.lastUpdate ?: res.error?.code
            }
            signature
        }
    }
}
