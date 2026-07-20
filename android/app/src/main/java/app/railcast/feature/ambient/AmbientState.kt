package app.railcast.feature.ambient

import app.railcast.core.design.Confidence
import app.railcast.core.design.confidencePrefix

/**
 * The ambient layer's content model (direction-study.md — "the answer lives
 * outside the app").
 *
 * The widget and the live notification exist to answer two questions without a
 * launch: *how late is it* and *which platform*. That makes this the surface
 * where the §2 headline metric — time-to-answer — is not reduced but removed.
 *
 * The whole model is pure and Android-free, because everything above the
 * RemoteViews call is testable and none of it should depend on a device. What
 * cannot be tested here is whether the OS actually redraws the widget on the
 * cadence we ask for; that is genuinely device- and OEM-specific.
 */

/** One journey, reduced to what an ambient surface has room for. */
data class AmbientJourney(
    val trainNo: String,
    val trainName: String,
    /** The status word — always accompanies the colour, never replaced by it (FR-10.2). */
    val statusWord: String,
    /** The user's stop, when known. Null for a train-only watch. */
    val stationName: String? = null,
    /** Arrival at [stationName], "16:49". Null when unknown. */
    val eta: String? = null,
    val platform: String? = null,
    val confidence: Confidence = Confidence.ESTIMATED,
    val cancelled: Boolean = false,
    /** Minutes until the user-relevant event; drives ordering. Null = unknown. */
    val minutesUntilRelevant: Int? = null,
)

/** What an ambient surface should render right now. */
sealed interface AmbientState {
    /** No journeys — never blank; the surface always offers one action. */
    data object Invitation : AmbientState

    /** The journey the user most likely cares about, plus how many others exist. */
    data class Live(val journey: AmbientJourney, val otherCount: Int) : AmbientState
}

object Ambient {

    /**
     * Resolves what to show from the user's journeys.
     *
     * This is Direction B's state-resolution idea, relocated: a *home screen*
     * that changes shape between launches is disorienting, but a widget that
     * does is simply intelligent — changing with state is what an ambient
     * surface is expected to do.
     *
     * Ordering is by urgency, not recency: the journey nearest a
     * user-relevant event wins, because the whole point is answering before
     * the user thinks to ask. Cancellations outrank everything — bad news the
     * user has not seen is the most valuable thing the surface can carry
     * (FR-2.4).
     */
    fun resolve(journeys: List<AmbientJourney>): AmbientState {
        if (journeys.isEmpty()) return AmbientState.Invitation
        val chosen = journeys.minWith(
            compareBy<AmbientJourney> { if (it.cancelled) 0 else 1 }
                .thenBy { it.minutesUntilRelevant ?: Int.MAX_VALUE },
        )
        return AmbientState.Live(chosen, otherCount = journeys.size - 1)
    }

    /**
     * The consequence line — what the delay MEANS for this user, which is the
     * answer that ends the anxiety loop. "12 min late" is the train's fact;
     * "Bhopal ~16:49" is the user's answer.
     *
     * The `~` is load-bearing here. Dashed underlines and the breathe animation
     * do not exist on RemoteViews, so copy is the ONLY confidence channel that
     * survives to the ambient layer (design-system.md §4.1a).
     */
    fun consequenceLine(j: AmbientJourney): String? {
        val station = j.stationName ?: return null
        val eta = j.eta ?: return null
        return "$station ${confidencePrefix(j.confidence)}$eta"
    }

    /**
     * Platform, or an em-dash when unknown — never blank, never "0". A blank
     * reads as "no platform"; a zero reads as platform zero. Both are lies.
     */
    fun platformLabel(j: AmbientJourney): String = j.platform?.takeIf { it.isNotBlank() } ?: "—"

    /**
     * Freshness is MANDATORY on ambient surfaces. Widget redraw cadence is
     * throttled by the OS and varies by OEM, so a surface that cannot promise
     * recency must state the recency it has (FR-2.5).
     */
    fun freshnessLabel(ageSeconds: Long): String = when {
        ageSeconds < 60 -> "just now"
        ageSeconds < 3600 -> "${ageSeconds / 60} min ago"
        ageSeconds < 86_400 -> "${ageSeconds / 3600} h ago"
        else -> "${ageSeconds / 86_400} d ago"
    }
}
