package app.railcast

import app.railcast.core.net.DeviceAuthRequest
import app.railcast.core.net.NetworkModule
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the /auth/device wire body (contracts §7). The app's Json config has
 * encodeDefaults=false, which silently DROPS default-valued fields — a
 * defaulted `platform` never reached the server, every mint failed
 * INVALID_INPUT, and the whole app 401'd in production. This test fails if
 * either required field goes missing from the serialized body again.
 */
class DeviceAuthWireTest {
    @Test
    fun mintBodyCarriesBothRequiredFields() {
        val body = NetworkModule.json.encodeToString(
            DeviceAuthRequest(platform = "android", appVersion = "0.1.0"),
        )
        assertEquals("""{"platform":"android","appVersion":"0.1.0"}""", body)
    }
}
