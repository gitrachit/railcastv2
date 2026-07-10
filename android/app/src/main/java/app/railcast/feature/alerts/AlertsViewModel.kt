package app.railcast.feature.alerts

import app.railcast.core.analytics.AnalyticsConsentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Thin holder over the alert + analytics-consent stores for the Alerts screen
 *  (4.8, 5.5). */
class AlertsViewModel(
    private val store: AlertPrefsStore,
    private val consent: AnalyticsConsentStore,
    private val scope: CoroutineScope,
) {
    val prefs: Flow<AlertPrefs> = store.prefs
    val analyticsEnabled: Flow<Boolean> = consent.enabled

    fun setOptIn(type: AlertType, on: Boolean) = scope.launch { store.setOptIn(type, on) }
    fun setQuietHours(quiet: QuietHours) = scope.launch { store.setQuietHours(quiet) }
    fun setMuted(entityKey: String, muted: Boolean) = scope.launch { store.setMuted(entityKey, muted) }
    fun setAnalyticsEnabled(enabled: Boolean) = scope.launch { consent.setEnabled(enabled) }
}
