package app.railcast

import app.railcast.feature.alerts.AlertPrefs
import app.railcast.feature.alerts.AlertType
import app.railcast.feature.alerts.NotificationPolicy
import app.railcast.feature.alerts.PushPayload
import app.railcast.feature.alerts.QuietHours
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {

    private val delay = PushPayload.Delay("12780", 20, "BPL")
    private val arrival = PushPayload.ArrivalAlarm("12780", "NU", "10:30", 15)

    @Test fun `opted-in type outside quiet hours notifies`() {
        assertTrue(NotificationPolicy.shouldNotify(delay, AlertPrefs(), nowMin = 12 * 60))
    }

    @Test fun `opted-out type is suppressed`() {
        val prefs = AlertPrefs(optIn = mapOf(AlertType.DELAY to false))
        assertFalse(NotificationPolicy.shouldNotify(delay, prefs, nowMin = 12 * 60))
    }

    @Test fun `muted journey is suppressed`() {
        val prefs = AlertPrefs(mutedEntities = setOf("train:12780"))
        assertFalse(NotificationPolicy.shouldNotify(delay, prefs, nowMin = 12 * 60))
    }

    @Test fun `quiet hours suppress normal alerts`() {
        val prefs = AlertPrefs(quietHours = QuietHours(enabled = true, startMin = 22 * 60, endMin = 7 * 60))
        assertFalse(NotificationPolicy.shouldNotify(delay, prefs, nowMin = 23 * 60)) // 11pm
        assertFalse(NotificationPolicy.shouldNotify(delay, prefs, nowMin = 6 * 60)) // 6am, wraps midnight
        assertTrue(NotificationPolicy.shouldNotify(delay, prefs, nowMin = 12 * 60)) // noon, outside
    }

    @Test fun `arrival alarm bypasses quiet hours`() {
        val prefs = AlertPrefs(quietHours = QuietHours(enabled = true, startMin = 22 * 60, endMin = 7 * 60))
        assertTrue(NotificationPolicy.shouldNotify(arrival, prefs, nowMin = 3 * 60)) // 3am, still fires
    }

    @Test fun `arrival alarm still respects opt-out`() {
        val prefs = AlertPrefs(optIn = mapOf(AlertType.ARRIVAL to false))
        assertFalse(NotificationPolicy.shouldNotify(arrival, prefs, nowMin = 3 * 60))
    }

    @Test fun `quiet hours within a single day do not wrap`() {
        val prefs = AlertPrefs(quietHours = QuietHours(enabled = true, startMin = 9 * 60, endMin = 17 * 60))
        assertTrue(QuietHours(true, 9 * 60, 17 * 60).isActiveAt(12 * 60)) // noon inside window
        assertFalse(QuietHours(true, 9 * 60, 17 * 60).isActiveAt(20 * 60)) // 8pm outside window
        assertFalse(NotificationPolicy.shouldNotify(delay, prefs, nowMin = 12 * 60)) // noon → suppressed
        assertTrue(NotificationPolicy.shouldNotify(delay, prefs, nowMin = 20 * 60)) // 8pm → notifies
    }

    @Test fun `equal start and end means quiet hours disabled`() {
        assertFalse(QuietHours(enabled = true, startMin = 60, endMin = 60).isActiveAt(60))
    }
}
