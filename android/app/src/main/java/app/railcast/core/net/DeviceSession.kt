package app.railcast.core.net

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicReference

private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "railcast_device")

/** Persists the anonymous device token (contracts §7, FR-10.5 — no login). */
class DeviceTokenStore(private val context: Context) {
    private val key = stringPreferencesKey("device_token")
    suspend fun read(): String? = context.deviceDataStore.data.map { it[key] }.first()
    suspend fun write(token: String) {
        context.deviceDataStore.edit { it[key] = token }
    }
}

/**
 * Owns the current device token: exposes a synchronous TokenProvider for the
 * auth interceptor and mints one on demand. Minting is single-flighted so a
 * burst of screens on cold start triggers exactly one /auth/device call.
 */
class DeviceSession(
    private val store: DeviceTokenStore,
    private val appVersion: String,
) {
    private val token = AtomicReference<String?>(null)

    val tokenProvider = TokenProvider { token.get() }

    /** Loads a persisted token into memory (call once at startup). */
    suspend fun restore() {
        if (token.get() == null) token.set(store.read())
    }

    /** Ensures a valid token exists, minting via the API if needed. */
    suspend fun ensureToken(api: RailcastApi): String? {
        token.get()?.let { return it }
        restore()
        token.get()?.let { return it }

        val result = apiResult({ NetworkModule.parseError(it) }) {
            api.authDevice(DeviceAuthRequest(appVersion = appVersion))
        }
        if (result is ApiResult.Ok) {
            token.set(result.data.deviceToken)
            store.write(result.data.deviceToken)
        }
        return token.get()
    }
}
