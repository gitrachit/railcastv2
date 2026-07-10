package app.railcast.feature.alerts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.alertsDataStore: DataStore<Preferences> by preferencesDataStore(name = "railcast_alerts")

/**
 * Persists notification preferences locally (FR-7.4) — no account. Every type is
 * on and quiet hours off by default (minimal, meaningful posture).
 */
class AlertPrefsStore(private val context: Context) {
    private fun optInKey(type: AlertType) = booleanPreferencesKey("optin_${type.name}")
    private val quietEnabled = booleanPreferencesKey("quiet_enabled")
    private val quietStart = intPreferencesKey("quiet_start")
    private val quietEnd = intPreferencesKey("quiet_end")
    private val muted = stringSetPreferencesKey("muted_entities")

    val prefs: Flow<AlertPrefs> = context.alertsDataStore.data.map { p ->
        AlertPrefs(
            optIn = AlertType.entries.associateWith { p[optInKey(it)] ?: true },
            quietHours = QuietHours(
                enabled = p[quietEnabled] ?: false,
                startMin = p[quietStart] ?: (22 * 60),
                endMin = p[quietEnd] ?: (7 * 60),
            ),
            mutedEntities = p[muted] ?: emptySet(),
        )
    }

    suspend fun setOptIn(type: AlertType, on: Boolean) {
        context.alertsDataStore.edit { it[optInKey(type)] = on }
    }

    suspend fun setQuietHours(quiet: QuietHours) {
        context.alertsDataStore.edit {
            it[quietEnabled] = quiet.enabled
            it[quietStart] = quiet.startMin
            it[quietEnd] = quiet.endMin
        }
    }

    suspend fun setMuted(entityKey: String, muted: Boolean) {
        context.alertsDataStore.edit { p ->
            val current = p[this.muted] ?: emptySet()
            p[this.muted] = if (muted) current + entityKey else current - entityKey
        }
    }
}
