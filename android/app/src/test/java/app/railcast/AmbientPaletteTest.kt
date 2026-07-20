package app.railcast

import app.railcast.R
import app.railcast.feature.ambient.AmbientJourney
import app.railcast.feature.ambient.AmbientPalette
import app.railcast.feature.ambient.AmbientPalette.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Colour selection on the ambient surfaces (FR-5.3, FR-10.2).
 *
 * Sunlight has no resource qualifier — it is a user choice, not a device
 * configuration — so the widget picks its tokens in code. That makes this a
 * decision worth testing rather than an inline `if` in a RemoteViews bind.
 */
class AmbientPaletteTest {

    private fun journey(cancelled: Boolean = false, delay: Int? = 0) = AmbientJourney(
        trainNo = "12951",
        trainName = "Rajdhani",
        statusWord = "on time",
        cancelled = cancelled,
        minutesUntilRelevant = delay,
    )

    // ── severity comes from state, never from the display string ────────────

    @Test fun a_cancellation_is_the_worst_case() {
        assertEquals(Severity.BAD, AmbientPalette.severity(journey(cancelled = true)))
    }

    @Test fun a_delay_warns() {
        assertEquals(Severity.WARN, AmbientPalette.severity(journey(delay = 12)))
    }

    @Test fun on_time_is_good() {
        assertEquals(Severity.GOOD, AmbientPalette.severity(journey(delay = 0)))
        assertEquals(Severity.GOOD, AmbientPalette.severity(journey(delay = null)))
    }

    /**
     * The bug this prevents: matching the localized status *word* would make
     * severity break the moment the user switched to Hindi, turning a late
     * train green.
     */
    @Test fun severity_ignores_the_status_word_entirely() {
        val hindi = journey(delay = 12).copy(statusWord = "१२ मिनट देरी")
        assertEquals(Severity.WARN, AmbientPalette.severity(hindi))
        val englishButOnTime = journey(delay = 0).copy(statusWord = "late-ish sounding text")
        assertEquals(Severity.GOOD, AmbientPalette.severity(englishButOnTime))
    }

    // ── token selection ─────────────────────────────────────────────────────

    @Test fun the_dark_board_uses_the_bright_signal_tokens() {
        assertEquals(R.color.rc_board_green, AmbientPalette.statusColor(Severity.GOOD, sunlight = false))
        assertEquals(R.color.rc_board_amber, AmbientPalette.statusColor(Severity.WARN, sunlight = false))
        assertEquals(R.color.rc_board_red, AmbientPalette.statusColor(Severity.BAD, sunlight = false))
    }

    @Test fun sunlight_uses_the_deepened_signal_tokens() {
        assertEquals(R.color.rc_sun_green, AmbientPalette.statusColor(Severity.GOOD, sunlight = true))
        assertEquals(R.color.rc_sun_amber, AmbientPalette.statusColor(Severity.WARN, sunlight = true))
        assertEquals(R.color.rc_sun_red, AmbientPalette.statusColor(Severity.BAD, sunlight = true))
    }

    /** If these ever collide, sunlight has silently stopped being applied. */
    @Test fun sunlight_and_ordinary_tokens_are_distinct() {
        for (severity in Severity.entries) {
            assertNotEquals(
                "sunlight token matches the ordinary one for $severity",
                AmbientPalette.statusColor(severity, sunlight = false),
                AmbientPalette.statusColor(severity, sunlight = true),
            )
        }
    }

    @Test fun the_board_background_follows_the_theme() {
        assertEquals(R.drawable.widget_bg, AmbientPalette.boardBackground(sunlight = false))
        assertEquals(R.drawable.widget_bg_sun, AmbientPalette.boardBackground(sunlight = true))
    }

    @Test fun sunlight_ink_is_the_high_contrast_pair() {
        assertEquals(R.color.rc_sun_ink, AmbientPalette.primaryInk(sunlight = true))
        assertEquals(R.color.rc_board_ink, AmbientPalette.primaryInk(sunlight = false))
    }
}
