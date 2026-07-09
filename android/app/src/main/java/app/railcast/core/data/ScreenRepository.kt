package app.railcast.core.data

import app.railcast.core.db.CachedScreen
import app.railcast.core.db.ScreenCache
import app.railcast.core.net.ApiResult
import app.railcast.core.net.EnvelopeDto
import app.railcast.core.net.NetworkModule
import app.railcast.core.net.RailcastApi
import app.railcast.core.net.TrainScreen
import app.railcast.core.net.apiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import retrofit2.Response

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
