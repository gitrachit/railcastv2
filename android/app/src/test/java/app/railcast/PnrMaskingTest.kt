package app.railcast

import app.railcast.core.data.MaskedPnr
import app.railcast.core.data.RawPnr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-4.3 / invariant 2: a PNR is masked in every response, log and UI. These
 * tests cover the leak paths a convention cannot.
 */
class PnrMaskingTest {

    private val raw = requireNonNull(RawPnr.parse("4512882882"))

    private fun <T> requireNonNull(v: T?): T = v!!

    @Test fun masking_keeps_only_the_last_four_digits() {
        assertEquals("••••2882", MaskedPnr.of("4512882882").value)
    }

    @Test fun parsing_requires_exactly_ten_digits() {
        assertNotNull(RawPnr.parse("4512882882"))
        assertNotNull(RawPnr.parse("451 288 2882")) // separators are tolerated
        assertNull(RawPnr.parse("451288288"))       // 9
        assertNull(RawPnr.parse("45128828821"))     // 11
        assertNull(RawPnr.parse(""))
    }

    /** The point of the type: interpolation into a log cannot leak the number. */
    @Test fun string_interpolation_yields_the_masked_form() {
        val logLine = "looking up $raw"
        assertEquals("looking up ••••2882", logLine)
        assertFalse("raw digits leaked into an interpolated string", logLine.contains("4512882882"))
    }

    @Test fun toString_never_exposes_the_raw_value() {
        assertFalse(raw.toString().contains("4512882882"))
        assertEquals("••••2882", raw.toString())
    }

    /** Revealing is possible — it must be, for the lookup — but explicit. */
    @Test fun reveal_returns_the_digits_for_the_request_path() {
        assertEquals("4512882882", raw.reveal())
    }

    @Test fun separators_are_stripped_before_the_request() {
        assertEquals("4512882882", requireNonNull(RawPnr.parse("451-288-2882")).reveal())
    }

    @Test fun masked_form_carries_no_full_digits_for_any_input() {
        for (input in listOf("4512882882", "1234567890", "0000000001")) {
            val masked = requireNonNull(RawPnr.parse(input)).masked().value
            assertFalse("mask leaked $input", masked.contains(input))
            assertTrue("mask lost its prefix", masked.startsWith("••••"))
            assertEquals(8, masked.length)
        }
    }
}
