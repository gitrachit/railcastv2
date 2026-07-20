package app.railcast.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.railcast.R
import app.railcast.core.design.RailcastIcons

/**
 * The single onboarding question — "what brought you here?" — mapped to the tab
 * we drop the user into (PRD §7: "one question → straight into value").
 *
 * Two intents share the Journeys tab since §7 was amended to three tabs:
 * tracking a train and checking a PNR both start there (a PNR is a journey you
 * hold a ticket for), and Track is no longer a destination of its own. The
 * [route] matches a Destination route in ui/RailcastApp so onboarding stays
 * decoupled from the nav graph. No login, no permission, no tutorial. [FR-10.5]
 */
enum class OnboardingIntent(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
    val route: String,
) {
    TRACK_TRAIN(R.string.onboarding_intent_track, R.string.onboarding_intent_track_sub, RailcastIcons.Train, "journeys"),
    CHECK_PNR(R.string.onboarding_intent_pnr, R.string.onboarding_intent_pnr_sub, RailcastIcons.Ticket, "journeys"),
    TRAINS_NEARBY(R.string.onboarding_intent_nearby, R.string.onboarding_intent_nearby_sub, RailcastIcons.Place, "find");

    companion object {
        /** Round-trips the persisted choice; unknown/legacy values fall back safely. */
        fun fromStored(name: String?): OnboardingIntent? =
            entries.firstOrNull { it.name == name }
    }
}
