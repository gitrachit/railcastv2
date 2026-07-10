package app.railcast.feature.alerts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Thin holder over AlertPrefsStore for the Alerts screen (4.8). */
class AlertsViewModel(
    private val store: AlertPrefsStore,
    private val scope: CoroutineScope,
) {
    val prefs: Flow<AlertPrefs> = store.prefs

    fun setOptIn(type: AlertType, on: Boolean) = scope.launch { store.setOptIn(type, on) }
    fun setQuietHours(quiet: QuietHours) = scope.launch { store.setQuietHours(quiet) }
    fun setMuted(entityKey: String, muted: Boolean) = scope.launch { store.setMuted(entityKey, muted) }
}
