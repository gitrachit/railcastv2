package app.railcast

import app.railcast.directory.QueryClassifier
import app.railcast.directory.SearchResult
import app.railcast.directory.Station
import app.railcast.directory.Train
import app.railcast.directory.TrainSearch
import app.railcast.feature.find.FindMode
import app.railcast.feature.find.FindViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FindViewModelTest {

    private class FakeSearch(private val results: List<SearchResult>) : TrainSearch {
        var lastQuery: String? = null
        override suspend fun search(query: String, limit: Int): List<SearchResult> {
            lastQuery = query
            return results
        }
    }

    private fun train(no: String) =
        SearchResult(Train(no, "Goa Express", fromCode = "NZM", toCode = "MAO"), score = 1)

    private fun station(code: String) =
        SearchResult(Station(code, "Bhopal Jn", "Bhopal", "MP", lat = null, lng = null), score = 1)

    @Test fun typing_a_name_searches_the_directory() = runTest {
        val search = FakeSearch(listOf(train("12779")))
        val vm = FindViewModel(search, this, debounceMs = 0)
        vm.onQueryChange("goa")
        advanceUntilIdle()
        assertEquals("goa", search.lastQuery)
        assertEquals(1, vm.state.value.results.size)
        vm.clear()
    }

    @Test fun a_pnr_is_recognised_by_shape() = runTest {
        val vm = FindViewModel(FakeSearch(emptyList()), this, debounceMs = 0)
        vm.onQueryChange("4512882882")
        advanceUntilIdle()
        assertTrue(vm.state.value.intent is QueryClassifier.Intent.Pnr)
        vm.clear()
    }

    /**
     * A route has two halves and neither is a directory entry, so searching the
     * raw string would return noise the user has to scroll past.
     */
    @Test fun a_route_does_not_hit_the_directory() = runTest {
        val search = FakeSearch(listOf(station("BPL")))
        val vm = FindViewModel(search, this, debounceMs = 0)
        vm.onQueryChange("Delhi to Goa")
        advanceUntilIdle()
        assertTrue(vm.state.value.intent is QueryClassifier.Intent.Route)
        assertEquals(null, search.lastQuery)
        assertTrue(vm.state.value.results.isEmpty())
        vm.clear()
    }

    /** A station board left open must not sit underneath a fresh query. */
    @Test fun typing_returns_to_the_search_body() = runTest {
        val vm = FindViewModel(FakeSearch(emptyList()), this, debounceMs = 0)
        vm.showStation()
        assertEquals(FindMode.STATION, vm.state.value.mode)
        vm.onQueryChange("bho")
        advanceUntilIdle()
        assertEquals(FindMode.SEARCH, vm.state.value.mode)
        vm.clear()
    }

    @Test fun clearing_the_field_drops_results() = runTest {
        val vm = FindViewModel(FakeSearch(listOf(train("12779"))), this, debounceMs = 0)
        vm.onQueryChange("goa")
        advanceUntilIdle()
        vm.onQueryChange("")
        advanceUntilIdle()
        assertTrue(vm.state.value.results.isEmpty())
        assertEquals(QueryClassifier.Intent.Empty, vm.state.value.intent)
        vm.clear()
    }

    @Test fun a_partial_number_still_searches_rather_than_erroring() = runTest {
        val search = FakeSearch(listOf(train("22905")))
        val vm = FindViewModel(search, this, debounceMs = 0)
        vm.onQueryChange("229")
        advanceUntilIdle()
        assertEquals("229", search.lastQuery)
        assertTrue(vm.state.value.intent is QueryClassifier.Intent.FreeText)
        vm.clear()
    }
}
