package app.railcast.feature.ambient

import android.content.Context

/**
 * Which journeys the user has muted on the ambient layer (FR-7.4).
 *
 * Separate from [AmbientRepository] on purpose: that holds *what is true*, this
 * holds *what the user asked not to see*. Mixing them would mean a data refresh
 * could quietly resurrect a muted journey, which is the failure users describe
 * as "I turned it off and it came back".
 *
 * Mute is per-journey and clears when that journey leaves the snapshot, so it
 * never becomes a setting the user has to remember to undo.
 */
object AmbientSuppression {

    private const val PREFS = "railcast_ambient_mute"
    private const val KEY_MUTED = "muted_train_nos"

    /** Null before unlock — same direct-boot hazard as the snapshot store. */
    private fun prefs(context: Context): android.content.SharedPreferences? {
        val userManager = context.getSystemService(android.os.UserManager::class.java)
        if (userManager != null && !userManager.isUserUnlocked) return null
        return runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull()
    }

    fun mute(context: Context, trainNo: String?) {
        if (trainNo.isNullOrBlank()) return
        val p = prefs(context) ?: return
        p.edit().putStringSet(KEY_MUTED, muted(context) + trainNo).apply()
    }

    fun unmute(context: Context, trainNo: String) {
        val p = prefs(context) ?: return
        p.edit().putStringSet(KEY_MUTED, muted(context) - trainNo).apply()
    }

    fun muted(context: Context): Set<String> =
        prefs(context)?.getStringSet(KEY_MUTED, emptySet())?.toSet() ?: emptySet()

    fun isMuted(context: Context, trainNo: String): Boolean = trainNo in muted(context)

    /**
     * Drops mutes for journeys that are no longer present, so a mute cannot
     * outlive the thing it silenced and surprise the user on a later trip.
     */
    fun prune(context: Context, liveTrainNos: Set<String>) {
        val p = prefs(context) ?: return
        val current = muted(context)
        val kept = pruneMutes(current, liveTrainNos)
        if (kept.size != current.size) p.edit().putStringSet(KEY_MUTED, kept).apply()
    }

    /** Applies the user's mutes to a journey list before anything renders it. */
    fun filter(context: Context, journeys: List<AmbientJourney>): List<AmbientJourney> =
        applyMutes(journeys, muted(context))

    // ── pure rules, tested without a device ─────────────────────────────────

    /** A muted journey is removed before resolution, so it cannot win the sort. */
    fun applyMutes(journeys: List<AmbientJourney>, muted: Set<String>): List<AmbientJourney> =
        if (muted.isEmpty()) journeys else journeys.filterNot { it.trainNo in muted }

    /** Keeps only mutes that still correspond to a live journey. */
    fun pruneMutes(muted: Set<String>, liveTrainNos: Set<String>): Set<String> =
        muted intersect liveTrainNos
}
