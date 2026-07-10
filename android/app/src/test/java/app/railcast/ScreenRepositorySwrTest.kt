package app.railcast

import app.railcast.core.data.ScreenRepository
import app.railcast.core.db.CachedScreen
import app.railcast.core.db.ScreenCache
import app.railcast.core.net.DeviceAuthRequest
import app.railcast.core.net.DeviceAuthResponse
import app.railcast.core.net.EnvelopeDto
import app.railcast.core.net.MetaDto
import app.railcast.core.net.NetworkModule
import app.railcast.core.net.RailcastApi
import app.railcast.core.net.TrainScreen
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private fun trainScreen(name: String) = TrainScreen(
    trainNo = "22188",
    name = name,
    runDateResolved = "2026-07-08",
    runDateChoices = emptyList(),
    status = app.railcast.core.net.TrainStatus(
        state = "running",
        summary = "Running",
        lastUpdate = "2026-07-08T16:17:00+05:30",
    ),
    route = emptyList(),
)

/** In-memory ScreenCache — proves the SWR repo without Room/instrumentation. */
private class FakeCache(seed: CachedScreen? = null) : ScreenCache {
    private val map = HashMap<String, CachedScreen>()
    var puts = 0
    init { seed?.let { map[it.key] = it } }
    override suspend fun get(key: String) = map[key]
    override suspend fun put(entry: CachedScreen) { map[entry.key] = entry; puts++ }
    override suspend fun clear() = map.clear()
}

/** Configurable fake API. */
private class FakeApi(
    private val response: () -> Response<EnvelopeDto<TrainScreen>>,
) : RailcastApi {
    var calls = 0
    override suspend fun authDevice(body: DeviceAuthRequest) =
        Response.success(EnvelopeDto(ok = true, data = DeviceAuthResponse("tok"), meta = null))
    override suspend fun trainScreen(trainNo: String, run: String): Response<EnvelopeDto<TrainScreen>> {
        calls++
        return response()
    }
    override suspend fun pnrScreen(pnr: String): Response<EnvelopeDto<app.railcast.core.net.PnrScreen>> =
        Response.success(EnvelopeDto(ok = false, data = null, meta = null))
    override suspend fun createWatch(body: app.railcast.core.net.WatchRequest): Response<EnvelopeDto<app.railcast.core.net.WatchCreated>> =
        Response.success(EnvelopeDto(ok = true, data = app.railcast.core.net.WatchCreated("w1", "2026-07-20T00:00:00Z"), meta = app.railcast.core.net.MetaDto("2026-07-10T00:00:00Z", false, 0)))
}

private fun okEnvelope(screen: TrainScreen, ttl: Int = 45, stale: Boolean = false) =
    Response.success(
        EnvelopeDto(ok = true, data = screen, meta = MetaDto("2026-07-08T11:06:45Z", stale, ttl)),
    )

class ScreenRepositorySwrTest {
    private val json = NetworkModule.json
    private fun cachedEntry(screen: TrainScreen, ageMs: Long, ttl: Int) = CachedScreen(
        key = "train:22188:auto",
        json = json.encodeToString(TrainScreen.serializer(), screen),
        serverFetchedAt = "2026-07-07T22:00:00Z",
        cachedAtEpochMs = 1_000_000L - ageMs,
        ttlSeconds = ttl,
    )

    @Test
    fun coldCache_emitsLoadingThenFresh() = runTest {
        val api = FakeApi { okEnvelope(trainScreen("FRESH")) }
        val repo = ScreenRepository(api, FakeCache(), json) { 1_000_000L }

        val emissions = repo.trainScreen("22188").toList()

        assertEquals(2, emissions.size)
        assertNull(emissions[0].value) // cold: loading, no value yet
        assertTrue(emissions[0].loading)
        assertEquals("FRESH", emissions[1].value?.name) // then fresh
        assertFalse(emissions[1].loading)
        assertFalse(emissions[1].stale)
    }

    @Test
    fun warmCache_emitsCachedFirstThenFresh_andRewritesCache() = runTest {
        val cache = FakeCache(cachedEntry(trainScreen("CACHED"), ageMs = 10_000, ttl = 45))
        val api = FakeApi { okEnvelope(trainScreen("FRESH")) }
        val repo = ScreenRepository(api, cache, json) { 1_000_000L }

        val emissions = repo.trainScreen("22188").toList()

        assertEquals("CACHED", emissions[0].value?.name) // instant cached value first (SWR)
        assertTrue(emissions[0].loading)
        assertFalse(emissions[0].stale) // 10s old, ttl 45s → fresh
        assertEquals("FRESH", emissions[1].value?.name) // then network
        assertEquals(1, cache.puts) // fresh value written back
    }

    @Test
    fun cachedPastTtl_marksStale() = runTest {
        val cache = FakeCache(cachedEntry(trainScreen("OLD"), ageMs = 60_000, ttl = 45))
        val api = FakeApi { okEnvelope(trainScreen("FRESH")) }
        val repo = ScreenRepository(api, cache, json) { 1_000_000L }

        val first = repo.trainScreen("22188").toList().first()
        assertEquals("OLD", first.value?.name)
        assertTrue(first.stale) // 60s old, ttl 45s → stale
    }

    @Test
    fun networkFailure_keepsServingCache_withError() = runTest {
        val cache = FakeCache(cachedEntry(trainScreen("CACHED"), ageMs = 10_000, ttl = 45))
        val api = FakeApi {
            Response.error(503, """{"ok":false,"error":{"code":"UPSTREAM_DOWN","message":"down","retryable":true}}"""
                .toResponseBody("application/json".toMediaType()))
        }
        val repo = ScreenRepository(api, cache, json) { 1_000_000L }

        val emissions = repo.trainScreen("22188").toList()

        assertEquals("CACHED", emissions.last().value?.name) // degrade, never blank (FR-9.1)
        assertTrue(emissions.last().stale)
        assertEquals("UPSTREAM_DOWN", emissions.last().error?.code)
        assertEquals(0, cache.puts) // failed fetch doesn't overwrite cache
    }

    @Test
    fun coldCacheAndNetworkFailure_emitsErrorNoValue() = runTest {
        val api = FakeApi {
            Response.error(503, """{"ok":false,"error":{"code":"UPSTREAM_DOWN","message":"down","retryable":true}}"""
                .toResponseBody("application/json".toMediaType()))
        }
        val repo = ScreenRepository(api, FakeCache(), json) { 1_000_000L }

        val last = repo.trainScreen("22188").toList().last()
        assertNull(last.value)
        assertEquals("UPSTREAM_DOWN", last.error?.code)
    }
}
