package app.railcast

import app.railcast.core.analytics.Analytics
import app.railcast.core.analytics.AnalyticsEvent
import app.railcast.core.analytics.AnalyticsScreen
import app.railcast.core.analytics.AlertLatency
import app.railcast.core.analytics.ConsentGatedAnalytics
import app.railcast.core.analytics.FirstSessionSuccess
import app.railcast.core.analytics.TimeToAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    override fun log(event: AnalyticsEvent) { events += event }
}

class AnalyticsTest {

    @Test fun `consent gate drops events when opted out`() {
        val sink = RecordingAnalytics()
        var consent = false
        val gated = ConsentGatedAnalytics(sink) { consent }

        gated.log(TimeToAnswer(AnalyticsScreen.HOME, 120))
        assertTrue("opted out → nothing logged", sink.events.isEmpty())

        consent = true
        gated.log(TimeToAnswer(AnalyticsScreen.HOME, 120))
        assertEquals(1, sink.events.size)
    }

    @Test fun `every event carries only numeric params — no strings can leak a PNR`() {
        val events = listOf(
            TimeToAnswer(AnalyticsScreen.PNR, 90),
            AlertLatency(typeOrdinal = 2, ms = 3400),
            FirstSessionSuccess(intentOrdinal = 1),
        )
        val pnr = "2458692882"
        for (e in events) {
            // The params map is <String, Long> by type — no free-form user strings.
            assertTrue(e.params.values.all { it is Long })
            assertTrue("no param may equal a PNR value", e.params.values.none { it.toString() == pnr })
        }
    }

    @Test fun `event names are stable identifiers`() {
        assertEquals("time_to_answer", TimeToAnswer(AnalyticsScreen.HOME, 1).name)
        assertEquals("alert_latency", AlertLatency(0, 1).name)
        assertEquals("first_session_success", FirstSessionSuccess(0).name)
    }

    @Test fun `time-to-answer encodes screen ordinal and duration`() {
        val e = TimeToAnswer(AnalyticsScreen.TRACK, 250)
        assertEquals(AnalyticsScreen.TRACK.ordinal.toLong(), e.params["screen"])
        assertEquals(250L, e.params["ms"])
    }
}
