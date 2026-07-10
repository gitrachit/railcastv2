package app.railcast.feature.home

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The saved trains shown as live cards on Home. An ordered seam so Home logic
 *  is unit-testable with an in-memory fake. Most-recently-saved first. */
interface SavedTrains {
    val trains: Flow<List<String>>
    suspend fun add(trainNo: String)
    suspend fun remove(trainNo: String)
}

private val Context.savedDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "railcast_saved")

/**
 * Persists saved train numbers locally (no account — FR-10.5). Stored as a
 * newline-joined ordered list so insertion order (most recent first) survives
 * relaunch; a Set would lose it.
 */
class SavedStore(private val context: Context) : SavedTrains {
    private val key = stringPreferencesKey("saved_trains")

    override val trains: Flow<List<String>> =
        context.savedDataStore.data.map { prefs ->
            prefs[key]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        }

    override suspend fun add(trainNo: String) = mutate { current ->
        listOf(trainNo) + current.filterNot { it == trainNo } // move-to-front, no dupes
    }

    override suspend fun remove(trainNo: String) = mutate { current ->
        current.filterNot { it == trainNo }
    }

    private suspend fun mutate(transform: (List<String>) -> List<String>) {
        context.savedDataStore.edit { prefs ->
            val current = prefs[key]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
            prefs[key] = transform(current).joinToString("\n")
        }
    }
}
