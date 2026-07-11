package app.railcast.directory

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nearest-station resolution for "trains near me" (FR-5.2). Pure — a haversine
 * scan over the bundled index (~9k stations is sub-millisecond), unit-tested
 * without Android. Stations lacking coordinates are skipped.
 */
object NearestStations {

    fun find(stations: List<Station>, lat: Double, lng: Double, limit: Int = 3): List<Station> =
        stations.asSequence()
            .filter { it.lat != null && it.lng != null }
            .sortedBy { haversineKm(lat, lng, it.lat!!, it.lng!!) }
            .take(limit)
            .toList()

    /** Great-circle distance in km (WGS84 mean radius). */
    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(sqrt(a))
    }
}
