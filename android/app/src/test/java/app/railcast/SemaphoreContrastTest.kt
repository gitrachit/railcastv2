package app.railcast

import androidx.compose.ui.graphics.Color
import app.railcast.core.design.RailcastColors
import app.railcast.core.design.RailcastDarkColors
import app.railcast.core.design.RailcastLightColors
import app.railcast.core.design.RailcastSunlightColors
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Palette-contrast gate (Semaphore, Part VIII.1). Pure JVM — WCAG relative
 * luminance from sRGB. Every fact-bearing text pair must clear 4.5:1 (7:1 in
 * Sunlight); the brand accent, a tap affordance, clears the 3:1 UI threshold.
 * A token tweak that silently fails a persona fails CI here.
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

    private fun assertPair(name: String, fg: Color, bg: Color, min: Float) {
        val r = ratio(fg, bg)
        assertTrue("$name = ${"%.2f".format(r)}:1, need $min:1", r >= min)
    }

    private fun textPairs(c: RailcastColors, min: Float, label: String) {
        assertPair("$label ink/surface", c.ink, c.surface, min)
        assertPair("$label ink2/surface", c.ink2, c.surface, min)
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
}
