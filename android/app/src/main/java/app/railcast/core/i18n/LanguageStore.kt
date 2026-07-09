package app.railcast.core.i18n

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "railcast_settings")

/**
 * Persists the chosen language so switching survives relaunch (FR-10.1 "without
 * state loss"). No account required — a local preference (FR-10.5).
 */
class LanguageStore(private val context: Context) {
    private val key = stringPreferencesKey("app_language_tag")

    val language: Flow<AppLanguage> =
        context.settingsDataStore.data.map { AppLanguage.fromTag(it[key]) }

    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[key] = language.tag }
    }
}
