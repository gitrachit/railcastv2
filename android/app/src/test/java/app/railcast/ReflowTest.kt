package app.railcast

import app.railcast.core.design.Reflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text reflow (FR-10.3, WCAG 1.4.4 / 1.4.10). The app previously clamped OS
 * font scale to 1.3x; these rules are what let the clamp be removed.
 */
class ReflowTest {

    @Test fun default_scale_keeps_fact_rows_on_one_line() {
        assertEquals(1, Reflow.maxLines(base = 1, fontScale = 1.0f))
        assertEquals(1, Reflow.maxLines(base = 1, fontScale = 1.15f))
    }

    @Test fun large_text_earns_a_second_line() {
        assertEquals(2, Reflow.maxLines(base = 1, fontScale = 1.3f))
        assertEquals(2, Reflow.maxLines(base = 1, fontScale = 1.5f))
    }

    /** 200% is the WCAG 1.4.4 bar and the reason the clamp had to go. */
    @Test fun wcag_200_percent_earns_a_third() {
        assertEquals(3, Reflow.maxLines(base = 1, fontScale = 2.0f))
    }

    @Test fun budget_respects_a_multiline_base() {
        assertEquals(2, Reflow.maxLines(base = 2, fontScale = 1.0f))
        assertEquals(4, Reflow.maxLines(base = 2, fontScale = 2.0f))
    }

    /** More room to scale must never mean less room to wrap. */
    @Test fun budget_is_monotonic_in_font_scale() {
        var previous = 0
        var scale = 0.8f
        while (scale <= 3.0f) {
            val lines = Reflow.maxLines(base = 1, fontScale = scale)
            assertTrue("maxLines shrank at scale $scale", lines >= previous)
            previous = lines
            scale += 0.05f
        }
    }

    @Test fun budget_is_bounded_so_one_row_cannot_eat_the_card() {
        assertEquals(3, Reflow.maxLines(base = 1, fontScale = 5.0f))
    }
}
