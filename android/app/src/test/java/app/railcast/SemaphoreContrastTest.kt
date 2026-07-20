package app.railcast

import androidx.compose.ui.graphics.Color
import app.railcast.core.design.RailcastColors
import app.railcast.core.design.RailcastDarkColors
import app.railcast.core.design.RailcastLightColors
import app.railcast.core.design.RailcastSunlightColors
import app.railcast.core.design.StatusLevel
import app.railcast.core.design.statusColor
import app.railcast.core.design.statusSoftColor
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Palette-contrast gate (Semaphore, Part VIII.1). Pure JVM — WCAG 2.2 relative
 * luminance from sRGB. Every fact-bearing text pair must clear 4.5:1 (7:1 in
 * Sunlight); the brand accent, a tap affordance, clears the 3:1 UI threshold.
 *
 * IMPORTANT — test the pair that actually renders. StatusChip draws
 * `statusColor` text on `statusSoftColor`, and in Light/Dark that tint is
 * ALPHA-BLENDED over whatever sits beneath (surface or bg). Asserting the
 * signal colour against a plain surface overstates contrast by roughly one
 * stop and previously let every Light-theme chip ship below 4.5:1. So we
 * composite the tint over the worst-case backdrop and measure that.
 */
class SemaphoreContrastTest {

    private fun lin(c: Float): Float =
        if (c <= 0.03928f) c / 12.92f
        else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

    private fun luminance(c: Color): Float =
        0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)

    private fun ratio(a: Color, b: Color): Float {
        val la = luminance(a); val lb = luminance(b)
        val hi = maxOf(la, lb); val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** Source-over composite of [fg] (using its own alpha) onto opaque [bg]. */
    private fun over(fg: Color, bg: Color): Color = Color(
        red = fg.red * fg.alpha + bg.red * (1f - fg.alpha),
        green = fg.green * fg.alpha + bg.green * (1f - fg.alpha),
        blue = fg.blue * fg.alpha + bg.blue * (1f - fg.alpha),
        alpha = 1f,
    )

    private fun assertPair(name: String, fg: Color, bg: Color, min: Float) {
        val r = ratio(fg, bg)
        assertTrue("$name = ${"%.2f".format(r)}:1, need $min:1", r >= min)
    }

    private fun textPairs(c: RailcastColors, min: Float, label: String) {
        assertPair("$label ink/surface", c.ink, c.surface, min)
        assertPair("$label ink2/surface", c.ink2, c.surface, min)
        assertPair("$label ink3/surface", c.ink3, c.surface, min)
    }

    /**
     * Every StatusChip level, composited over both backdrops a chip can sit on.
     * This is the FR-10.2 surface: the word must be readable even for a user who
     * cannot use the colour at all.
     */
    private fun chipPairs(c: RailcastColors, min: Float, label: String) {
        val backdrops = listOf(c.surface, c.bg)
        for (level in StatusLevel.entries) {
            val fg = statusColor(level, c)
            val tint = statusSoftColor(level, c)
            for (backdrop in backdrops) {
                assertPair("$label chip $level on tint over ${backdrop.value}", fg, over(tint, backdrop), min)
            }
        }
    }

    /** The dark departure-board sub-palette (board hero, station board rows). */
    private fun boardPairs(c: RailcastColors, min: Float, label: String) {
        assertPair("$label boardGreen/board", c.boardGreen, c.board, min)
        assertPair("$label boardAmber/board", c.boardAmber, c.board, min)
        assertPair("$label boardInk/board", c.boardInk, c.board, min)
    }

    @Test fun light_text_pairs() {
        val c = RailcastLightColors
        textPairs(c, 4.5f, "light")
        assertPair("light brand/surface", c.brand, c.surface, 3f)
    }

    @Test fun dark_text_pairs() {
        val c = RailcastDarkColors
        textPairs(c, 4.5f, "dark")
        assertPair("dark brand/surface", c.brand, c.surface, 3f)
    }

    @Test fun sunlight_text_pairs_are_high_contrast() {
        val c = RailcastSunlightColors
        assertPair("sun ink/surface", c.ink, c.surface, 7f)
        assertPair("sun ink2/surface", c.ink2, c.surface, 7f)
        assertPair("sun ink3/surface", c.ink3, c.surface, 7f)
        assertPair("sun brand/surface", c.brand, c.surface, 4.5f)
    }

    @Test fun light_status_chips_are_readable() = chipPairs(RailcastLightColors, 4.5f, "light")

    @Test fun dark_status_chips_are_readable() = chipPairs(RailcastDarkColors, 4.5f, "dark")

    @Test fun sunlight_status_chips_are_high_contrast() = chipPairs(RailcastSunlightColors, 7f, "sun")

    @Test fun light_board_pairs() = boardPairs(RailcastLightColors, 4.5f, "light")

    @Test fun dark_board_pairs() = boardPairs(RailcastDarkColors, 4.5f, "dark")

    @Test fun sunlight_board_pairs() = boardPairs(RailcastSunlightColors, 7f, "sun")
}
