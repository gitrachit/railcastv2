package app.railcast

import app.railcast.directory.FormatValidation
import app.railcast.directory.QueryClassifier
import app.railcast.directory.QueryClassifier.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One field, four meanings (wireframe W8). The user never tells the app what
 * kind of thing they typed — mode toggles are where errors live.
 */
class QueryClassifierTest {

    @Test fun five_digits_is_a_train() {
        assertEquals(Intent.TrainNumber("12951"), QueryClassifier.classify("12951"))
    }

    @Test fun ten_digits_is_a_pnr() {
        assertEquals(Intent.Pnr("4512882882"), QueryClassifier.classify("4512882882"))
    }

    @Test fun a_name_is_free_text() {
        assertEquals(Intent.FreeText("Goa Express"), QueryClassifier.classify("Goa Express"))
    }

    @Test fun nothing_typed_is_empty() {
        assertEquals(Intent.Empty, QueryClassifier.classify(""))
        assertEquals(Intent.Empty, QueryClassifier.classify("   "))
    }

    // ── routes ──────────────────────────────────────────────────────────────

    @Test fun to_separates_a_route() {
        assertEquals(Intent.Route("Delhi", "Goa"), QueryClassifier.classify("Delhi to Goa"))
    }

    @Test fun routes_accept_arrows_and_devanagari() {
        assertEquals(Intent.Route("Delhi", "Goa"), QueryClassifier.classify("Delhi → Goa"))
        assertEquals(Intent.Route("Delhi", "Goa"), QueryClassifier.classify("Delhi -> Goa"))
        assertEquals(Intent.Route("दिल्ली", "गोवा"), QueryClassifier.classify("दिल्ली से गोवा"))
    }

    /**
     * The ordering trap: "12951 to Goa" must be a route, not a train number.
     * Checking digits first would misread it and send the user to the wrong
     * screen with no way to tell why.
     */
    @Test fun a_route_beats_a_number_that_looks_like_a_train() {
        assertEquals(Intent.Route("12951", "Goa"), QueryClassifier.classify("12951 to Goa"))
    }

    @Test fun a_half_typed_route_is_still_free_text() {
        assertTrue(QueryClassifier.classify("Delhi to ") is Intent.FreeText)
        assertTrue(QueryClassifier.classify("Delhi to") is Intent.FreeText)
    }

    // ── partial input never blocks ──────────────────────────────────────────

    @Test fun a_partial_number_still_searches() {
        // "229" should match train numbers in the directory while typing —
        // classification ranks intent, it does not gatekeep.
        assertEquals(Intent.FreeText("229"), QueryClassifier.classify("229"))
    }

    @Test fun separators_in_a_pnr_are_tolerated() {
        assertEquals(Intent.Pnr("4512882882"), QueryClassifier.classify("451-288-2882"))
    }

    // ── inline hints (FR-1.5: guidance while typing, never an error after) ───

    @Test fun a_short_number_hints_at_a_train_length() {
        assertEquals(FormatValidation.Msg.TRAIN_LENGTH, QueryClassifier.hint("129"))
    }

    @Test fun a_number_between_the_two_shapes_hints_at_a_pnr() {
        assertEquals(FormatValidation.Msg.PNR_LENGTH, QueryClassifier.hint("1295100"))
    }

    @Test fun valid_shapes_say_nothing() {
        assertNull(QueryClassifier.hint("12951"))
        assertNull(QueryClassifier.hint("4512882882"))
    }

    @Test fun names_are_never_scolded_for_format() {
        assertNull(QueryClassifier.hint("Goa Express"))
        assertNull(QueryClassifier.hint(""))
    }
}
