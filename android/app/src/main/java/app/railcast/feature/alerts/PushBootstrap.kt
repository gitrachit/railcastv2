package app.railcast.feature.alerts

import android.content.Context
import app.railcast.core.net.PushTokenRegistrar
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Uploads the current FCM token once per app start (contracts §5) so the
 * server can reach a re-installed or restored device even when onNewToken
 * never fired. No-op when Firebase isn't configured — the app builds and runs
 * fully without google-services.json; push simply stays off, mirroring the
 * server's NoopSender.
 */
object PushBootstrap {
    fun register(context: Context, registrar: PushTokenRegistrar, scope: CoroutineScope) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            scope.launch { registrar.register(token) }
        }
    }
}
