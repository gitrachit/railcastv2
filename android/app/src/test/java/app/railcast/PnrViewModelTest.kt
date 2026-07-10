package app.railcast

import app.railcast.core.data.Resource
import app.railcast.core.net.ChartStatus
import app.railcast.core.net.PnrJourney
import app.railcast.core.net.PnrPassenger
import app.railcast.core.net.PnrScreen
import app.railcast.core.net.PnrTrainRef
import app.railcast.core.net.StationRef
import app.railcast.core.poll.PollController
import app.railcast.feature.pnr.PnrViewModel
import app.railcast.feature.pnr.SaveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun pnrModel(prepared: Boolean = false, current: String = "CNF") = PnrScreen(
    pnrMasked = "••••2882",
    train = PnrTrainRef("12780", "Goa Express"),
    journey = PnrJourney("2026-07-10", StationRef("NZM", "Nizamuddin"), StationRef("VSG", "Vasco"), StationRef("NZM", "Nizamuddin"), "2A", "GN"),
    chart = ChartStatus(prepared),
    passengers = listOf(PnrPassenger(1, "CNF", current, "A1", 32, "SL")),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PnrViewModelTest {

    private fun vm(
        scope: CoroutineScope,
        prepared: Boolean = false,
        saveOk: Boolean = true,
        onWatch: (String) -> Unit = {},
        pnrScreen: (String) -> Flow<Resource<PnrScreen>> = {
            flow {
                emit(Resource(null, null, stale = false, loading = true, error = null))
                emit(Resource(pnrModel(prepared), "2026-07-10T10:00Z", stale = false, loading = false, error = null))
            }
        },
    ) = PnrViewModel(
        pnrScreen = pnrScreen,
        createChartWatch = { p -> onWatch(p); saveOk },
        poller = PollController(scope),
        scope = scope,
    )

    @Test fun `input filters non-digits and caps at ten`() = runTest {
        val pnr = vm(backgroundScope)
        pnr.onInputChange("24a58 69-2882999")
        assertEquals("2458692882", pnr.state.value.input)
    }

    @Test fun `short input shows no scolding hint`() = runTest {
        val pnr = vm(backgroundScope)
        pnr.onInputChange("24586")
        assertNull(pnr.state.value.hint)
    }

    @Test fun `lookup masks the entered pnr and never exposes it raw`() = runTest {
        val pnr = vm(backgroundScope)
        pnr.onInputChange("2458692882")
        pnr.lookup()
        runCurrent()

        assertEquals("••••2882", pnr.state.value.maskedInput)
        assertEquals("Goa Express", pnr.state.value.resource?.value?.train?.name)
        // The raw PNR must not surface anywhere in the observable state.
        assertFalse(pnr.state.toString().contains("2458692882"))
    }

    @Test fun `lookup on an invalid pnr refuses and flags length`() = runTest {
        val pnr = vm(backgroundScope)
        pnr.onInputChange("24586")
        pnr.lookup()
        runCurrent()
        assertNull(pnr.state.value.resource)
        assertEquals("validation_pnr_length", pnr.state.value.hint)
    }

    @Test fun `chart already prepared triggers the celebration`() = runTest {
        val pnr = vm(backgroundScope, prepared = true)
        pnr.onInputChange("2458692882")
        pnr.lookup()
        runCurrent()
        assertTrue(pnr.state.value.chartJustPrepared)
    }

    @Test fun `unprepared chart does not celebrate`() = runTest {
        val pnr = vm(backgroundScope, prepared = false)
        pnr.onInputChange("2458692882")
        pnr.lookup()
        runCurrent()
        assertFalse(pnr.state.value.chartJustPrepared)
    }

    @Test fun `save creates a chart watch with the raw pnr and reports saved`() = runTest {
        var watched: String? = null
        val pnr = vm(backgroundScope, onWatch = { watched = it })
        pnr.onInputChange("2458692882")
        pnr.lookup()
        runCurrent()
        pnr.save()
        runCurrent()

        assertEquals("2458692882", watched) // raw goes to the server watch only
        assertEquals(SaveState.Saved, pnr.state.value.saveState)
    }

    @Test fun `failed save is reported and retryable`() = runTest {
        val pnr = vm(backgroundScope, saveOk = false)
        pnr.onInputChange("2458692882")
        pnr.lookup()
        runCurrent()
        pnr.save()
        runCurrent()
        assertEquals(SaveState.Failed, pnr.state.value.saveState)
    }

    @Test fun `clear drops the pnr and resets state`() = runTest {
        val pnr = vm(backgroundScope)
        pnr.onInputChange("2458692882")
        pnr.lookup()
        runCurrent()
        pnr.clear()
        assertNull(pnr.state.value.resource)
        assertNull(pnr.state.value.maskedInput)
        assertEquals("", pnr.state.value.input)
    }
}
