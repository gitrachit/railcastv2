package app.railcast.feature.track

import app.railcast.core.net.CoachGuide
import app.railcast.core.net.CoachOrder

/** Where on the platform a coach sits, so we can say "stand near the front /
 *  middle / rear" (FR-3.1) without needing exact metres. */
enum class PlatformZone { FRONT, MIDDLE, REAR }

/**
 * Pure coach-position logic (FR-3.1–3.3). The order is already reversal-correct
 * for its reference station (the server resolves that, FR-3.2); this just maps a
 * coach's slot to a platform zone and picks out GEN coaches. Unit-tested.
 */
object CoachLayout {

    /** Coaches left-to-right along the platform (position 1 = front). */
    fun ordered(guide: CoachGuide): List<CoachOrder> = guide.order.sortedBy { it.position }

    /** Front/middle/rear from a 1-based slot, robust for short rakes. */
    fun zone(position: Int, total: Int): PlatformZone {
        if (total <= 0) return PlatformZone.MIDDLE
        val ratio = (position - 0.5) / total
        return when {
            ratio < 1.0 / 3 -> PlatformZone.FRONT
            ratio > 2.0 / 3 -> PlatformZone.REAR
            else -> PlatformZone.MIDDLE
        }
    }

    fun zoneOf(guide: CoachGuide, coachNumber: String): PlatformZone? {
        val ordered = ordered(guide)
        val idx = ordered.indexOfFirst { it.number.equals(coachNumber, ignoreCase = true) }
        return if (idx < 0) null else zone(idx + 1, ordered.size)
    }

    /** All unreserved (GEN) coach numbers — the GEN-mode highlight set (FR-3.3). */
    fun genCoaches(guide: CoachGuide): Set<String> =
        guide.order.filter { it.type.equals("GEN", ignoreCase = true) }.map { it.number }.toSet()

    fun isGen(order: CoachOrder): Boolean = order.type.equals("GEN", ignoreCase = true)
}
