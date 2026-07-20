package app.railcast.feature.ambient

import android.content.Context
import app.railcast.core.design.Confidence

/**
 * The snapshot the ambient surfaces read.
 *
 * Deliberately SharedPreferences rather than the DataStore used everywhere
 * else: `AppWidgetProvider.onUpdate` is a synchronous broadcast callback, and
 * DataStore is suspend-only. Blocking on a coroutine inside a broadcast is how
 * widgets get killed for ANR. This is the one place the app's storage
 * convention is wrong, so it is the one place it is not followed.
 *
 * The app writes a snapshot whenever it has fresh journey data; the widget only
 * ever reads. That keeps the widget free of network, database and coroutine
 * work — it renders what the app last knew, and says how old that is.
 */
object AmbientRepository {

    private const val PREFS = "railcast_ambient"
    private const val KEY_COUNT = "count"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_PREFIX = "j0_"

    /**
     * Null before the device is unlocked.
     *
     * Credential-encrypted storage does not exist until the user unlocks, and
     * `getSharedPreferences` throws `IllegalStateException` if you ask for it
     * during direct boot. [JourneyWidgetProvider] is a BroadcastReceiver, so it
     * CAN be invoked in that window — `ACTION_APPWIDGET_UPDATE` after a reboot —
     * and an uncaught throw in `onReceive` is a boot-time crash, not a missing
     * widget.
     *
     * Device-protected storage would survive the window, but a journey snapshot
     * does not belong there: it names the user's train and destination, and
     * that storage is readable before authentication. Rendering the invitation
     * until unlock is the correct trade.
     */
    private fun prefs(context: Context): android.content.SharedPreferences? {
        val userManager = context.getSystemService(android.os.UserManager::class.java)
        if (userManager != null && !userManager.isUserUnlocked) return null
        return runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull()
    }

    /**
     * Persists the resolved journey. Only the chosen one is stored — the widget
     * shows one journey, so storing the rest would be dead weight in a file
     * read on every redraw.
     */
    fun writeSnapshot(context: Context, journeys: List<AmbientJourney>, nowMs: Long = System.currentTimeMillis()) {
        val editor = prefs(context)?.edit() ?: return
        editor.putInt(KEY_COUNT, journeys.size)
        editor.putLong(KEY_UPDATED_AT, nowMs)
        when (val state = Ambient.resolve(journeys)) {
            AmbientState.Invitation -> editor.remove(KEY_PREFIX + "no")
            is AmbientState.Live -> {
                val j = state.journey
                editor.putString(KEY_PREFIX + "no", j.trainNo)
                editor.putString(KEY_PREFIX + "name", j.trainName)
                editor.putString(KEY_PREFIX + "status", j.statusWord)
                editor.putString(KEY_PREFIX + "station", j.stationName)
                editor.putString(KEY_PREFIX + "eta", j.eta)
                editor.putString(KEY_PREFIX + "platform", j.platform)
                editor.putString(KEY_PREFIX + "confidence", j.confidence.name)
                editor.putBoolean(KEY_PREFIX + "cancelled", j.cancelled)
            }
        }
        editor.apply()
        JourneyWidgetProvider.refresh(context)
    }

    /** What the widget should render right now, from the last snapshot. */
    fun currentState(context: Context): AmbientState {
        val p = prefs(context) ?: return AmbientState.Invitation
        val no = p.getString(KEY_PREFIX + "no", null) ?: return AmbientState.Invitation
        val journey = AmbientJourney(
            trainNo = no,
            trainName = p.getString(KEY_PREFIX + "name", "").orEmpty(),
            statusWord = p.getString(KEY_PREFIX + "status", "").orEmpty(),
            stationName = p.getString(KEY_PREFIX + "station", null),
            eta = p.getString(KEY_PREFIX + "eta", null),
            platform = p.getString(KEY_PREFIX + "platform", null),
            confidence = runCatching {
                Confidence.valueOf(p.getString(KEY_PREFIX + "confidence", null) ?: "")
            }.getOrDefault(Confidence.ESTIMATED),
            cancelled = p.getBoolean(KEY_PREFIX + "cancelled", false),
        )
        return AmbientState.Live(journey, otherCount = (p.getInt(KEY_COUNT, 1) - 1).coerceAtLeast(0))
    }

    /** Age of the snapshot. Drives the mandatory freshness stamp (FR-2.5). */
    fun ageSeconds(context: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val updated = prefs(context)?.getLong(KEY_UPDATED_AT, 0L) ?: 0L
        if (updated <= 0L) return Long.MAX_VALUE / 1000
        return ((nowMs - updated).coerceAtLeast(0L)) / 1000
    }

    /** Journey ended or was removed — clear rather than leave a stale answer. */
    fun clear(context: Context) {
        prefs(context)?.edit()?.clear()?.apply()
        JourneyWidgetProvider.refresh(context)
    }
}
