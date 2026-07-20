package app.railcast

import app.railcast.core.design.Confidence
import app.railcast.feature.ambient.Ambient
import app.railcast.feature.ambient.AmbientJourney
import app.railcast.feature.ambient.AmbientState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ambient layer's content rules. Everything above the RemoteViews call is
 * pure, so the design decisions are verified here rather than on a device —
 * which matters, because widget redraw behaviour itself is OEM-specific and
 * cannot be asserted in CI.
 */
class AmbientStateTest {

    private fun journey(
        no: String = "12951",
        cancelled: Boolean = false,
        minutes: Int? = 60,
        eta: String? = "16:49",
        station: String? = "Bhopal",
        platform: String? = "4",
        confidence: Confidence = Confidence.ESTIMATED,
    ) = AmbientJourney(
        trainNo = no,
        trainName = "Rajdhani",
        statusWord = "12 min late",
        stationName = station,
        eta = eta,
        platform = platform,
        confidence = confidence,
        cancelled = cancelled,
        minutesUntilRelevant = minutes,
    )

    @Test fun no_journeys_invites_rather_than_showing_nothing() {
        assertEquals(AmbientState.Invitation, Ambient.resolve(emptyList()))
    }

    @Test fun one_journey_is_the_answer() {
        val state = Ambient.resolve(listOf(journey())) as AmbientState.Live
        assertEquals("12951", state.journey.trainNo)
        assertEquals(0, state.otherCount)
    }

    @Test fun the_most_urgent_journey_wins_not_the_most_recent() {
        val far = journey(no = "11111", minutes = 600)
        val soon = journey(no = "22222", minutes = 20)
        val state = Ambient.resolve(listOf(far, soon)) as AmbientState.Live
        assertEquals("22222", state.journey.trainNo)
        assertEquals(1, state.otherCount)
    }

    /** Bad news the user has not seen is the most valuable thing here (FR-2.4). */
    @Test fun a_cancellation_outranks_a_sooner_running_train() {
        val imminent = journey(no = "22222", minutes = 5)
        val cancelled = journey(no = "33333", minutes = 900, cancelled = true)
        val state = Ambient.resolve(listOf(imminent, cancelled)) as AmbientState.Live
        assertEquals("33333", state.journey.trainNo)
    }

    @Test fun unknown_timing_sorts_last_but_still_renders() {
        val unknown = journey(no = "11111", minutes = null)
        val known = journey(no = "22222", minutes = 300)
        val state = Ambient.resolve(listOf(unknown, known)) as AmbientState.Live
        assertEquals("22222", state.journey.trainNo)
    }

    // ── the consequence line ────────────────────────────────────────────────

    @Test fun consequence_marks_an_estimate_with_the_tilde() {
        assertEquals("Bhopal ~16:49", Ambient.consequenceLine(journey()))
    }

    /** The tilde is the ONLY confidence channel that survives RemoteViews. */
    @Test fun an_observed_time_carries_no_tilde() {
        val line = Ambient.consequenceLine(journey(confidence = Confidence.CERTAIN))
        assertEquals("Bhopal 16:49", line)
        assertTrue(!line!!.contains("~"))
    }

    @Test fun no_consequence_without_the_users_stop() {
        // A train-only watch cannot say what the delay means for this person.
        assertEquals(null, Ambient.consequenceLine(journey(station = null)))
        assertEquals(null, Ambient.consequenceLine(journey(eta = null)))
    }

    // ── unknowns ────────────────────────────────────────────────────────────

    @Test fun unknown_platform_is_an_em_dash_never_blank_or_zero() {
        assertEquals("—", Ambient.platformLabel(journey(platform = null)))
        assertEquals("—", Ambient.platformLabel(journey(platform = "")))
        assertEquals("4", Ambient.platformLabel(journey(platform = "4")))
    }

    // ── freshness ───────────────────────────────────────────────────────────

    @Test fun freshness_is_stated_because_cadence_cannot_be_promised() {
        assertEquals("just now", Ambient.freshnessLabel(30))
        assertEquals("5 min ago", Ambient.freshnessLabel(5 * 60))
        assertEquals("2 h ago", Ambient.freshnessLabel(2 * 3600))
        assertEquals("3 d ago", Ambient.freshnessLabel(3 * 86_400))
    }
}
