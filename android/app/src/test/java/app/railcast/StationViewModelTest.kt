package app.railcast

import app.railcast.core.data.Resource
import app.railcast.core.net.StationRef
import app.railcast.core.net.StationScreen
import app.railcast.core.net.StationTrain
import app.railcast.core.net.StationTrainTime
import app.railcast.core.poll.PollController
import app.railcast.directory.SearchResult
import app.railcast.directory.Station
import app.railcast.directory.TrainSearch
import app.railcast.feature.station.StationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class StationFakeSearch(private val hits: List<SearchResult>) : TrainSearch {
    override suspend fun search(query: String, limit: Int) = hits
}

private fun stationBoard(code: String, hrs: Int) = StationScreen(
    station = StationRef(code, "Bhopal Jn"),
    windowHrs = hrs,
    trains = listOf(
        StationTrain("111", "A Exp", StationRef("S", "Src"), StationRef("NDLS", "Delhi"), "1", null, StationTrainTime("10:00"), "ontime", listOf("2A")),
        StationTrain("222", "B Exp", StationRef("S", "Src"), StationRef("CSMT", "Mumbai"), "2", null, StationTrainTime("10:30", "10:50", 20), "late", listOf("SL")),
    ),
)

@OptIn(ExperimentalCoroutinesApi::class)
class StationViewModelTest {

    private var lastHrs = -1

    private fun vm(scope: CoroutineScope) = StationViewModel(
        search = StationFakeSearch(listOf(SearchResult(Station("BPL", "Bhopal Jn", "Bhopal", "MP", null, null), 100))),
        stationScreen = { code, hrs ->
            lastHrs = hrs
            flow {
                emit(Resource(null, null, stale = false, loading = true, error = null))
                emit(Resource(stationBoard(code, hrs), "2026-07-10T10:00Z", stale = false, loading = false, error = null))
            }
        },
        poller = PollController(scope),
        scope = scope,
        cadenceMs = 1000L,
        debounceMs = 100L,
    )

    @Test fun `search finds stations after debounce`() = runTest {
        val vm = vm(backgroundScope)
        vm.onQueryChange("bhopal")
        advanceTimeBy(100L); runCurrent()
        assertEquals(1, vm.state.value.results.size)
    }

    @Test fun `open loads the board at the default four-hour window`() = runTest {
        val vm = vm(backgroundScope)
        vm.open("BPL")
        runCurrent()
        assertEquals("BPL", vm.state.value.code)
        assertEquals(4, vm.state.value.windowHrs)
        assertEquals(2, vm.state.value.resource?.value?.trains?.size)
    }

    @Test fun `switching the window refetches at the new hours`() = runTest {
        val vm = vm(backgroundScope)
        vm.open("BPL"); runCurrent()
        vm.setWindow(8); runCurrent()
        assertEquals(8, vm.state.value.windowHrs)
        assertEquals(8, lastHrs)
    }

    @Test fun `on-time filter narrows the visible board`() = runTest {
        val vm = vm(backgroundScope)
        vm.open("BPL"); runCurrent()
        assertEquals(2, vm.state.value.visibleTrains.size)
        vm.toggleOnTimeOnly()
        assertEquals(listOf("111"), vm.state.value.visibleTrains.map { it.no })
    }

    @Test fun `destination filter applies to the visible board`() = runTest {
        val vm = vm(backgroundScope)
        vm.open("BPL"); runCurrent()
        vm.setDestFilter("mumbai")
        assertEquals(listOf("222"), vm.state.value.visibleTrains.map { it.no })
    }

    @Test fun `available classes come from the loaded board`() = runTest {
        val vm = vm(backgroundScope)
        vm.open("BPL"); runCurrent()
        assertEquals(listOf("2A", "SL"), vm.state.value.availableClasses)
    }

    @Test fun `back clears the opened station`() = runTest {
        val vm = vm(backgroundScope)
        vm.open("BPL"); runCurrent()
        vm.back()
        assertNull(vm.state.value.code)
        assertTrue(vm.state.value.visibleTrains.isEmpty())
    }
}
