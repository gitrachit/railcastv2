package app.railcast.feature.alerts

import app.railcast.RailcastApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Live FCM binding (FR-7.2/7.3, backlog 4.8). Deliberately paper-thin: parsing
 * (PushPayload), the notify decision (NotificationPolicy) and posting
 * (NotificationPoster) are pure, unit-tested pieces — this class only glues
 * them to Firebase. Without google-services.json Firebase never initializes
 * and this service never runs.
 */
class RailcastMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val container get() = (application as RailcastApplication).container

    override fun onNewToken(token: String) {
        scope.launch { container.pushTokens.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = PushPayload.parse(message.data) ?: return
        // Blocking is deliberate: FCM may reap the process once this method
        // returns, so the prefs read + post must complete inside it. Data
        // messages are rendered client-side, which is what lets opt-in, mute
        // and quiet hours apply even when the server already sent the push.
        val prefs = runBlocking { container.alertPrefs.prefs.first() }
        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (NotificationPolicy.shouldNotify(payload, prefs, nowMin)) {
            container.notifications.post(PushHandler.toSpec(payload), PushHandler.notificationId(payload))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
