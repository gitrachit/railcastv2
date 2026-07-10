package app.railcast.feature.onboarding

import androidx.annotation.StringRes
import app.railcast.R

/**
 * The single onboarding question — "what brought you here?" — mapped to the tab
 * we drop the user into (PRD §7: "one question → straight into value"). The
 * [route] matches a Destination route in ui/RailcastApp so onboarding stays
 * decoupled from the nav graph. No login, no permission, no tutorial. [FR-10.5]
 */
enum class OnboardingIntent(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: String,
    val route: String,
) {
    TRACK_TRAIN(R.string.onboarding_intent_track, R.string.onboarding_intent_track_sub, "🧭", "track"),
    CHECK_PNR(R.string.onboarding_intent_pnr, R.string.onboarding_intent_pnr_sub, "🎫", "home"),
    TRAINS_NEARBY(R.string.onboarding_intent_nearby, R.string.onboarding_intent_nearby_sub, "📍", "station");

    companion object {
        /** Round-trips the persisted choice; unknown/legacy values fall back safely. */
        fun fromStored(name: String?): OnboardingIntent? =
            entries.firstOrNull { it.name == name }
    }
}
