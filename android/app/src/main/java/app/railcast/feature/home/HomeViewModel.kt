package app.railcast.feature.home

import app.railcast.core.data.Resource
import app.railcast.core.net.TrainScreen
import app.railcast.core.poll.PollController
import app.railcast.directory.FormatValidation
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

/** One saved train's card: its number plus the latest SWR snapshot (null until
 *  the first emission lands). */
data class SavedCard(val trainNo: String, val resource: Resource<TrainScreen>?)

data class HomeUiState(
    val query: String = "",
    val validationHint: String? = null, // FormatValidation message key, or null
    val results: List<SearchResult> = emptyList(),
    val saved: List<SavedCard> = emptyList(),
)

/**
 * Home = search + saved live cards (backlog 4.2). Directory search resolves a
 * name/number to a train before any API call (FR-1.1); saved trains each refresh
 * through the ONE PollController (PRD §6.4) — never a per-card timer — serving
 * cached-then-fresh so cards render instantly and offline (FR-9.1). Pure of
 * Android deps (interfaces + a flow factory) so it's unit-tested on the JVM.
 */
class HomeViewModel(
    private val search: TrainSearch,
    private val saved: SavedTrains,
    private val trainScreen: (trainNo: String) -> Flow<Resource<TrainScreen>>,
    private val poller: PollController,
    private val scope: CoroutineScope,
    private val cadenceMs: Long = 45_000L, // trackTrain volatility band (PRD §6.4)
    private val debounceMs: Long = 150L,
) {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        scope.launch { saved.trains.collect { reconcile(it) } }
    }

    fun onQueryChange(raw: String) {
        val q = raw
        _state.update { it.copy(query = q, validationHint = validate(q)) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        searchJob = scope.launch {
            delay(debounceMs)
            val hits = search.search(q, 20)
            _state.update { it.copy(results = hits) }
        }
    }

    fun onSaveTrain(trainNo: String) { scope.launch { saved.add(trainNo) } }
    fun onRemoveTrain(trainNo: String) { scope.launch { saved.remove(trainNo) } }

    /** Inline format hint only for a pure-digit entry (a train number in the
     *  making); names never trigger it (FR-1.5). */
    private fun validate(q: String): String? {
        if (q.isEmpty() || !q.all { it.isDigit() }) return null
        return when (val r = FormatValidation.validate(FormatValidation.Field.TRAIN_NUMBER, q)) {
            is FormatValidation.Result.Invalid -> r.messageKey
            is FormatValidation.Result.Valid -> null
        }
    }

    /** Keep poll loops and card list in lock-step with the saved set. */
    private fun reconcile(list: List<String>) {
        val existing = _state.value.saved.associateBy { it.trainNo }
        // Update the visible list first (preserving any resource already fetched)
        // so an immediate poll emission has a card to attach to.
        _state.update { st -> st.copy(saved = list.map { existing[it] ?: SavedCard(it, null) }) }

        for (no in existing.keys - list.toSet()) poller.unregister(loopKey(no))
        for (no in list) if (no !in existing) registerLoop(no)
    }

    private fun registerLoop(trainNo: String) {
        poller.register(loopKey(trainNo), cadenceMs) {
            var signature: String? = null
            trainScreen(trainNo).collect { res ->
                updateCard(trainNo, res)
                // Back off only once a settled (non-loading) value is in hand.
                if (!res.loading) signature = res.value?.status?.lastUpdate ?: res.error?.code
            }
            signature
        }
    }

    private fun updateCard(trainNo: String, res: Resource<TrainScreen>) {
        _state.update { st ->
            st.copy(saved = st.saved.map { if (it.trainNo == trainNo) it.copy(resource = res) else it })
        }
    }

    private fun loopKey(trainNo: String) = "home:train:$trainNo"
}
