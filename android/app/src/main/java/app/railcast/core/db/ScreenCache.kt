package app.railcast.core.db

/**
 * A cached screen payload: the raw JSON of the screen `data`, the server's
 * fetch time (for the freshness stamp, FR-2.5), and enough to compute
 * staleness (cachedAt + ttl). Keyed per screen entity.
 */
data class CachedScreen(
    val key: String,
    val json: String,
    val serverFetchedAt: String,
    val cachedAtEpochMs: Long,
    val ttlSeconds: Int,
)

/**
 * Per-key screen cache. Abstracted so the SWR repository is unit-testable
 * without Room/instrumentation; RoomScreenCache is the production impl.
 */
interface ScreenCache {
    suspend fun get(key: String): CachedScreen?
    suspend fun put(entry: CachedScreen)
    suspend fun clear()
}
