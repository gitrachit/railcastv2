package app.railcast

import app.railcast.core.design.RailcastMono
import app.railcast.core.design.monoNumerals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the mono-numerals signature (blueprint §2.2): number runs get the mono
 * span, words don't. Verifying the span maths here is what lets the sweep land
 * across screens safely.
 */
class MonoNumeralsTest {
    private fun runs(text: String) =
        monoNumerals(text).let { a -> a.spanStyles.map { a.text.substring(it.start, it.end) } }

    @Test fun `only the number is styled in a mixed string`() {
        val a = monoNumerals("Running · 12 min late")
        assertEquals(1, a.spanStyles.size)
        val s = a.spanStyles[0]
        assertEquals("12", a.text.substring(s.start, s.end))
        assertEquals(RailcastMono, s.item.fontFamily)
    }

    @Test fun `times, arrows and platform numbers each get their own run`() {
        assertEquals(listOf("16:25", "16:37", "4"), runs("16:25  →  16:37   Platform 4"))
    }

    @Test fun `fares and codes keep their punctuation inside one run`() {
        assertEquals(listOf("2,470"), runs("₹2,470"))
        assertEquals(listOf("12951"), runs("Train 12951"))
    }

    @Test fun `a string with no digits gets no spans`() {
        assertTrue(monoNumerals("On time").spanStyles.isEmpty())
    }
}
