package app.railcast.feature.track

import app.railcast.core.design.Confidence
import app.railcast.core.net.TrainStatus

/**
 * The consequence line for the pinned answer block (wireframe W5).
 *
 * Design Law 3: the consequence outranks the datum. "12 min late" is the
 * train's fact; "Bhopal ~16:49" is the user's answer, and it is the line that
 * ends the anxiety loop — without it the user does the arithmetic themselves,
 * under stress, on a platform.
 *
 * Pure, so the honesty rules are tested rather than eyeballed.
 */
object JourneyAnswer {

    /**
     * Arrival at the next stop is a projection: the train is not there yet.
     *
     * When the screen is serving cached data, it is something weaker still —
     * not a projection from *current* delay but the last thing we knew, which
     * may be hours old. STALE says that; ESTIMATED would overclaim, implying a
     * live calculation that is not happening (FR-9.1, FR-11.1).
     */
    fun confidence(status: TrainStatus, stale: Boolean = false): Confidence = when {
        status.state == "cancelled" -> Confidence.UNKNOWN
        stale -> Confidence.STALE
        else -> Confidence.ESTIMATED
    }

    /**
     * "Bhopal ~16:49", or null when there is no next stop to speak of. A train
     * that has arrived, or never started, has no consequence to state, and
     * inventing one would be worse than saying nothing.
     */
    fun consequence(status: TrainStatus, stale: Boolean = false): String? {
        val next = status.nextStation ?: return null
        val eta = next.etaActual ?: next.etaScheduled
        if (eta.isBlank()) return null
        // Only a live projection earns the `~`. A stale value is marked by the
        // freshness strip beside it instead, so the two are never confused.
        val prefix = if (confidence(status, stale) == Confidence.ESTIMATED) "~" else ""
        return "${next.name} $prefix$eta"
    }

    /**
     * Whether a pinned answer is worth showing. A cancelled run takes over the
     * whole screen instead (FR-2.4), and a run with no summary has nothing to
     * pin.
     */
    fun hasAnswer(status: TrainStatus): Boolean =
        status.state != "cancelled" && status.summary.isNotBlank()
}
