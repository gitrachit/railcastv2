package app.railcast

import android.app.Application
import app.railcast.core.AppContainer

/**
 * Process-scoped composition root. The container must outlive any one activity:
 * RailcastMessagingService handles pushes with no activity alive, and activity
 * recreation (rotation, theme change) must not rebuild the Room/OkHttp
 * singletons. Lazy so a push-only process wake pays the cost only when used.
 */
class RailcastApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
