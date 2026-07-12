package app.railcast

import app.railcast.core.net.ApiErrorDto
import app.railcast.core.net.DeviceAuthRequest
import app.railcast.core.net.DeviceAuthResponse
import app.railcast.core.net.DeviceSession
import app.railcast.core.net.EmptyData
import app.railcast.core.net.EnvelopeDto
import app.railcast.core.net.MetaDto
import app.railcast.core.net.PlanRowHydration
import app.railcast.core.net.PlanScreen
import app.railcast.core.net.PnrScreen
import app.railcast.core.net.PushTokenRegistrar
import app.railcast.core.net.PushTokenRequest
import app.railcast.core.net.RailcastApi
import app.railcast.core.net.StationScreen
import app.railcast.core.net.TokenStore
import app.railcast.core.net.TrainScreen
import app.railcast.core.net.WatchCreated
import app.railcast.core.net.WatchRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private class MemTokenStore(var token: String? = null) : TokenStore {
    override suspend fun read(): String? = token
    override suspend fun write(token: String) {
        this.token = token
    }
}

private fun meta() = MetaDto(fetchedAt = "2026-07-11T00:00:00Z", stale = false, ttlSeconds = 0)

/** Fake API: mints on demand, records push-token uploads, scriptable outcome. */
private class RegistrarFakeApi(var acceptToken: Boolean = true) : RailcastApi {
    val uploaded = mutableListOf<String>()
    var mints = 0

    override suspend fun authDevice(body: DeviceAuthRequest): Response<EnvelopeDto<DeviceAuthResponse>> {
        mints++
        return Response.success(EnvelopeDto(ok = true, data = DeviceAuthResponse("minted-token"), meta = meta()))
    }

    override suspend fun registerPushToken(body: PushTokenRequest): Response<EnvelopeDto<EmptyData>> {
        uploaded += body.fcmToken
        return if (acceptToken) {
            Response.success(EnvelopeDto(ok = true, data = EmptyData(), meta = meta()))
        } else {
            Response.success(EnvelopeDto(ok = false, error = ApiErrorDto("INVALID_INPUT", "bad", retryable = false)))
        }
    }

    override suspend fun trainScreen(trainNo: String, run: String): Response<EnvelopeDto<TrainScreen>> = throw NotImplementedError()
    override suspend fun pnrScreen(pnr: String): Response<EnvelopeDto<PnrScreen>> = throw NotImplementedError()
    override suspend fun stationScreen(code: String, hrs: Int): Response<EnvelopeDto<StationScreen>> = throw NotImplementedError()
    override suspend fun planScreen(from: String, to: String, date: String, quota: String): Response<EnvelopeDto<PlanScreen>> = throw NotImplementedError()
    override suspend fun planRow(trainNo: String, from: String, to: String, date: String, cls: String, quota: String): Response<EnvelopeDto<PlanRowHydration>> = throw NotImplementedError()
    override suspend fun createWatch(body: WatchRequest): Response<EnvelopeDto<WatchCreated>> = throw NotImplementedError()
}

class PushTokenRegistrarTest {

    private fun registrar(api: RegistrarFakeApi, store: TokenStore = MemTokenStore()) =
        PushTokenRegistrar(api, DeviceSession(store, appVersion = "0.1.0"))

    @Test fun `uploads the token after ensuring auth, minting on a fresh install`() = runTest {
        val api = RegistrarFakeApi()
        assertTrue(registrar(api).register("fcm-abc"))
        assertEquals(1, api.mints) // fresh install: device token minted first
        assertEquals(listOf("fcm-abc"), api.uploaded)
    }

    @Test fun `reuses a persisted device token without re-minting`() = runTest {
        val api = RegistrarFakeApi()
        assertTrue(registrar(api, MemTokenStore(token = "existing")).register("fcm-abc"))
        assertEquals(0, api.mints)
        assertEquals(listOf("fcm-abc"), api.uploaded)
    }

    @Test fun `server rejection surfaces as false`() = runTest {
        val api = RegistrarFakeApi(acceptToken = false)
        assertFalse(registrar(api).register("fcm-abc"))
    }

    @Test fun `an empty token is never uploaded`() = runTest {
        val api = RegistrarFakeApi()
        assertFalse(registrar(api).register(""))
        assertTrue(api.uploaded.isEmpty())
        assertEquals(0, api.mints)
    }
}
