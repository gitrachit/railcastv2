package app.railcast

import app.railcast.core.design.Confidence
import app.railcast.feature.ambient.Ambient
import app.railcast.feature.ambient.AmbientJourney
import app.railcast.feature.ambient.AmbientState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * What survives the trip to an ambient surface.
 *
 * The widget renders from a snapshot rather than live data, so the fields that
 * carry meaning must be exactly the fields that get stored. A confidence lost
 * in serialisation would render an estimate as fact on the one surface where
 * copy is the only honesty channel available.
 */
class AmbientSnapshotTest {

    private val journey = AmbientJourney(
        trainNo = "12951",
        trainName = "Rajdhani",
        statusWord = "12 min late",
        stationName = "Bhopal",
        eta = "16:49",
        platform = "4",
        confidence = Confidence.ESTIMATED,
        minutesUntilRelevant = 45,
    )

    /**
     * The tilde must be produced from stored state, not added at render time by
     * a caller who might forget — it is the only confidence channel RemoteViews
     * supports.
     */
    @Test fun the_estimate_marker_derives_from_stored_confidence() {
        assertEquals("Bhopal ~16:49", Ambient.consequenceLine(journey))
        assertEquals(
            "Bhopal 16:49",
            Ambient.consequenceLine(journey.copy(confidence = Confidence.CERTAIN)),
        )
    }

    /** Confidence names are persisted as strings; they must round-trip. */
    @Test fun every_confidence_level_round_trips_by_name() {
        for (level in Confidence.entries) {
            assertEquals(level, Confidence.valueOf(level.name))
        }
    }

    @Test fun resolution_is_stable_for_the_same_input() {
        val list = listOf(journey, journey.copy(trainNo = "22222", minutesUntilRelevant = 10))
        val first = Ambient.resolve(list) as AmbientState.Live
        val second = Ambient.resolve(list) as AmbientState.Live
        assertEquals(first.journey.trainNo, second.journey.trainNo)
        assertEquals("22222", first.journey.trainNo)
    }

    /** A snapshot with no journey must resolve to the invitation, not a crash. */
    @Test fun empty_resolves_to_invitation() {
        assertEquals(AmbientState.Invitation, Ambient.resolve(emptyList()))
    }

    /** A train-only watch has no stop, so it has no consequence to state. */
    @Test fun a_journey_without_a_stop_still_renders_its_status() {
        val trainOnly = journey.copy(stationName = null, eta = null)
        assertEquals(null, Ambient.consequenceLine(trainOnly))
        val state = Ambient.resolve(listOf(trainOnly)) as AmbientState.Live
        assertNotNull(state.journey.statusWord)
        assertEquals("12 min late", state.journey.statusWord)
    }
}
