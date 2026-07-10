package app.railcast

import app.railcast.core.data.pnrScreenKey
import app.railcast.feature.pnr.maskPnr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PnrFormatTest {

    @Test fun `mask keeps only the last four digits`() {
        assertEquals("••••2882", maskPnr("2458692882"))
    }

    @Test fun `mask strips non-digits before masking`() {
        assertEquals("••••2882", maskPnr("245-869 2882"))
    }

    @Test fun `mask handles short values without crashing`() {
        assertEquals("••••12", maskPnr("12"))
        assertEquals("••••", maskPnr(""))
    }

    @Test fun `cache key never contains the raw pnr and is stable`() {
        val raw = "2458692882"
        val key = pnrScreenKey(raw, salt = "install-salt")
        assertTrue(key.startsWith("pnr:"))
        assertFalse("raw PNR must not appear in the cache key", key.contains(raw))
        assertEquals(key, pnrScreenKey(raw, salt = "install-salt")) // deterministic per salt
    }

    @Test fun `different pnrs hash to different keys`() {
        assertNotEquals(pnrScreenKey("2458692882"), pnrScreenKey("2458692883"))
    }

    @Test fun `salt changes the key so an unsalted rainbow table is useless`() {
        val raw = "2458692882"
        assertNotEquals(pnrScreenKey(raw), pnrScreenKey(raw, salt = "a"))
        assertNotEquals(pnrScreenKey(raw, salt = "a"), pnrScreenKey(raw, salt = "b"))
    }
}
