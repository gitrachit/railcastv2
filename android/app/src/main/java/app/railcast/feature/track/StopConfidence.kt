package app.railcast.feature.track

import app.railcast.core.design.Confidence

/**
 * What the app actually knows about a stop's time (FR-11.1, FR-2.2).
 *
 * A timeline row can show two different kinds of number and they must not look
 * alike:
 *
 *  - the **scheduled** time is the published timetable — certain, whatever the
 *    train is doing;
 *  - the **actual** time is observed for a stop the train has already reached,
 *    but for a stop still ahead it is a *projection* from current delay.
 *
 * Rendering a projected arrival exactly like an observed one is the specific
 * dishonesty FR-11.1 exists to prevent, and it is the one users notice: a
 * confident-looking future ETA that slips by twenty minutes reads as the app
 * lying, not as the railway running late.
 */
object StopConfidence {

    /** Contract states where the train has already been (api-contracts §1). */
    private val REACHED = setOf("passed", "departed")

    /**
     * @param state one of passed | departed | next | upcoming | destination
     * @param showsActual whether the row renders an actual time at all
     */
    fun forStop(state: String, showsActual: Boolean): Confidence = when {
        // Timetable only — the published schedule is a fact.
        !showsActual -> Confidence.CERTAIN
        // The train has been here; the time was observed.
        state in REACHED -> Confidence.CERTAIN
        // Still ahead: this is a projection from the current delay.
        else -> Confidence.ESTIMATED
    }
}
