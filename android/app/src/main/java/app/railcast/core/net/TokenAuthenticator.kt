package app.railcast.core.net

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Recovers from a mid-session 401 (e.g. the server rotated AUTH_TOKEN_SECRET,
 * invalidating every device token): re-mints via /auth/device and replays the
 * request once with the fresh token. Runs on an OkHttp worker thread, so the
 * blocking bridge into the suspend mint is safe. Requests that carried no
 * Authorization header (the mint call itself) are never retried, and after one
 * failed replay the 401 is surfaced to the caller (SWR then serves cache).
 */
class TokenAuthenticator(
    private val session: DeviceSession,
    private val api: () -> RailcastApi?,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val sent = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?: return null // unauthenticated call (token mint) — nothing to refresh
        if (attemptCount(response) >= MAX_ATTEMPTS) return null

        val apiClient = api() ?: return null
        val fresh = runBlocking { session.remint(apiClient, rejectedToken = sent) } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $fresh")
            .build()
    }

    private fun attemptCount(response: Response): Int {
        var r: Response? = response
        var n = 0
        while (r != null) {
            n++
            r = r.priorResponse
        }
        return n
    }

    private companion object {
        const val MAX_ATTEMPTS = 2 // original + one replay with a fresh token
    }
}
