package app.railcast.directory

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Ranked directory lookup — the seam consumers (e.g. Home) depend on, so they
 *  stay testable without the Android asset loader. */
interface TrainSearch {
    suspend fun search(query: String, limit: Int = 20): List<SearchResult>
}

/**
 * App-facing directory: loads the bundled index once (off the main thread) and
 * serves ranked, offline search. The index ships in assets and is swapped
 * atomically by delta updates (FR-1.2) — this reader treats it as opaque.
 */
class Directory(
    private val context: Context,
    private val assetPath: String = "directory/index.json",
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TrainSearch {
    private val loadLock = Mutex()
    @Volatile private var index: DirectoryIndex? = null

    /** Parse the asset on first use; cached thereafter. */
    suspend fun ensureLoaded(): DirectoryIndex {
        index?.let { return it }
        return loadLock.withLock {
            index ?: withContext(io) {
                val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                DirectoryIndex.parse(text)
            }.also { index = it }
        }
    }

    /** Ranked hits for [query], best first. Empty query → no results. */
    override suspend fun search(query: String, limit: Int): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val idx = ensureLoaded()
        return withContext(io) { DirectorySearch.search(idx, query, limit) }
    }
}
