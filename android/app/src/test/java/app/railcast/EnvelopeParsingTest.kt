package app.railcast

import app.railcast.core.net.ApiResult
import app.railcast.core.net.EnvelopeDto
import app.railcast.core.net.NetworkModule
import app.railcast.core.net.TrainScreen
import app.railcast.core.net.apiResult
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private const val OK_BODY = """
{"ok":true,"data":{"trainNo":"22188","name":"INTERCITY EXP","runDateResolved":"2026-07-08",
"runDateChoices":[{"runDate":"2026-07-08","label":"today","active":true}],
"status":{"state":"running","summary":"Running · on time","delayMin":0,
"lastStation":{"code":"ADTL","name":"Adhartal"},
"nextStation":{"code":"JBP","name":"Jabalpur","etaScheduled":"2026-07-08T16:27:00+05:30","etaActual":null},
"lastUpdate":"2026-07-08T16:17:00+05:30"},
"route":[{"code":"ADTL","name":"Adhartal","km":0,"day":1,"platform":"1",
"scheduled":{"arr":null,"dep":"2026-07-08T16:15:00+05:30"},"actual":{"arr":null,"dep":"2026-07-08T16:15:00+05:30"},
"delayMin":0,"state":"departed","lat":23.22,"lng":79.97}],
"position":{"kind":"interpolated","lat":23.19,"lng":79.94,"betweenCodes":["ADTL","JBP"],"progress":0.5},
"coach":null,"prediction":null},
"meta":{"fetchedAt":"2026-07-08T11:06:45.754Z","stale":false,"ttlSeconds":45}}
"""

class EnvelopeParsingTest {
    private fun okResponse(body: String) =
        Response.success(NetworkModule.json.decodeFromString(EnvelopeDto.serializer(TrainScreen.serializer()), body))

    @Test
    fun parsesSuccessEnvelopeToOk() = runTest {
        val result = apiResult<TrainScreen>({ NetworkModule.parseError(it) }) { okResponse(OK_BODY) }
        assertTrue(result is ApiResult.Ok)
        result as ApiResult.Ok
        assertEquals("INTERCITY EXP", result.data.name)
        assertEquals("running", result.data.status.state)
        assertEquals(0, result.data.status.delayMin)
        assertEquals(listOf("ADTL", "JBP"), result.data.position!!.betweenCodes)
        assertEquals(45, result.meta.ttlSeconds)
        assertEquals(false, result.meta.stale)
    }

    @Test
    fun mapsErrorEnvelopeFromNon2xxBody() = runTest {
        val errBody = """{"ok":false,"error":{"code":"NOT_FOUND","message":"unknown train","retryable":false}}"""
        val response: Response<EnvelopeDto<TrainScreen>> =
            Response.error(404, errBody.toResponseBody("application/json".toMediaType()))
        val result = apiResult<TrainScreen>({ NetworkModule.parseError(it) }) { response }

        assertTrue(result is ApiResult.Err)
        result as ApiResult.Err
        assertEquals("NOT_FOUND", result.error.code)
        assertEquals(404, result.httpStatus)
    }

    @Test
    fun networkExceptionBecomesRetryableUpstreamDown() = runTest {
        val result = apiResult<TrainScreen>({ NetworkModule.parseError(it) }) {
            throw java.io.IOException("no route to host")
        }
        assertTrue(result is ApiResult.Err)
        result as ApiResult.Err
        assertEquals("UPSTREAM_DOWN", result.error.code)
        assertTrue(result.error.retryable)
    }
}
