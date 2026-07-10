package app.railcast.feature.alerts

/** Quiet-hours window in minutes-of-day [0,1440). Wraps past midnight when
 *  start > end. Disabled when not enabled or start == end. */
data class QuietHours(val enabled: Boolean = false, val startMin: Int = 22 * 60, val endMin: Int = 7 * 60) {
    fun isActiveAt(nowMin: Int): Boolean {
        if (!enabled || startMin == endMin) return false
        return if (startMin < endMin) nowMin in startMin until endMin
        else nowMin >= startMin || nowMin < endMin // wraps midnight
    }
}

/**
 * Local notification preferences (FR-7.4). The server also enforces these, but
 * the client renders FCM data messages itself, so it applies opt-in + quiet
 * hours + mute-this-journey before posting. Default posture is minimal: every
 * type on, quiet hours off until the user sets them.
 */
data class AlertPrefs(
    val optIn: Map<AlertType, Boolean> = AlertType.entries.associateWith { true },
    val quietHours: QuietHours = QuietHours(),
    val mutedEntities: Set<String> = emptySet(),
) {
    fun isOptedIn(type: AlertType): Boolean = optIn[type] ?: true
    fun isMuted(entityKey: String): Boolean = entityKey in mutedEntities
}

/**
 * The one place that decides whether an incoming push becomes a notification.
 * Pure → unit-tested. ARRIVAL alarms bypass quiet hours by design (api-contracts
 * §5, FR-7.3); everything else honours opt-in, mute, and quiet hours.
 */
object NotificationPolicy {
    fun shouldNotify(payload: PushPayload, prefs: AlertPrefs, nowMin: Int): Boolean {
        if (!prefs.isOptedIn(payload.type)) return false
        if (prefs.isMuted(payload.entityKey)) return false
        if (payload.type == AlertType.ARRIVAL) return true // full-screen alarm bypasses quiet hours
        return !prefs.quietHours.isActiveAt(nowMin)
    }
}
