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

    @Test fun `swap reverses origin and destination`() = runTest {
        val plan = vm(backgroundScope)
        plan.selectFrom(stationA); plan.selectTo(stationB)
        plan.swap()
        val s = plan.state.value
        assertEquals(stationB, s.from)
        assertEquals(stationA, s.to)
        assertEquals("Narsinghpur", s.fromQuery)
        assertEquals("Jabalpur", s.toQuery)
        assertTrue(s.canSearch) // still valid after swapping
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

    @Test fun `stale hydration from a previous search never patches the new one`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var calls = 0
        val plan = PlanViewModel(
            search = PlanFakeSearch(emptyList()),
            planScreen = { from, to, date, quota ->
                flow {
                    emit(
                        Resource(
                            PlanScreen(StationRef(from, "F"), StationRef(to, "T"), date, quota, listOf(pendingRow("111", "10:00"))),
                            "t", stale = false, loading = false, error = null,
                        ),
                    )
                }
            },
            planRow = { _, _, _, _, _, _ ->
                if (++calls == 1) { gate.await(); hydration(999.0) } // slow row from search 1
                else hydration(500.0)
            },
            scope = backgroundScope,
            initialDate = "2026-07-10",
        )
        plan.selectFrom(stationA); plan.selectTo(stationB)
        plan.search(); runCurrent() // search 1: its hydration is stuck on the gate
        plan.search(); runCurrent() // search 2: hydrates immediately with 500
        gate.complete(Unit); runCurrent() // search 1's stale 999 arrives late — must be dropped

        val fare = plan.state.value.rows.single().fare as FareCell.Ready
        assertEquals(500.0, fare.value.total, 0.0) // not clobbered by the stale hydration
    }

    @Test fun `retry re-runs the current search`() = runTest {
        var searches = 0
        val plan = PlanViewModel(
            search = PlanFakeSearch(emptyList()),
            planScreen = { _, _, _, _ ->
                searches++
                flow { emit(Resource(null, null, stale = false, loading = false, error = null)) }
            },
            planRow = { _, _, _, _, _, _ -> null },
            scope = backgroundScope,
            initialDate = "2026-07-10",
        )
        plan.selectFrom(stationA); plan.selectTo(stationB)
        plan.search(); runCurrent()
        assertEquals(1, searches)
        plan.retry(); runCurrent()
        assertEquals(2, searches)
    }

    @Test fun `tatkal reminder marks the row and a failure is retryable`() = runTest {
        val created = mutableListOf<Triple<String, String, String>>()
        var succeed = false
        val plan = PlanViewModel(
            search = PlanFakeSearch(emptyList()),
            planScreen = { from, to, date, quota ->
                flow {
                    emit(
                        Resource(
                            PlanScreen(StationRef(from, "F"), StationRef(to, "T"), date, quota, listOf(pendingRow("111", "10:00"))),
                            "t", stale = false, loading = false, error = null,
                        ),
                    )
                }
            },
            planRow = { _, _, _, _, _, _ -> null },
            scope = backgroundScope,
            initialDate = "2026-07-10",
            createTatkalWatch = { no, date, band -> created += Triple(no, date, band); succeed },
        )
        plan.selectFrom(stationA); plan.selectTo(stationB)
        plan.search(); runCurrent()
        val row = plan.state.value.rows.single()

        plan.remindTatkal(row); runCurrent()
        assertTrue(plan.state.value.tatkalFailed.contains("111")) // create failed → retryable
        succeed = true
        plan.remindTatkal(row); runCurrent()
        assertTrue(plan.state.value.tatkalReminded.contains("111"))
        assertFalse(plan.state.value.tatkalFailed.contains("111"))
        assertEquals(Triple("111", "2026-07-10", "nonac"), created.last()) // SL-only row → nonac band

        plan.remindTatkal(row); runCurrent()
        assertEquals(2, created.size) // already reminded → no duplicate watch
    }

    @Test fun `quota selection is retained`() = runTest {
        val plan = vm(backgroundScope)
        plan.setQuota(PlanQuota.TATKAL)
        assertEquals(PlanQuota.TATKAL, plan.state.value.quota)
    }
}
