package app.railcast.core.net

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "railcast_device")

/** Persistence seam so DeviceSession is unit-testable on the JVM. */
interface TokenStore {
    suspend fun read(): String?
    suspend fun write(token: String)
}

/** Persists the anonymous device token (contracts §7, FR-10.5 — no login). */
class DeviceTokenStore(private val context: Context) : TokenStore {
    private val key = stringPreferencesKey("device_token")
    override suspend fun read(): String? = context.deviceDataStore.data.map { it[key] }.first()
    override suspend fun write(token: String) {
        context.deviceDataStore.edit { it[key] = token }
    }
}

/**
 * Owns the current device token: exposes a synchronous TokenProvider for the
 * auth interceptor and mints one on demand. Minting is single-flighted so a
 * burst of screens on cold start triggers exactly one /auth/device call.
 */
class DeviceSession(
    private val store: TokenStore,
    private val appVersion: String,
) {
    private val token = AtomicReference<String?>(null)
    private val mintLock = Mutex()

    val tokenProvider = TokenProvider { token.get() }

    /** Loads a persisted token into memory (call once at startup). */
    suspend fun restore() {
        if (token.get() == null) token.set(store.read())
    }

    /** Ensures a valid token exists, minting via the API if needed. */
    suspend fun ensureToken(api: RailcastApi): String? {
        token.get()?.let { return it }
        // Single-flight: a cold-start burst of screens mints exactly once.
        return mintLock.withLock {
            token.get()?.let { return it }
            restore()
            token.get()?.let { return it }

            val result = apiResult({ NetworkModule.parseError(it) }) {
                api.authDevice(DeviceAuthRequest(platform = "android", appVersion = appVersion))
            }
            if (result is ApiResult.Ok) {
                token.set(result.data.deviceToken)
                store.write(result.data.deviceToken)
            }
            token.get()
        }
    }

    /**
     * Replaces a server-rejected token (mid-session 401 — e.g. the server
     * rotated AUTH_TOKEN_SECRET). Single-flighted: when a burst of requests
     * 401s together, the first caller mints and the rest reuse its result.
     * Returns null when a fresh token could not be obtained.
     */
    suspend fun remint(api: RailcastApi, rejectedToken: String?): String? = mintLock.withLock {
        // Someone else already re-minted while we waited for the lock.
        token.get()?.takeIf { it != rejectedToken }?.let { return@withLock it }

        val result = apiResult({ NetworkModule.parseError(it) }) {
            api.authDevice(DeviceAuthRequest(platform = "android", appVersion = appVersion))
        }
        if (result is ApiResult.Ok) {
            token.set(result.data.deviceToken)
            store.write(result.data.deviceToken)
        }
        token.get()?.takeIf { it != rejectedToken }
    }
}
