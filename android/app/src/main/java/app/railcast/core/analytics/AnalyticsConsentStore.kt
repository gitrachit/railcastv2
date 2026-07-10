package app.railcast.core.analytics

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.analyticsDataStore: DataStore<Preferences> by preferencesDataStore(name = "railcast_analytics")

/** Persists the analytics opt-out locally (FR-11.3). Default on; the user can
 *  turn it off in settings and it's honoured immediately. */
class AnalyticsConsentStore(private val context: Context) {
    private val key = booleanPreferencesKey("analytics_enabled")

    val enabled: Flow<Boolean> = context.analyticsDataStore.data.map { it[key] ?: true }

    suspend fun setEnabled(enabled: Boolean) {
        context.analyticsDataStore.edit { it[key] = enabled }
    }
}
