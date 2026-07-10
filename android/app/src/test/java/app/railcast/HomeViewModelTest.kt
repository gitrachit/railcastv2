package app.railcast

import app.railcast.core.data.Resource
import app.railcast.core.net.TrainScreen
import app.railcast.core.net.TrainStatus
import app.railcast.core.poll.PollController
import app.railcast.directory.SearchResult
import app.railcast.directory.Train
import app.railcast.directory.TrainSearch
import app.railcast.feature.home.HomeViewModel
import app.railcast.feature.home.SavedTrains
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

private class FakeSearch(private val hits: List<SearchResult>) : TrainSearch {
    var queries = mutableListOf<String>()
    override suspend fun search(query: String, limit: Int): List<SearchResult> {
        queries += query
        return hits
    }
}

private class FakeSaved(initial: List<String> = emptyList()) : SavedTrains {
    private val flow = MutableStateFlow(initial)
    override val trains: Flow<List<String>> = flow
    override suspend fun add(trainNo: String) { flow.value = listOf(trainNo) + flow.value.filterNot { it == trainNo } }
    override suspend fun remove(trainNo: String) { flow.value = flow.value.filterNot { it == trainNo } }
}

private fun screen(no: String, summary: String = "On time") = TrainScreen(
    trainNo = no,
    name = "Test Express",
    runDateResolved = "2026-07-10",
    runDateChoices = emptyList(),
    status = TrainStatus(state = "running", summary = summary, lastUpdate = "t-$summary"),
    route = emptyList(),
)

private fun result(no: String) = SearchResult(Train(no, "Test Express", "AAA", "BBB"), score = 100)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private fun vm(
        scope: kotlinx.coroutines.CoroutineScope,
        search: TrainSearch = FakeSearch(listOf(result("12780"))),
        saved: SavedTrains = FakeSaved(),
        trainScreen: (String) -> Flow<Resource<TrainScreen>> = { no ->
            flow {
                emit(Resource(null, null, stale = false, loading = true, error = null))
                emit(Resource(screen(no), "2026-07-10T10:00:00Z", stale = false, loading = false, error = null))
            }
        },
    ) = HomeViewModel(search, saved, trainScreen, PollController(scope), scope, cadenceMs = 1000L, debounceMs = 100L)

    @Test fun `search populates results after debounce`() = runTest {
        val search = FakeSearch(listOf(result("12780"), result("12781")))
        val home = vm(backgroundScope, search = search)
        runCurrent()

        home.onQueryChange("test")
        runCurrent()
        assertTrue("no search before debounce elapses", search.queries.isEmpty())

        advanceTimeBy(100L); runCurrent()
        assertEquals(2, home.state.value.results.size)
    }

    @Test fun `blank query clears results without searching`() = runTest {
        val home = vm(backgroundScope)
        runCurrent()
        home.onQueryChange("   ")
        advanceTimeBy(100L); runCurrent()
        assertTrue(home.state.value.results.isEmpty())
    }

    @Test fun `overlong digit query raises a length hint`() = runTest {
        val home = vm(backgroundScope)
        runCurrent()
        home.onQueryChange("123456")
        assertEquals("validation_train_length", home.state.value.validationHint)
    }

    @Test fun `mid-typing digits and valid numbers show no hint`() = runTest {
        val home = vm(backgroundScope)
        runCurrent()
        home.onQueryChange("127")
        assertNull(home.state.value.validationHint)
        home.onQueryChange("12780")
        assertNull(home.state.value.validationHint)
        home.onQueryChange("rajdhani")
        assertNull(home.state.value.validationHint)
    }

    @Test fun `saving a train adds a live card populated from the poll loop`() = runTest {
        val home = vm(backgroundScope)
        runCurrent()

        home.onSaveTrain("12780")
        runCurrent()

        val saved = home.state.value.saved
        assertEquals(1, saved.size)
        assertEquals("12780", saved[0].trainNo)
        assertEquals("On time", saved[0].resource?.value?.status?.summary)
        assertFalse(saved[0].resource!!.loading)
    }

    @Test fun `removing a train drops its card`() = runTest {
        val saved = FakeSaved(listOf("12780"))
        val home = vm(backgroundScope, saved = saved)
        runCurrent()
        assertEquals(1, home.state.value.saved.size)

        home.onRemoveTrain("12780")
        runCurrent()
        assertTrue(home.state.value.saved.isEmpty())
    }

    @Test fun `saved card renders cached value first when offline`() = runTest {
        // Flow that only ever yields a stale cached value (no fresh) — offline path.
        val home = vm(backgroundScope, trainScreen = { no ->
            flow { emit(Resource(screen(no, "Cached"), "2026-07-10T09:00:00Z", stale = true, loading = false, error = null)) }
        })
        runCurrent()
        home.onSaveTrain("12780")
        runCurrent()

        val card = home.state.value.saved.single()
        assertEquals("Cached", card.resource?.value?.status?.summary)
        assertTrue(card.resource!!.stale)
    }
}
