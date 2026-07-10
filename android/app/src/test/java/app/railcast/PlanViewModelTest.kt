package app.railcast

import app.railcast.core.data.Resource
import app.railcast.core.net.AvailabilityCell
import app.railcast.core.net.FareCell
import app.railcast.core.net.PlanRow
import app.railcast.core.net.PlanRowHydration
import app.railcast.core.net.PlanScreen
import app.railcast.core.net.RowAvailability
import app.railcast.core.net.RowFare
import app.railcast.core.net.RowFareBreakdown
import app.railcast.core.net.StationRef
import app.railcast.directory.SearchResult
import app.railcast.directory.Station
import app.railcast.directory.TrainSearch
import app.railcast.feature.plan.PlanQuota
import app.railcast.feature.plan.PlanSort
import app.railcast.feature.plan.PlanViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class PlanFakeSearch(private val hits: List<SearchResult>) : TrainSearch {
    override suspend fun search(query: String, limit: Int) = hits
}

private fun pendingRow(no: String, dep: String) = PlanRow(
    no = no, name = "Train $no", dep = dep, arr = "23:00", durationMin = 90,
    classes = listOf("SL"), runsOn = List(7) { true },
)

private fun hydration(total: Double, status: String = "available") =
    PlanRowHydration(
        RowAvailability(status, status, null, true),
        RowFare(total, RowFareBreakdown(total, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {

    private val stationA = Station("JBP", "Jabalpur", "Jabalpur", "MP", null, null)
    private val stationB = Station("NU", "Narsinghpur", "Narsinghpur", "MP", null, null)

    private fun vm(
        scope: CoroutineScope,
        rows: List<PlanRow> = listOf(pendingRow("111", "10:00"), pendingRow("222", "08:00")),
        fares: Map<String, Double> = mapOf("111" to 500.0, "222" to 300.0),
    ) = PlanViewModel(
        search = PlanFakeSearch(listOf(SearchResult(stationA, 100), SearchResult(stationB, 90))),
        planScreen = { from, to, date, quota ->
            flow {
                emit(Resource(null, null, stale = false, loading = true, error = null))
                emit(
                    Resource(
                        PlanScreen(StationRef(from, "F"), StationRef(to, "T"), date, quota, rows),
                        "2026-07-10T10:00Z", stale = false, loading = false, error = null,
                    ),
                )
            }
        },
        planRow = { no, _, _, _, _, _ -> fares[no]?.let { hydration(it) } },
        scope = scope,
        initialDate = "2026-07-10",
        debounceMs = 100L,
    )

    @Test fun `from search yields station suggestions after debounce`() = runTest {
        val plan = vm(backgroundScope)
        plan.onFromQuery("jab")
        advanceTimeBy(100L); runCurrent()
        assertEquals(2, plan.state.value.fromResults.size)
    }

    @Test fun `selecting from and to enables search`() = runTest {
        val plan = vm(backgroundScope)
        assertFalse(plan.state.value.canSearch)
        plan.selectFrom(stationA)
        plan.selectTo(stationB)
        assertTrue(plan.state.value.canSearch)
        assertEquals("Jabalpur", plan.state.value.fromQuery)
    }

    @Test fun `search loads rows then hydrates each one`() = runTest {
        val plan = vm(backgroundScope)
        plan.selectFrom(stationA); plan.selectTo(stationB)
        plan.search()
        runCurrent()

        val rows = plan.state.value.rows
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.availability is AvailabilityCell.Ready && it.fare is FareCell.Ready })
        assertEquals(500.0, (rows.first { it.no == "111" }.fare as FareCell.Ready).value.total, 0.0)
    }

    @Test fun `a row that fails to hydrate stays pending without blocking others`() = runTest {
        val plan = vm(backgroundScope, fares = mapOf("111" to 500.0)) // 222 has no fare → null
        plan.selectFrom(stationA); plan.selectTo(stationB)
        plan.search()
        runCurrent()

        val rows = plan.state.value.rows
        assertTrue(rows.first { it.no == "111" }.availability is AvailabilityCell.Ready)
        assertTrue(rows.first { it.no == "222" }.availability is AvailabilityCell.Pending)
    }

    @Test fun `sort by price reorders the visible rows`() = runTest {
        val plan = vm(backgroundScope)
        plan.selectFrom(stationA); plan.selectTo(stationB)
        plan.search(); runCurrent()
        plan.setSort(PlanSort.PRICE)
        assertEquals(listOf("222", "111"), plan.state.value.visibleRows.map { it.no }) // 300 before 500
    }

    @Test fun `date cannot step before today but can step forward`() = runTest {
        val plan = vm(backgroundScope)
        plan.stepDate(-1)
        assertEquals("2026-07-10", plan.state.value.date) // floored at today
        plan.stepDate(1)
        assertEquals("2026-07-11", plan.state.value.date)
    }

    @Test fun `expand toggles a single row`() = runTest {
        val plan = vm(backgroundScope)
        plan.toggleExpand("111")
        assertEquals("111", plan.state.value.expanded)
        plan.toggleExpand("111")
        assertEquals(null, plan.state.value.expanded)
    }

    @Test fun `quota selection is retained`() = runTest {
        val plan = vm(backgroundScope)
        plan.setQuota(PlanQuota.TATKAL)
        assertEquals(PlanQuota.TATKAL, plan.state.value.quota)
    }
}
