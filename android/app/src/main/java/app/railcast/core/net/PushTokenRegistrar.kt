package app.railcast.core.net

/**
 * Uploads the device's FCM registration token to the BFF (contracts §5,
 * POST /device/push-token) so the watcher fan-out can reach this install.
 * Auth-first: DeviceSession single-flights the mint, so calling this from a
 * cold push-service process (no activity alive) is safe.
 */
class PushTokenRegistrar(
    private val api: RailcastApi,
    private val session: DeviceSession,
) {
    suspend fun register(fcmToken: String): Boolean {
        if (fcmToken.isEmpty()) return false
        session.ensureToken(api) ?: return false
        val result = apiResult({ NetworkModule.parseError(it) }) {
            api.registerPushToken(PushTokenRequest(fcmToken))
        }
        return result is ApiResult.Ok
    }
}
