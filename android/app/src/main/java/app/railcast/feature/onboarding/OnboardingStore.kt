package app.railcast.feature.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "railcast_onboarding")

/**
 * Remembers that first-run onboarding is done, plus the chosen intent so the
 * first entry lands on the right tab. Purely local — no account (FR-10.5).
 */
class OnboardingStore(private val context: Context) {
    private val doneKey = booleanPreferencesKey("onboarding_complete")
    private val intentKey = stringPreferencesKey("onboarding_intent")

    val completed: Flow<Boolean> =
        context.onboardingDataStore.data.map { it[doneKey] ?: false }

    val intent: Flow<OnboardingIntent?> =
        context.onboardingDataStore.data.map { OnboardingIntent.fromStored(it[intentKey]) }

    suspend fun complete(intent: OnboardingIntent) {
        context.onboardingDataStore.edit {
            it[doneKey] = true
            it[intentKey] = intent.name
        }
    }
}
