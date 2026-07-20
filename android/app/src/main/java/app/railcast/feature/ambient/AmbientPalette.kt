package app.railcast.feature.ambient

import android.content.Context
import androidx.annotation.ColorRes
import app.railcast.R

/**
 * Colour selection for the ambient surfaces (FR-5.3, FR-10.2).
 *
 * Android picks Light or Dark tokens for us through resource qualifiers, but
 * **Sunlight has no qualifier** — it is an explicit user choice, not a device
 * configuration, so the framework has no way to know about it. A widget left to
 * the resource system therefore renders the ordinary palette to a user who
 * asked for the high-contrast one, which is exactly the person standing on a
 * platform in direct sun who cannot read it.
 *
 * So the widget reads the preference itself and picks the token.
 *
 * The status mapping is pure and tested, because "which colour is this status"
 * is the FR-10.2 decision and it should not live inline in a RemoteViews bind.
 * Colour remains the third signal regardless: the widget always prints the
 * status *word* beside it.
 */
object AmbientPalette {

    /** Severity, derived without colour so the mapping is testable. */
    enum class Severity { GOOD, WARN, BAD }

    /**
     * Reads the sunlight preference synchronously.
     *
     * SunlightStore is DataStore-backed and suspend-only, which a RemoteViews
     * bind cannot await, so the flag is mirrored into the same SharedPreferences
     * file the snapshot uses. Mirrored rather than moved: the app keeps
     * DataStore as its convention, and only the ambient layer reads the copy.
     */
    fun isSunlight(context: Context): Boolean =
        runCatching {
            val userManager = context.getSystemService(android.os.UserManager::class.java)
            if (userManager != null && !userManager.isUserUnlocked) return false
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SUNLIGHT, false)
        }.getOrDefault(false)

    /** Called by the app when the preference changes, to keep the mirror true. */
    fun setSunlight(context: Context, enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SUNLIGHT, enabled).apply()
        }
    }

    /**
     * A cancellation is BAD, a delay is WARN, anything else GOOD.
     *
     * Matching on the localized status *word* would break the moment the user
     * switched language, so severity comes from the journey's own flags and the
     * delay it carries — never from the display string.
     */
    fun severity(journey: AmbientJourney): Severity = when {
        journey.cancelled -> Severity.BAD
        (journey.minutesUntilRelevant ?: 0) > 0 -> Severity.WARN
        else -> Severity.GOOD
    }

    @ColorRes
    fun statusColor(journey: AmbientJourney, context: Context): Int =
        statusColor(severity(journey), isSunlight(context))

    /**
     * Pure: severity + theme → token.
     *
     * Sunlight uses the deepened `rc_sun_*` signals against a white board; the
     * ordinary board is dark, so it takes the bright board variants. Both sets
     * are contrast-gated — sunlight at 7:1, the rest at 4.5:1.
     */
    @ColorRes
    fun statusColor(severity: Severity, sunlight: Boolean): Int = when (severity) {
        Severity.GOOD -> if (sunlight) R.color.rc_sun_green else R.color.rc_board_green
        Severity.WARN -> if (sunlight) R.color.rc_sun_amber else R.color.rc_board_amber
        Severity.BAD -> if (sunlight) R.color.rc_sun_red else R.color.rc_board_red
    }

    /** The board surface drawable. */
    fun boardBackground(sunlight: Boolean): Int =
        if (sunlight) R.drawable.widget_bg_sun else R.drawable.widget_bg

    @ColorRes
    fun primaryInk(sunlight: Boolean): Int = if (sunlight) R.color.rc_sun_ink else R.color.rc_board_ink

    @ColorRes
    fun secondaryInk(sunlight: Boolean): Int = if (sunlight) R.color.rc_sun_ink3 else R.color.rc_board_ink

    private const val PREFS = "railcast_ambient"
    private const val KEY_SUNLIGHT = "sunlight"
}
