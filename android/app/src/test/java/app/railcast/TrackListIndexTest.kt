package app.railcast

import app.railcast.core.net.NextStation
import app.railcast.core.net.Position
import app.railcast.core.net.TrainScreen
import app.railcast.core.net.TrainStatus
import app.railcast.feature.track.JourneyAnswer
import app.railcast.feature.track.TrackListIndex
import app.railcast.core.design.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun screen(
    state: String = "running",
    summary: String = "12 min late",
    position: Position? = null,
    next: NextStation? = null,
) = TrainScreen(
    trainNo = "12951",
    name = "Rajdhani",
    runDateResolved = "2026-07-20",
    runDateChoices = emptyList(),
    status = TrainStatus(state = state, summary = summary, lastUpdate = "t", nextStation = next),
    route = emptyList(),
    position = position,
)

/**
 * The pinned-answer layout (wireframe W5) moved the header and hero out of the
 * scrolling list. These lock the two things that quietly depended on the old
 * order.
 */
class TrackListIndexTest {

    private val somewhere = Position(
        kind = "interpolated",
        lat = 1.0,
        lng = 1.0,
        betweenCodes = listOf("KOTA", "BPL"),
        progress = 0.4,
    )

    @Test fun a_plain_running_train_puts_the_coach_section_first() {
        assertEquals(0, TrackListIndex.coachIndex(screen(), hasRunDateChoice = false))
    }

    @Test fun each_optional_section_above_it_shifts_it_down_by_one() {
        assertEquals(1, TrackListIndex.coachIndex(screen(state = "diverted"), hasRunDateChoice = false))
        assertEquals(1, TrackListIndex.coachIndex(screen(), hasRunDateChoice = true))
        assertEquals(1, TrackListIndex.coachIndex(screen(position = somewhere), hasRunDateChoice = false))
    }

    @Test fun the_sections_accumulate() {
        assertEquals(
            3,
            TrackListIndex.coachIndex(screen(state = "rescheduled", position = somewhere), hasRunDateChoice = true),
        )
    }

    /**
     * The regression this exists for: the old inline counter started at 2 to
     * account for a header and hero that are no longer in the list, so the jump
     * landed two items past the coach guide.
     */
    @Test fun the_pinned_header_and_answer_occupy_no_list_index() {
        assertEquals(0, TrackListIndex.coachIndex(screen(), hasRunDateChoice = false))
    }
}

/** The consequence line — Law 3: the answer, not the datum. */
class JourneyAnswerTest {

    @Test fun the_consequence_names_the_stop_and_marks_the_estimate() {
        val s = screen(next = NextStation("BPL", "Bhopal", etaScheduled = "16:37", etaActual = "16:49"))
        assertEquals("Bhopal ~16:49", JourneyAnswer.consequence(s.status))
    }

    @Test fun it_falls_back_to_the_scheduled_time() {
        val s = screen(next = NextStation("BPL", "Bhopal", etaScheduled = "16:37"))
        assertEquals("Bhopal ~16:37", JourneyAnswer.consequence(s.status))
    }

    /** Nothing ahead means nothing to promise — silence beats invention. */
    @Test fun no_next_stop_yields_no_consequence() {
        assertNull(JourneyAnswer.consequence(screen(state = "arrived").status))
    }

    @Test fun a_blank_eta_yields_no_consequence() {
        val s = screen(next = NextStation("BPL", "Bhopal", etaScheduled = ""))
        assertNull(JourneyAnswer.consequence(s.status))
    }

    @Test fun the_next_stop_is_always_a_projection_never_observed() {
        val s = screen(next = NextStation("BPL", "Bhopal", etaScheduled = "16:37"))
        assertEquals(Confidence.ESTIMATED, JourneyAnswer.confidence(s.status))
    }

    /** A cancelled run takes over the screen instead of pinning an answer. */
    @Test fun cancelled_runs_pin_nothing() {
        assertFalse(JourneyAnswer.hasAnswer(screen(state = "cancelled").status))
        assertTrue(JourneyAnswer.hasAnswer(screen().status))
    }

    @Test fun a_run_with_no_summary_pins_nothing() {
        assertFalse(JourneyAnswer.hasAnswer(screen(summary = "").status))
    }
}
