package app.railcast.feature.station

import app.railcast.core.data.Resource
import app.railcast.core.net.StationScreen
import app.railcast.core.net.StationTrain
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

data class StationUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val code: String? = null, // opened station
    val windowHrs: Int = 4, // 2 | 4 | 8
    val filters: StationFilterState = StationFilterState(),
    val resource: Resource<StationScreen>? = null,
) {
    /** The board after filters (FR-5.1). Empty when nothing loaded yet. */
    val visibleTrains: List<StationTrain>
        get() = resource?.value?.let { StationFilters.apply(it.trains, filters) } ?: emptyList()

    val availableClasses: List<String>
        get() = resource?.value?.let { StationFilters.classesOf(it.trains) } ?: emptyList()
}

/**
 * Station board (backlog 4.6, FR-5.1). Search a station → live arrivals/
 * departures over a 2/4/8-hr window with delay, platform, destination and
 * cancelled state; client-side destination/class/on-time filters. Refreshes
 * through the ONE PollController, cached-then-fresh (FR-9.1). Android-free so
 * it's unit-tested on the JVM.
 */
class StationViewModel(
    private val search: TrainSearch,
    private val stationScreen: (code: String, hrs: Int) -> Flow<Resource<StationScreen>>,
    private val poller: PollController,
    private val scope: CoroutineScope,
    private val cadenceMs: Long = 90_000L, // station board 60–120 s band (PRD §6.4)
    private val debounceMs: Long = 150L,
) {
    private val _state = MutableStateFlow(StationUiState())
    val state: StateFlow<StationUiState> = _state.asStateFlow()

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
            _state.update { it.copy(results = search.search(raw, 20)) }
        }
    }

    fun open(code: String) {
        _state.update { it.copy(code = code, resource = null, query = "", results = emptyList()) }
        startBoard(code, _state.value.windowHrs)
    }

    /** Switch the 2/4/8-hr window and re-fetch. */
    fun setWindow(hrs: Int) {
        if (hrs == _state.value.windowHrs && _state.value.resource != null) return
        _state.update { it.copy(windowHrs = hrs, resource = null) }
        _state.value.code?.let { startBoard(it, hrs) }
    }

    fun setDestFilter(text: String) = _state.update { it.copy(filters = it.filters.copy(dest = text)) }
    fun setClassFilter(cls: String?) = _state.update { it.copy(filters = it.filters.copy(cls = cls)) }
    fun toggleOnTimeOnly() = _state.update { it.copy(filters = it.filters.copy(onTimeOnly = !it.filters.onTimeOnly)) }

    fun back() {
        loopKey?.let { poller.unregister(it) }
        loopKey = null
        _state.value = StationUiState()
    }

    private fun startBoard(code: String, hrs: Int) {
        loopKey?.let { poller.unregister(it) }
        val key = "station:$code:$hrs"
        loopKey = key
        poller.register(key, cadenceMs) {
            var signature: String? = null
            stationScreen(code, hrs).collect { res ->
                _state.update { if (it.code == code && it.windowHrs == hrs) it.copy(resource = res) else it }
                if (!res.loading) {
                    signature = res.value?.trains?.joinToString { t -> "${t.no}:${t.status}:${t.platform}" }
                        ?: res.error?.code
                }
            }
            signature
        }
    }
}
