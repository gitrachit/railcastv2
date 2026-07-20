package app.railcast.feature.ambient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The Mute action on the live notification (FR-7.4, "one-tap
 * mute-this-journey").
 *
 * Mute must work from the notification itself. Requiring the user to open the
 * app, find the journey and disable it there would make the surface harder to
 * escape than to receive — which is the definition of the dark pattern §7
 * forbids.
 *
 * It clears the notification immediately rather than waiting for the next data
 * refresh: a mute that visibly does nothing for thirty seconds reads as broken,
 * and the user taps it again.
 */
class LiveJourneyMuteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // A throw here kills the process (see the direct-boot fix), and this
        // runs on a user tap, so it must not be able to fail loudly.
        runCatching {
            LiveJourneyNotification.clear(context)
            AmbientSuppression.mute(context, intent.getStringExtra(EXTRA_TRAIN_NO))
        }
    }

    companion object {
        const val EXTRA_TRAIN_NO = "train_no"
    }
}
