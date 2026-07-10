package app.railcast.core.data

import app.railcast.core.db.CachedScreen
import app.railcast.core.db.ScreenCache
import app.railcast.core.net.ApiResult
import app.railcast.core.net.EnvelopeDto
import app.railcast.core.net.NetworkModule
import app.railcast.core.net.PnrScreen
import app.railcast.core.net.RailcastApi
import app.railcast.core.net.TrainScreen
import app.railcast.core.net.WatchCreated
import app.railcast.core.net.WatchEntity
import app.railcast.core.net.WatchRequest
import app.railcast.core.net.apiResult
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import retrofit2.Response

/** SWR cache key for a PNR: a SHA-256 of the raw value so the raw PNR is NEVER
 *  written to disk — Room only ever sees the hash, while the cached payload
 *  carries the masked form (FR-4.3, invariant 2). */
fun pnrScreenKey(pnr: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(pnr.toByteArray())
    return "pnr:" + digest.joinToString("") { "%02x".format(it) }
}

/**
 * Stale-while-revalidate repository (PRD §6.4, android/CLAUDE.md): every screen
 * emits its Room-cached value first (instant, offline-safe — FR-9.1), then the
 * fresh network value. On network failure it keeps serving the cached copy with
 * an error attached rather than blanking the screen.
 */
class ScreenRepository(
    private val api: RailcastApi,
    private val cache: ScreenCache,
    private val json: Json = NetworkModule.json,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun trainScreen(trainNo: String, run: String = "auto"): Flow<Resource<TrainScreen>> =
        swr("train:$trainNo:$run", TrainScreen.serializer()) { api.trainScreen(trainNo, run) }

    /** PNR screen. [pnr] is the raw value (request path only); it is hashed for
     *  the cache key and never persisted (FR-4.3). */
    fun pnrScreen(pnr: String): Flow<Resource<PnrScreen>> =
        swr(pnrScreenKey(pnr), PnrScreen.serializer()) { api.pnrScreen(pnr) }

    /** Save = create a server-side chart watch. The raw [pnr] goes only in the
     *  request body; the server encrypts it at rest and drives the FR-4.2 push.
     *  The client keeps only the masked form + watchId. */
    suspend fun createChartWatch(pnr: String): ApiResult<WatchCreated> =
        apiResult({ NetworkModule.parseError(it) }) {
            api.createWatch(WatchRequest(type = "chart", entity = WatchEntity(kind = "pnr", pnr = pnr)))
        }

    private fun <T> swr(
        key: String,
        serializer: KSerializer<T>,
        fetch: suspend () -> Response<EnvelopeDto<T>>,
    ): Flow<Resource<T>> = flow {
        val cached = cache.get(key)
        val cachedValue = cached?.let { runCatching { json.decodeFromString(serializer, it.json) }.getOrNull() }
        if (cachedValue != null) {
            val stale = now() - cached.cachedAtEpochMs > cached.ttlSeconds * 1000L
            emit(Resource(cachedValue, cached.serverFetchedAt, stale, loading = true, error = null))
        } else {
            emit(Resource(null, null, stale = false, loading = true, error = null))
        }

        when (val result = apiResult({ NetworkModule.parseError(it) }, fetch)) {
            is ApiResult.Ok -> {
                cache.put(
                    CachedScreen(
                        key = key,
                        json = json.encodeToString(serializer, result.data),
                        serverFetchedAt = result.meta.fetchedAt,
                        cachedAtEpochMs = now(),
                        ttlSeconds = result.meta.ttlSeconds,
                    ),
                )
                emit(Resource(result.data, result.meta.fetchedAt, result.meta.stale, loading = false, error = null))
            }
            is ApiResult.Err -> {
                // Degrade to cache, never block (FR-9.1); surface the error too.
                emit(Resource(cachedValue, cached?.serverFetchedAt, stale = cachedValue != null, loading = false, error = result.error))
            }
        }
    }
}
