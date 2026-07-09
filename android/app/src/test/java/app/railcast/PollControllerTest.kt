package app.railcast

import app.railcast.core.poll.PollController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val BASE = 1000L

/** Fetch returning scripted signatures; counts calls. */
private class FakeFetch(private val signatures: List<String?>, private val default: String? = "same") :
    PollController.PollFetch {
    var calls = 0
    override suspend fun refresh(): String? {
        val sig = signatures.getOrElse(calls) { default }
        calls++
        return sig
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PollControllerTest {

    @Test
    fun refreshesImmediatelyOnRegister() = runTest {
        val controller = PollController(backgroundScope)
        val fetch = FakeFetch(emptyList())
        controller.register("train", BASE, fetch)
        runCurrent()
        assertEquals(1, fetch.calls) // immediate refresh, no waiting for the first cadence
    }

    @Test
    fun pollsEveryCadenceWhenPayloadKeepsChanging() = runTest {
        val controller = PollController(backgroundScope)
        val fetch = FakeFetch(listOf("a", "b", "c", "d"))
        controller.register("train", BASE, fetch)
        runCurrent()

        repeat(3) { advanceTimeBy(BASE); runCurrent() }
        assertEquals(4, fetch.calls) // t0, t1000, t2000, t3000 — no back-off, payload changes
    }

    @Test
    fun backsOffWhilePayloadIsUnchanged() = runTest {
        val controller = PollController(backgroundScope)
        val fetch = FakeFetch(emptyList(), default = "same") // always identical
        controller.register("train", BASE, fetch)
        runCurrent()

        // t0 fetch → delay 1x; t1000 fetch → delay 2x (due 3000); t2000 nothing; t3000 fetch.
        repeat(3) { advanceTimeBy(BASE); runCurrent() }
        assertEquals(3, fetch.calls) // fewer than the 4 a changing payload would produce
    }

    @Test
    fun resetsToBaseCadenceWhenPayloadChanges() = runTest {
        val controller = PollController(backgroundScope)
        // A, A (back off), then B (change → reset to base)
        val fetch = FakeFetch(listOf("A", "A", "B", "B"))
        controller.register("train", BASE, fetch)
        runCurrent()

        advanceTimeBy(BASE); runCurrent() // t1000: A==A → streak1, delay 2x → due 3000
        advanceTimeBy(2 * BASE); runCurrent() // t3000: B != A → reset, delay 1x → due 4000
        advanceTimeBy(BASE); runCurrent() // t4000: fetch fires because it reset to base
        assertEquals(4, fetch.calls) // without reset it would have waited to t7000 → 3 calls
    }

    @Test
    fun stopsAllLoopsOnBackground() = runTest {
        val controller = PollController(backgroundScope)
        val fetch = FakeFetch(listOf("a", "b", "c"))
        controller.register("train", BASE, fetch)
        runCurrent()
        assertEquals(1, fetch.calls)

        controller.onBackground()
        assertEquals(0, controller.activeLoopCount)
        repeat(5) { advanceTimeBy(BASE); runCurrent() }
        assertEquals(1, fetch.calls) // no polling while backgrounded (NFR-3)
    }

    @Test
    fun resumesWithImmediateRefreshOnForeground() = runTest {
        val controller = PollController(backgroundScope)
        val fetch = FakeFetch(listOf("a", "b", "c", "d"))
        controller.register("train", BASE, fetch)
        runCurrent()
        controller.onBackground()
        advanceTimeBy(3 * BASE); runCurrent()
        assertEquals(1, fetch.calls)

        controller.onForeground()
        runCurrent()
        assertEquals(2, fetch.calls) // immediate refresh on resume
    }

    @Test
    fun unregisterStopsOnlyThatLoop() = runTest {
        val controller = PollController(backgroundScope)
        val a = FakeFetch(listOf("1", "2", "3", "4"))
        val b = FakeFetch(listOf("x", "y", "z", "w"))
        controller.register("A", BASE, a)
        controller.register("B", BASE, b)
        runCurrent()

        controller.unregister("A")
        repeat(2) { advanceTimeBy(BASE); runCurrent() }

        assertEquals(1, a.calls) // stopped after its single immediate fetch
        assertEquals(3, b.calls) // keeps polling
    }
}
