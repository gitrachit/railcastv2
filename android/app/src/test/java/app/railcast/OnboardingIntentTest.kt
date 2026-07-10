package app.railcast

import app.railcast.feature.onboarding.OnboardingIntent
import app.railcast.ui.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingIntentTest {

    @Test fun `every intent routes to a real destination`() {
        for (intent in OnboardingIntent.entries) {
            assertNotNull(
                "intent ${intent.name} → unknown route ${intent.route}",
                Destination.entries.firstOrNull { it.route == intent.route },
            )
        }
    }

    @Test fun `intents map to the expected tabs`() {
        assertEquals(Destination.TRACK.route, OnboardingIntent.TRACK_TRAIN.route)
        assertEquals(Destination.STATION.route, OnboardingIntent.TRAINS_NEARBY.route)
        assertEquals(Destination.HOME.route, OnboardingIntent.CHECK_PNR.route)
    }

    @Test fun `stored value round-trips`() {
        for (intent in OnboardingIntent.entries) {
            assertEquals(intent, OnboardingIntent.fromStored(intent.name))
        }
    }

    @Test fun `unknown or missing stored value is null, not a crash`() {
        assertNull(OnboardingIntent.fromStored(null))
        assertNull(OnboardingIntent.fromStored("LEGACY_REMOVED"))
        assertNull(OnboardingIntent.fromStored(""))
    }

    @Test fun `each intent carries its own icon and strings`() {
        val icons = OnboardingIntent.entries.map { it.icon }
        assertTrue("icons must be distinct", icons.toSet().size == icons.size)
    }
}
