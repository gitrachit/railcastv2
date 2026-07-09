package app.railcast

import app.railcast.core.net.AuthInterceptor
import app.railcast.core.net.TokenProvider
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Minimal OkHttp chain that records the request it was asked to proceed with. */
private class RecordingChain(private val request: Request) : Interceptor.Chain {
    var proceeded: Request? = null
    override fun request() = request
    override fun proceed(request: Request): Response {
        proceeded = request
        return Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("".toResponseBody(null)).build()
    }
    override fun connection() = null
    override fun call() = throw UnsupportedOperationException()
    override fun connectTimeoutMillis() = 0
    override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    override fun readTimeoutMillis() = 0
    override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    override fun writeTimeoutMillis() = 0
    override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
}

class AuthInterceptorTest {
    private fun run(token: String?, url: String): Request {
        val chain = RecordingChain(Request.Builder().url(url).build())
        AuthInterceptor(TokenProvider { token }).intercept(chain)
        return chain.proceeded!!
    }

    @Test
    fun attachesBearerTokenToScreenRequests() {
        val req = run("dev-token-123", "https://api.railcast.app/screen/train/22188")
        assertEquals("Bearer dev-token-123", req.header("Authorization"))
    }

    @Test
    fun doesNotAttachToAuthDeviceCall() {
        val req = run("dev-token-123", "https://api.railcast.app/auth/device")
        assertNull(req.header("Authorization"))
    }

    @Test
    fun noTokenMeansNoHeader() {
        val req = run(null, "https://api.railcast.app/screen/train/22188")
        assertNull(req.header("Authorization"))
    }
}
