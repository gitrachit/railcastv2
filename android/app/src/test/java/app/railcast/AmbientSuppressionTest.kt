package app.railcast

import app.railcast.feature.ambient.Ambient
import app.railcast.feature.ambient.AmbientJourney
import app.railcast.feature.ambient.AmbientState
import app.railcast.feature.ambient.AmbientSuppression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Muting a journey on the ambient layer (FR-7.4, "one-tap mute-this-journey").
 *
 * The rule that matters: mutes apply BEFORE resolution. Filtering after would
 * let a muted journey win the urgency sort and then leave the surface empty —
 * or worse, resurrect it on the next refresh, which users experience as
 * "I turned it off and it came back".
 */
class AmbientSuppressionTest {

    private fun journey(no: String, minutes: Int = 60, cancelled: Boolean = false) =
        AmbientJourney(
            trainNo = no,
            trainName = "Train $no",
            statusWord = "on time",
            minutesUntilRelevant = minutes,
            cancelled = cancelled,
        )

    @Test fun an_unmuted_list_passes_through_untouched() {
        val list = listOf(journey("1"), journey("2"))
        assertEquals(list, AmbientSuppression.applyMutes(list, emptySet()))
    }

    @Test fun a_muted_journey_is_removed() {
        val list = listOf(journey("1"), journey("2"))
        val kept = AmbientSuppression.applyMutes(list, setOf("1"))
        assertEquals(listOf("2"), kept.map { it.trainNo })
    }

    /** The whole point of filtering first. */
    @Test fun muting_the_most_urgent_promotes_the_next_one() {
        val list = listOf(journey("far", minutes = 600), journey("soon", minutes = 5))
        val state = Ambient.resolve(AmbientSuppression.applyMutes(list, setOf("soon"))) as AmbientState.Live
        assertEquals("far", state.journey.trainNo)
    }

    /** Muting a cancellation must work too — it outranks everything otherwise. */
    @Test fun muting_a_cancelled_journey_removes_it_from_the_surface() {
        val list = listOf(journey("running", minutes = 5), journey("dead", minutes = 900, cancelled = true))
        val state = Ambient.resolve(AmbientSuppression.applyMutes(list, setOf("dead"))) as AmbientState.Live
        assertEquals("running", state.journey.trainNo)
    }

    @Test fun muting_everything_leaves_the_invitation_not_a_crash() {
        val list = listOf(journey("1"), journey("2"))
        val state = Ambient.resolve(AmbientSuppression.applyMutes(list, setOf("1", "2")))
        assertEquals(AmbientState.Invitation, state)
    }

    // ── pruning ─────────────────────────────────────────────────────────────

    /** A mute must not outlive the journey and surprise the user next trip. */
    @Test fun mutes_for_departed_journeys_are_dropped() {
        assertEquals(setOf("2"), AmbientSuppression.pruneMutes(setOf("1", "2"), liveTrainNos = setOf("2", "3")))
    }

    @Test fun pruning_keeps_mutes_that_still_matter() {
        assertEquals(setOf("1", "2"), AmbientSuppression.pruneMutes(setOf("1", "2"), setOf("1", "2")))
    }

    @Test fun pruning_an_empty_set_is_a_no_op() {
        assertTrue(AmbientSuppression.pruneMutes(emptySet(), setOf("1")).isEmpty())
    }

    /** Re-saving a muted train later starts it unmuted, since the mute was pruned. */
    @Test fun a_pruned_mute_does_not_come_back() {
        val afterJourneyEnded = AmbientSuppression.pruneMutes(setOf("12951"), liveTrainNos = emptySet())
        assertTrue(afterJourneyEnded.isEmpty())
        val list = listOf(journey("12951"))
        assertEquals(list, AmbientSuppression.applyMutes(list, afterJourneyEnded))
    }
}
