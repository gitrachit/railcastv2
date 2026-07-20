package app.railcast

import app.railcast.core.design.Confidence
import app.railcast.feature.track.StopConfidence
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A projected arrival must never render like an observed one (FR-11.1, FR-2.2).
 * These are the contract's stop states (api-contracts §1).
 */
class StopConfidenceTest {

    @Test fun reached_stops_report_observed_times() {
        assertEquals(Confidence.CERTAIN, StopConfidence.forStop("passed", showsActual = true))
        assertEquals(Confidence.CERTAIN, StopConfidence.forStop("departed", showsActual = true))
    }

    @Test fun stops_still_ahead_report_projections() {
        assertEquals(Confidence.ESTIMATED, StopConfidence.forStop("next", showsActual = true))
        assertEquals(Confidence.ESTIMATED, StopConfidence.forStop("upcoming", showsActual = true))
        assertEquals(Confidence.ESTIMATED, StopConfidence.forStop("destination", showsActual = true))
    }

    /** The published timetable is a fact even for a stop days away. */
    @Test fun timetable_only_rows_are_certain_in_every_state() {
        for (state in listOf("passed", "departed", "next", "upcoming", "destination")) {
            assertEquals(
                "schedule-only row in state=$state",
                Confidence.CERTAIN,
                StopConfidence.forStop(state, showsActual = false),
            )
        }
    }

    /** An unrecognised upstream state must not silently claim certainty. */
    @Test fun unknown_states_degrade_to_estimated() {
        assertEquals(Confidence.ESTIMATED, StopConfidence.forStop("something_new", showsActual = true))
    }
}
