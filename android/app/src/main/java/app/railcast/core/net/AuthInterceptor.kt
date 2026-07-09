package app.railcast.core.net

import okhttp3.Interceptor
import okhttp3.Response

/** Supplies the current device bearer token (null before the first mint). */
fun interface TokenProvider {
    fun currentToken(): String?
}

/**
 * Attaches `Authorization: Bearer <deviceToken>` to every request except the
 * token-minting call itself (contracts §7). No token yet → request goes out
 * bare and the server answers UNAUTHORIZED, which the caller handles by minting.
 */
class AuthInterceptor(private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokens.currentToken()
        if (token == null || request.url.encodedPath.endsWith("/auth/device")) {
            return chain.proceed(request)
        }
        val authed = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }
}
