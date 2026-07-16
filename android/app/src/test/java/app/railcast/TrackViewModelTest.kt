package app.railcast

import app.railcast.core.data.Resource
import app.railcast.core.net.RunDateChoice
import app.railcast.core.net.TrainScreen
import app.railcast.core.net.TrainStatus
import app.railcast.core.poll.PollController
import app.railcast.directory.SearchResult
import app.railcast.directory.Train
import app.railcast.directory.TrainSearch
import app.railcast.feature.home.SavedTrains
import app.railcast.feature.track.TrackViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class TrackFakeSearch(private val hits: List<SearchResult>) : TrainSearch {
    override suspend fun search(query: String, limit: Int) = hits
}

private class FakeSaved(initial: List<String> = emptyList()) : SavedTrains {
    private val flow = MutableStateFlow(initial)
    override val trains = flow
    override suspend fun add(trainNo: String) {
        flow.value = listOf(trainNo) + flow.value.filterNot { it == trainNo }
    }
    override suspend fun remove(trainNo: String) {
        flow.value = flow.value.filterNot { it == trainNo }
    }
}

private fun trackScreen(no: String, state: String = "running", run: String = "auto") = TrainScreen(
    trainNo = no,
    name = "Goa Express",
    runDateResolved = "2026-07-10",
    runDateChoices = listOf(
        RunDateChoice("2026-07-10", "today", active = true),
        RunDateChoice("2026-07-09", "yesterday", active = false),
    ),
    status = TrainStatus(state = state, summary = "Running · $run", lastUpdate = "t-$run"),
    route = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrackViewModelTest {

    private fun vm(
        scope: CoroutineScope,
        search: TrainSearch = TrackFakeSearch(listOf(SearchResult(Train("12780", "Goa Express", "NZM", "VSG"), 100))),
        onFactory: (String, String) -> Unit = { _, _ -> },
        state: String = "running",
        saved: SavedTrains = FakeSaved(),
    ): TrackViewModel = TrackViewModel(
        search = search,
        trainScreen = { no, run ->
            onFactory(no, run)
            flow {
                emit(Resource(null, null, stale = false, loading = true, error = null))
                emit(Resource(trackScreen(no, state, run), "2026-07-10T10:00Z", stale = false, loading = false, error = null))
            }
        },
        saved = saved,
        poller = PollController(scope),
        scope = scope,
        cadenceMs = 1000L,
        debounceMs = 100L,
    )

    @Test fun `search returns results after debounce`() = runTest {
        val track = vm(backgroundScope)
        track.onQueryChange("goa")
        advanceTimeBy(100L); runCurrent()
        assertEquals(1, track.state.value.results.size)
    }

    @Test fun `opening a train tracks it and populates the board`() = runTest {
        val track = vm(backgroundScope)
        track.open("12780")
        runCurrent()

        val s = track.state.value
        assertEquals("12780", s.trainNo)
        assertEquals("auto", s.selectedRun)
        assertEquals("Goa Express", s.resource?.value?.name)
        assertFalse(s.resource!!.loading)
        assertTrue(s.results.isEmpty()) // search cleared on open
    }

    @Test fun `pin toggles saved state and reflects an already-saved train`() = runTest {
        val store = FakeSaved()
        val track = vm(backgroundScope, saved = store)
        track.open("12780")
        runCurrent()
        assertFalse(track.state.value.pinned)

        track.togglePin(); runCurrent()
        assertTrue(track.state.value.pinned)
        assertEquals(listOf("12780"), store.trains.value)

        track.togglePin(); runCurrent()
        assertFalse(track.state.value.pinned)

        // Opening a train that's already saved comes up pinned.
        val track2 = vm(backgroundScope, saved = FakeSaved(listOf("22188")))
        track2.open("22188"); runCurrent()
        assertTrue(track2.state.value.pinned)
    }

    @Test fun `selecting a run re-fetches with that explicit run date`() = runTest {
        val runs = mutableListOf<String>()
        val track = vm(backgroundScope, onFactory = { _, run -> runs += run })
        track.open("12780")
        runCurrent()
        track.selectRun("2026-07-09")
        runCurrent()

        assertEquals("2026-07-09", track.state.value.selectedRun)
        assertTrue("expected an explicit-run fetch", runs.contains("2026-07-09"))
        assertFalse(track.state.value.showRunSheet) // sheet closes on pick
    }

    @Test fun `back returns to search and clears the tracked train`() = runTest {
        val track = vm(backgroundScope)
        track.open("12780")
        runCurrent()
        track.back()
        runCurrent()

        assertNull(track.state.value.trainNo)
        assertNull(track.state.value.resource)
    }

    @Test fun `cancelled run is surfaced through the resource`() = runTest {
        val track = vm(backgroundScope, state = "cancelled")
        track.open("12780")
        runCurrent()
        assertEquals("cancelled", track.state.value.resource?.value?.status?.state)
    }

    @Test fun `retry re-fetches the current train`() = runTest {
        val calls = mutableListOf<String>()
        val track = vm(backgroundScope, onFactory = { no, _ -> calls += no })
        track.open("12780"); runCurrent()
        assertEquals(1, calls.size)
        track.retry(); runCurrent()
        assertEquals(2, calls.size) // re-fetched on retry (PRD §7 next step)
    }

    @Test fun `run sheet toggles open and closed`() = runTest {
        val track = vm(backgroundScope)
        track.open("12780")
        runCurrent()
        track.openRunSheet()
        assertTrue(track.state.value.showRunSheet)
        track.dismissRunSheet()
        assertFalse(track.state.value.showRunSheet)
    }
}
