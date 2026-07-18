package app.railcast

import app.railcast.core.format.IsoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the ISO display formatting — the raw-timestamp bug that made the board
 * read like a database dump on-device. clock() must show the wall time; the
 * age label must be human and timezone-correct.
 */
class IsoTimeTest {
    @Test fun `clock shows the wall-clock time of an offset timestamp`() {
        assertEquals("02:50", IsoTime.clock("2026-07-18T02:50:00+05:30"))
        assertEquals("16:15", IsoTime.clock("2026-07-17T16:15:00+05:30"))
    }

    @Test fun `clock is blank for null or garbage`() {
        assertEquals("", IsoTime.clock(null))
        assertEquals("", IsoTime.clock("not-a-time"))
    }

    @Test fun `epoch millis handles Z and explicit offsets`() {
        // 2026-07-18T00:00:00Z == 1784332800000
        assertEquals(1_784_332_800_000L, IsoTime.epochMillis("2026-07-18T00:00:00Z"))
        // 05:30 local at +05:30 is the same instant as 00:00Z.
        assertEquals(1_784_332_800_000L, IsoTime.epochMillis("2026-07-18T05:30:00+05:30"))
    }

    @Test fun `epoch millis rejects a non-instant`() {
        assertNull(IsoTime.epochMillis("2026-07-18"))
    }

    @Test fun `friendly date is a readable weekday plus day plus month`() {
        assertEquals("Sat, 18 Jul", IsoTime.friendlyDate("2026-07-18", java.util.Locale.US))
        assertEquals("not-a-date", IsoTime.friendlyDate("not-a-date", java.util.Locale.US))
    }

    @Test fun `age buckets by how old the instant is`() {
        val now = IsoTime.epochMillis("2026-07-18T12:00:00Z")!!
        assertEquals(IsoTime.Age.JustNow, IsoTime.age("2026-07-18T11:59:40Z", now)) // 20s
        assertEquals(IsoTime.Age.Minutes(6), IsoTime.age("2026-07-18T11:54:00Z", now)) // 6m
        assertEquals(IsoTime.Age.Hours(2), IsoTime.age("2026-07-18T10:00:00Z", now)) // 2h
        assertEquals(IsoTime.Age.Days(3), IsoTime.age("2026-07-15T11:00:00Z", now)) // ~3d, stale offline
        assertEquals(IsoTime.Age.Unknown, IsoTime.age(null, now))
    }
}
