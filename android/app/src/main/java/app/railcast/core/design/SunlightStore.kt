package app.railcast.core.design

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sunlightDataStore: DataStore<Preferences> by preferencesDataStore(name = "railcast_display")

/**
 * Persists the sunlight (high-contrast) theme choice (FR-5.3).
 *
 * The palette shipped before any code path could select it, so the mode existed
 * on paper only. This is the switch. Local preference, no account (FR-10.5).
 *
 * Ambient-light AUTO-SUGGEST is a separate, later concern: proposing the mode
 * needs a light sensor reading and a dismissible prompt, and it must never
 * override an explicit choice. This store deliberately holds only the explicit
 * choice so that layer can be added without changing what "the user asked for"
 * means.
 */
class SunlightStore(private val context: Context) {
    private val key = booleanPreferencesKey("sunlight_mode")

    val sunlight: Flow<Boolean> = context.sunlightDataStore.data.map { it[key] ?: false }

    suspend fun setSunlight(enabled: Boolean) {
        context.sunlightDataStore.edit { it[key] = enabled }
    }
}
