package app.railcast.feature.plan

import app.railcast.core.data.Resource
import app.railcast.core.net.AvailabilityCell
import app.railcast.core.net.FareCell
import app.railcast.core.net.PlanRow
import app.railcast.core.net.PlanRowHydration
import app.railcast.core.net.PlanScreen
import app.railcast.directory.Station
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

data class PlanUiState(
    val fromQuery: String = "",
    val fromResults: List<Station> = emptyList(),
    val from: Station? = null,
    val toQuery: String = "",
    val toResults: List<Station> = emptyList(),
    val to: Station? = null,
    val date: String = "",
    val quota: PlanQuota = PlanQuota.GENERAL,
    val sort: PlanSort = PlanSort.DEPARTURE,
    val resource: Resource<PlanScreen>? = null,
    val rows: List<PlanRow> = emptyList(),
    val expanded: String? = null,
) {
    val canSearch: Boolean get() = from != null && to != null
    val visibleRows: List<PlanRow> get() = PlanSorting.sort(rows, sort)
}

/**
 * Plan (backlog 4.7, FR-6.1–6.3). A→B for a date + quota returns the train list
 * fast; each row's seats + fare hydrate through a separate call so a slow row
 * never blocks the list (FR-6.2). Expanding a row shows the full fare breakdown
 * (FR-6.3). Android-free (search seam + list/row flow factories) so the search,
 * hydration and sort are unit-tested on the JVM.
 */
class PlanViewModel(
    private val search: TrainSearch,
    private val planScreen: (from: String, to: String, date: String, quota: String) -> Flow<Resource<PlanScreen>>,
    private val planRow: suspend (trainNo: String, from: String, to: String, date: String, cls: String, quota: String) -> PlanRowHydration?,
    private val scope: CoroutineScope,
    initialDate: String,
    private val debounceMs: Long = 150L,
) {
    private val _state = MutableStateFlow(PlanUiState(date = initialDate))
    val state: StateFlow<PlanUiState> = _state.asStateFlow()

    private var fromJob: Job? = null
    private var toJob: Job? = null

    fun onFromQuery(raw: String) {
        _state.update { it.copy(fromQuery = raw, from = null) }
        fromJob?.cancel()
        if (raw.isBlank()) { _state.update { it.copy(fromResults = emptyList()) }; return }
        fromJob = scope.launch { delay(debounceMs); _state.update { it.copy(fromResults = stationsFor(raw)) } }
    }

    fun onToQuery(raw: String) {
        _state.update { it.copy(toQuery = raw, to = null) }
        toJob?.cancel()
        if (raw.isBlank()) { _state.update { it.copy(toResults = emptyList()) }; return }
        toJob = scope.launch { delay(debounceMs); _state.update { it.copy(toResults = stationsFor(raw)) } }
    }

    fun selectFrom(station: Station) =
        _state.update { it.copy(from = station, fromQuery = station.name, fromResults = emptyList()) }

    fun selectTo(station: Station) =
        _state.update { it.copy(to = station, toQuery = station.name, toResults = emptyList()) }

    // The journey date can't go before "today" (the date the screen opened).
    private val floorDate: String = initialDate

    fun stepDate(days: Int) {
        val next = PlanDates.addDays(_state.value.date, days)
        if (next < floorDate) return // never plan into the past
        _state.update { it.copy(date = next) }
    }

    fun setQuota(quota: PlanQuota) = _state.update { it.copy(quota = quota) }
    fun setSort(sort: PlanSort) = _state.update { it.copy(sort = sort) }

    /** Re-run the current A→B search after an error (PRD §7 "next step"). */
    fun retry() = search()
    fun toggleExpand(trainNo: String) =
        _state.update { it.copy(expanded = if (it.expanded == trainNo) null else trainNo) }

    // Monotonic search generation: list updates and row hydrations only apply
    // when they belong to the LATEST search, so a slow row fetched for one
    // date/quota can never patch the same train number in a newer search.
    private var searchGen = 0

    /** Run the A→B search and kick off per-row hydration. */
    fun search() {
        val s = _state.value
        val from = s.from ?: return
        val to = s.to ?: return
        val gen = ++searchGen
        _state.update { it.copy(resource = null, rows = emptyList(), expanded = null) }
        scope.launch {
            planScreen(from.code, to.code, s.date, s.quota.code).collect { res ->
                if (gen != searchGen) return@collect
                _state.update { it.copy(resource = res, rows = res.value?.trains ?: it.rows) }
            }
            if (gen == searchGen) hydrateRows(gen, from.code, to.code, s.date, s.quota.code)
        }
    }

    /** Fetch every pending row's seats + fare concurrently — independent calls,
     *  so the slowest never blocks the rest (FR-6.2). */
    private fun hydrateRows(gen: Int, from: String, to: String, date: String, quota: String) {
        for (row in _state.value.rows) {
            if (row.availability !is AvailabilityCell.Pending) continue
            val cls = row.classes.firstOrNull() ?: continue
            scope.launch {
                val h = planRow(row.no, from, to, date, cls, quota) ?: return@launch
                if (gen == searchGen) patchRow(row.no, h)
            }
        }
    }

    private fun patchRow(trainNo: String, h: PlanRowHydration) {
        _state.update { st ->
            st.copy(
                rows = st.rows.map {
                    if (it.no == trainNo) {
                        it.copy(availability = AvailabilityCell.Ready(h.availability), fare = FareCell.Ready(h.fare))
                    } else {
                        it
                    }
                },
            )
        }
    }

    private suspend fun stationsFor(raw: String): List<Station> =
        search.search(raw, 20).mapNotNull { it.entry as? Station }
}
