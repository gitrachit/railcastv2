package app.railcast

import app.railcast.core.design.Confidence
import app.railcast.core.design.confidencePrefix
import app.railcast.core.design.describe
import app.railcast.core.design.displayValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The honesty rules of the Confidence System, as behaviour rather than intent
 * (FR-11.1, FR-2.2). Pure functions so they run on the JVM without Compose.
 */
class ConfidenceValueTest {

    @Test fun unknown_renders_em_dash_never_zero_or_blank() {
        assertEquals("—", displayValue("0", Confidence.UNKNOWN))
        assertEquals("—", displayValue("", Confidence.UNKNOWN))
        assertEquals("—", displayValue("4", Confidence.UNKNOWN))
    }

    @Test fun known_states_render_their_value_verbatim() {
        for (c in listOf(Confidence.CERTAIN, Confidence.ESTIMATED, Confidence.STALE)) {
            assertEquals("16:49", displayValue("16:49", c))
        }
    }

    @Test fun only_estimates_carry_the_tilde() {
        assertEquals("~", confidencePrefix(Confidence.ESTIMATED))
        assertEquals("", confidencePrefix(Confidence.CERTAIN))
        assertEquals("", confidencePrefix(Confidence.STALE))
        assertEquals("", confidencePrefix(Confidence.UNKNOWN))
    }

    /** A blind user cannot see the dashed edge, so the word must be in the text. */
    @Test fun screen_readers_hear_the_epistemic_state() {
        assertEquals("estimated arrival at Bhopal", describe("16:49", Confidence.ESTIMATED, "arrival at Bhopal"))
        assertEquals("arrival at Bhopal", describe("16:49", Confidence.CERTAIN, "arrival at Bhopal"))
        assertEquals("arrival at Bhopal, last known", describe("16:49", Confidence.STALE, "arrival at Bhopal"))
        assertEquals("platform not available", describe("", Confidence.UNKNOWN, "platform"))
    }

    @Test fun describe_falls_back_to_the_value_when_unlabelled() {
        assertEquals("estimated 16:49", describe("16:49", Confidence.ESTIMATED, null))
        assertEquals("value not available", describe("", Confidence.UNKNOWN, null))
    }
}
