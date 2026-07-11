package app.railcast

import app.railcast.directory.NearestStations
import app.railcast.directory.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun st(code: String, lat: Double?, lng: Double?) =
    Station(code, "Station $code", "", "", lat, lng)

class NearestStationsTest {

    // Real geography: from Bhopal, Rani Kamlapati (~4 km) beats Itarsi (~70 km)
    // beats New Delhi (~600 km).
    private val stations = listOf(
        st("NDLS", 28.6428, 77.2197),
        st("ET", 22.6083, 77.7668),
        st("RKMP", 23.2219, 77.4395),
        st("NOCOORD", null, null),
    )

    @Test fun `orders by great-circle distance from the fix`() {
        val fromBhopal = NearestStations.find(stations, lat = 23.2599, lng = 77.4126)
        assertEquals(listOf("RKMP", "ET", "NDLS"), fromBhopal.map { it.code })
    }

    @Test fun `stations without coordinates are skipped, never crash`() {
        val result = NearestStations.find(stations, 23.0, 77.0, limit = 10)
        assertTrue(result.none { it.code == "NOCOORD" })
        assertEquals(3, result.size)
    }

    @Test fun `respects the limit`() {
        assertEquals(1, NearestStations.find(stations, 23.0, 77.0, limit = 1).size)
    }

    @Test fun `haversine sanity — Bhopal to Delhi is roughly 600 km`() {
        val km = NearestStations.haversineKm(23.2599, 77.4126, 28.6428, 77.2197)
        assertTrue("got $km", km in 550.0..650.0)
    }
}
