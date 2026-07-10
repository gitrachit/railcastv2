package app.railcast

import app.railcast.core.net.StationRef
import app.railcast.core.net.StationTrain
import app.railcast.core.net.StationTrainTime
import app.railcast.feature.station.StationFilterState
import app.railcast.feature.station.StationFilters
import app.railcast.feature.station.stationStatusVisual
import app.railcast.core.design.StatusLevel
import org.junit.Assert.assertEquals
import org.junit.Test

private fun train(
    no: String,
    dest: String,
    status: String,
    classes: List<String>,
    delay: Int? = null,
) = StationTrain(
    no = no,
    name = "Train $no",
    source = StationRef("SRC", "Source"),
    dest = StationRef(dest.take(3).uppercase(), dest),
    platform = "1",
    arrival = null,
    departure = StationTrainTime("10:00", "10:0${delay ?: 0}", delay),
    status = status,
    classes = classes,
)

class StationFiltersTest {

    private val board = listOf(
        train("111", "Delhi", "ontime", listOf("2A", "SL")),
        train("222", "Mumbai", "late", listOf("3A", "SL"), delay = 20),
        train("333", "Delhi", "cancelled", listOf("2A")),
    )

    @Test fun `no filters returns everything`() {
        assertEquals(3, StationFilters.apply(board, StationFilterState()).size)
    }

    @Test fun `destination filter matches name or code, case-insensitive`() {
        val r = StationFilters.apply(board, StationFilterState(dest = "delhi"))
        assertEquals(setOf("111", "333"), r.map { it.no }.toSet())
    }

    @Test fun `class filter keeps only trains offering that class`() {
        val r = StationFilters.apply(board, StationFilterState(cls = "3A"))
        assertEquals(listOf("222"), r.map { it.no })
    }

    @Test fun `on-time-only drops late and cancelled`() {
        val r = StationFilters.apply(board, StationFilterState(onTimeOnly = true))
        assertEquals(listOf("111"), r.map { it.no })
    }

    @Test fun `filters compose together`() {
        val r = StationFilters.apply(board, StationFilterState(dest = "delhi", cls = "2A", onTimeOnly = true))
        assertEquals(listOf("111"), r.map { it.no }) // 333 is Delhi+2A but cancelled
    }

    @Test fun `classesOf lists distinct sorted classes`() {
        assertEquals(listOf("2A", "3A", "SL"), StationFilters.classesOf(board))
    }

    @Test fun `status visual pairs colour with an icon and escalates big delays`() {
        assertEquals(StatusLevel.GOOD, stationStatusVisual("ontime", null).first)
        assertEquals(StatusLevel.WARN, stationStatusVisual("late", 5).first)
        assertEquals(StatusLevel.BAD, stationStatusVisual("late", 40).first)
        assertEquals(StatusLevel.BAD, stationStatusVisual("cancelled", null).first)
    }
}
