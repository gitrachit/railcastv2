package app.railcast

import app.railcast.core.net.DeviceAuthRequest
import app.railcast.core.net.DeviceAuthResponse
import app.railcast.core.net.DeviceSession
import app.railcast.core.net.EnvelopeDto
import app.railcast.core.net.MetaDto
import app.railcast.core.net.RailcastApi
import app.railcast.core.net.TokenAuthenticator
import app.railcast.core.net.TokenStore
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response as RetrofitResponse

private class MemoryTokenStore(var stored: String? = null) : TokenStore {
    override suspend fun read(): String? = stored
    override suspend fun write(token: String) { stored = token }
}

/** Fake API: only authDevice is real; everything else is unused here. */
private class MintingApi(private val tokens: Iterator<String>) : RailcastApi {
    var mints = 0
    override suspend fun authDevice(body: DeviceAuthRequest): RetrofitResponse<EnvelopeDto<DeviceAuthResponse>> {
        mints++
        return RetrofitResponse.success(
            EnvelopeDto(ok = true, data = DeviceAuthResponse(tokens.next()), meta = MetaDto("t", false, 0)),
        )
    }
    override suspend fun trainScreen(trainNo: String, run: String) = error("unused")
    override suspend fun pnrScreen(pnr: String) = error("unused")
    override suspend fun stationScreen(code: String, hrs: Int) = error("unused")
    override suspend fun planScreen(from: String, to: String, date: String, quota: String) = error("unused")
    override suspend fun planRow(trainNo: String, from: String, to: String, date: String, cls: String, quota: String) = error("unused")
    override suspend fun createWatch(body: app.railcast.core.net.WatchRequest) = error("unused")
    override suspend fun registerPushToken(body: app.railcast.core.net.PushTokenRequest) = error("unused")
}

class DeviceSessionTest {

    @Test fun `ensureToken restores a persisted token without minting`() = runTest {
        val api = MintingApi(listOf("t1").iterator())
        val session = DeviceSession(MemoryTokenStore(stored = "persisted"), "1.0")
        assertEquals("persisted", session.ensureToken(api))
        assertEquals(0, api.mints)
    }

    @Test fun `ensureToken mints exactly once for a cold-start burst`() = runTest {
        val api = MintingApi(listOf("t1", "t2").iterator())
        val session = DeviceSession(MemoryTokenStore(), "1.0")
        val a = async { session.ensureToken(api) }
        val b = async { session.ensureToken(api) }
        assertEquals("t1", a.await())
        assertEquals("t1", b.await())
        assertEquals(1, api.mints)
    }

    @Test fun `remint replaces a rejected token and persists it`() = runTest {
        val store = MemoryTokenStore()
        val api = MintingApi(listOf("t1", "t2").iterator())
        val session = DeviceSession(store, "1.0")
        session.ensureToken(api) // t1
        assertEquals("t2", session.remint(api, rejectedToken = "t1"))
        assertEquals("t2", store.stored)
    }

    @Test fun `remint reuses a concurrent re-mint instead of minting again`() = runTest {
        val api = MintingApi(listOf("t1", "t2", "never").iterator())
        val session = DeviceSession(MemoryTokenStore(), "1.0")
        session.ensureToken(api) // t1
        session.remint(api, rejectedToken = "t1") // → t2
        // A second 401-holder arrives late with the OLD rejected token: no new mint.
        assertEquals("t2", session.remint(api, rejectedToken = "t1"))
        assertEquals(2, api.mints)
    }
}

class TokenAuthenticatorTest {

    private fun response401(request: Request, prior: Response? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("unauthorized")
            .apply { prior?.let(::priorResponse) }
            .build()

    private fun authedRequest(token: String): Request =
        Request.Builder().url("http://x/screen/train/12780").header("Authorization", "Bearer $token").build()

    @Test fun `replays once with a freshly minted token`() = runTest {
        val api = MintingApi(listOf("fresh").iterator())
        val session = DeviceSession(MemoryTokenStore(stored = "stale"), "1.0")
        session.ensureToken(api) // loads "stale" without minting
        val auth = TokenAuthenticator(session) { api }

        val retry = auth.authenticate(null, response401(authedRequest("stale")))
        assertEquals("Bearer fresh", retry?.header("Authorization"))
    }

    @Test fun `never retries the unauthenticated mint call itself`() {
        val session = DeviceSession(MemoryTokenStore(), "1.0")
        val auth = TokenAuthenticator(session) { null }
        val bare = Request.Builder().url("http://x/auth/device").build()
        assertNull(auth.authenticate(null, response401(bare)))
    }

    @Test fun `gives up after one replay instead of looping`() = runTest {
        val api = MintingApi(generateSequence { "again" }.iterator())
        val session = DeviceSession(MemoryTokenStore(stored = "stale"), "1.0")
        session.ensureToken(api)
        val auth = TokenAuthenticator(session) { api }

        val first = response401(authedRequest("stale"))
        val second = response401(authedRequest("again"), prior = first)
        assertNull(auth.authenticate(null, second))
    }
}
