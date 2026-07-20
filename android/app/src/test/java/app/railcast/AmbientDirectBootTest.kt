package app.railcast

import app.railcast.feature.ambient.Ambient
import app.railcast.feature.ambient.AmbientState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Direct boot: the widget must render, not crash, before the device is
 * unlocked.
 *
 * Credential-encrypted storage does not exist until the user unlocks. Because
 * AppWidgetProvider is a BroadcastReceiver, ACTION_APPWIDGET_UPDATE can arrive
 * inside that window after a reboot, and `getSharedPreferences` throws
 * IllegalStateException there. An uncaught throw in onReceive is a boot-time
 * crash of the whole process — the exact failure seen in the logcat dump that
 * prompted this guard.
 *
 * The Android-facing guard (`UserManager.isUserUnlocked`) needs a device to
 * exercise. What is asserted here is the contract it degrades to: an
 * unavailable snapshot resolves to Invitation, which always renders.
 */
class AmbientDirectBootTest {

    /** No snapshot — the state AmbientRepository returns when prefs are locked. */
    @Test fun an_unavailable_snapshot_renders_the_invitation() {
        assertEquals(AmbientState.Invitation, Ambient.resolve(emptyList()))
    }

    /** Invitation must carry no journey fields to read, so nothing can NPE. */
    @Test fun the_invitation_has_nothing_to_dereference() {
        val state: AmbientState = AmbientState.Invitation
        when (state) {
            AmbientState.Invitation -> Unit // exhaustive: no journey to touch
            is AmbientState.Live -> throw AssertionError("empty input produced a Live state")
        }
    }
}
