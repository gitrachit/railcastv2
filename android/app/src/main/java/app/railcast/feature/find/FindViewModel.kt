package app.railcast.feature.find

import app.railcast.directory.QueryClassifier
import app.railcast.directory.SearchResult
import app.railcast.directory.TrainSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which body Find is showing. The omni-input decides; the user never picks. */
enum class FindMode { SEARCH, STATION, PLAN }

data class FindUiState(
    val query: String = "",
    val intent: QueryClassifier.Intent = QueryClassifier.Intent.Empty,
    val results: List<SearchResult> = emptyList(),
    /** FormatValidation message key for the inline hint, or null. */
    val hint: String? = null,
    val mode: FindMode = FindMode.SEARCH,
)

/**
 * Find (wireframe W8) — one input over trains, PNRs, stations and routes.
 *
 * The classifier decides what the user meant from the shape of what they
 * typed, so there is no mode toggle to get wrong. Free text still searches the
 * offline directory, so a partial query is never an error — classification
 * ranks intent, it does not gatekeep (FR-1.1, FR-1.5).
 *
 * Android-free so it unit-tests on the JVM, like every other ViewModel here.
 */
class FindViewModel(
    private val search: TrainSearch,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 150L,
) {
    private val _state = MutableStateFlow(FindUiState())
    val state: StateFlow<FindUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(raw: String) {
        val intent = QueryClassifier.classify(raw)
        _state.update {
            it.copy(
                query = raw,
                intent = intent,
                hint = QueryClassifier.hint(raw),
                // Typing returns to the search body; a station board or plan
                // the user opened earlier must not sit under a new query.
                mode = FindMode.SEARCH,
            )
        }
        searchJob?.cancel()
        if (raw.isBlank()) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        // A route has two halves and neither is a directory entry on its own,
        // so searching the raw string would return noise.
        if (intent is QueryClassifier.Intent.Route) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        searchJob = scope.launch {
            delay(debounceMs)
            _state.update { it.copy(results = search.search(raw, 20)) }
        }
    }

    /** Show the station board body (a station result was chosen). */
    fun showStation() = _state.update { it.copy(mode = FindMode.STATION) }

    /** Show the plan body (a route was typed, or "Plan a trip" tapped). */
    fun showPlan() = _state.update { it.copy(mode = FindMode.PLAN) }

    fun clear() {
        searchJob?.cancel()
        _state.value = FindUiState()
    }
}
