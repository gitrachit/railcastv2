package app.railcast

import app.railcast.core.net.AvailabilityCell
import app.railcast.core.net.FareCell
import app.railcast.core.net.PlanRow
import app.railcast.core.net.RowAvailability
import app.railcast.core.net.RowFare
import app.railcast.core.net.RowFareBreakdown
import app.railcast.feature.plan.PlanDates
import app.railcast.feature.plan.PlanQuota
import app.railcast.feature.plan.PlanSort
import app.railcast.feature.plan.PlanSorting
import app.railcast.feature.plan.TatkalTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun row(
    no: String,
    dep: String,
    avail: String? = null,
    fare: Double? = null,
) = PlanRow(
    no = no, name = "Train $no", dep = dep, arr = "23:59", durationMin = 60,
    classes = listOf("SL"), runsOn = List(7) { true },
    availability = avail?.let { AvailabilityCell.Ready(RowAvailability(it, it, null, true)) } ?: AvailabilityCell.Pending,
    fare = fare?.let { FareCell.Ready(RowFare(it, RowFareBreakdown(it, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))) } ?: FareCell.Pending,
)

class PlanLogicTest {

    @Test fun `addDays steps forward and back in UTC`() {
        assertEquals("2026-07-11", PlanDates.addDays("2026-07-10", 1))
        assertEquals("2026-07-09", PlanDates.addDays("2026-07-10", -1))
        assertEquals("2026-08-01", PlanDates.addDays("2026-07-31", 1)) // month rollover
    }

    @Test fun `today is the IST date, not UTC`() {
        // 20:00 UTC = 01:30 IST next day — an evening IST user must not be
        // floored to yesterday's date (Indian Railways runs on IST).
        val utcEvening = java.util.Date(java.time.Instant.parse("2026-07-10T20:00:00Z").toEpochMilli())
        assertEquals("2026-07-11", PlanDates.today(utcEvening))
        val utcMorning = java.util.Date(java.time.Instant.parse("2026-07-10T08:00:00Z").toEpochMilli())
        assertEquals("2026-07-10", PlanDates.today(utcMorning))
    }

    @Test fun `quota maps from code with a general fallback`() {
        assertEquals(PlanQuota.TATKAL, PlanQuota.fromCode("TQ"))
        assertEquals(PlanQuota.GENERAL, PlanQuota.fromCode(null))
        assertEquals(PlanQuota.GENERAL, PlanQuota.fromCode("??"))
        assertEquals(true, PlanQuota.TATKAL.isTatkal)
    }

    @Test fun `sort by departure orders chronologically`() {
        val rows = listOf(row("A", "14:00"), row("B", "06:30"), row("C", "09:15"))
        assertEquals(listOf("B", "C", "A"), PlanSorting.sort(rows, PlanSort.DEPARTURE).map { it.no })
    }

    @Test fun `sort by price puts cheapest first and pending last`() {
        val rows = listOf(row("A", "10:00", fare = 800.0), row("B", "10:00"), row("C", "10:00", fare = 300.0))
        assertEquals(listOf("C", "A", "B"), PlanSorting.sort(rows, PlanSort.PRICE).map { it.no })
    }

    @Test fun `tatkal band follows the classes — AC opens earlier on mixed rakes`() {
        assertEquals("ac", TatkalTiming.bandFor(listOf("2A", "SL")))
        assertEquals("ac", TatkalTiming.bandFor(listOf("cc")))
        assertEquals("nonac", TatkalTiming.bandFor(listOf("SL", "2S")))
    }

    @Test fun `tatkal opens 10 or 11 IST on the day before the journey`() {
        val tenIstPrevDay = java.time.Instant.parse("2026-07-11T04:30:00Z").toEpochMilli() // 10:00 IST
        assertEquals(tenIstPrevDay, TatkalTiming.opensAtMs("2026-07-12", "ac"))
        assertEquals(tenIstPrevDay + 3600_000, TatkalTiming.opensAtMs("2026-07-12", "nonac"))
        assertFalse(TatkalTiming.isOpen("2026-07-12", "ac", tenIstPrevDay - 1))
        assertTrue(TatkalTiming.isOpen("2026-07-12", "ac", tenIstPrevDay))
    }

    @Test fun `sort by seats ranks available over waitlist over pending`() {
        val rows = listOf(
            row("A", "10:00", avail = "waitlist"),
            row("B", "10:00"), // pending
            row("C", "10:00", avail = "available"),
            row("D", "10:00", avail = "rac"),
        )
        assertEquals(listOf("C", "D", "A", "B"), PlanSorting.sort(rows, PlanSort.SEATS).map { it.no })
    }
}
