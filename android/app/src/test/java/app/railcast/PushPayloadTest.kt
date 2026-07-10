package app.railcast

import app.railcast.feature.alerts.AlertType
import app.railcast.feature.alerts.NotifChannel
import app.railcast.feature.alerts.OemGuidance
import app.railcast.feature.alerts.OemVendor
import app.railcast.feature.alerts.PushHandler
import app.railcast.feature.alerts.PushPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPayloadTest {

    @Test fun `parses each push kind from an FCM data map`() {
        assertTrue(PushPayload.parse(mapOf("kind" to "CHART_PREPARED", "pnrMasked" to "••••2882", "trainNo" to "12780")) is PushPayload.ChartPrepared)
        assertTrue(PushPayload.parse(mapOf("kind" to "DELAY", "trainNo" to "12780", "delayMin" to "20")) is PushPayload.Delay)
        assertTrue(PushPayload.parse(mapOf("kind" to "PLATFORM_CHANGE", "trainNo" to "12780", "platform" to "5")) is PushPayload.PlatformChange)
        assertTrue(PushPayload.parse(mapOf("kind" to "ARRIVAL_ALARM", "trainNo" to "12780", "stationCode" to "NU")) is PushPayload.ArrivalAlarm)
    }

    @Test fun `cancelled and diverted both map to the CANCEL opt-in`() {
        val cancelled = PushPayload.parse(mapOf("kind" to "CANCELLED", "trainNo" to "12780")) as PushPayload.Disruption
        val diverted = PushPayload.parse(mapOf("kind" to "DIVERTED", "trainNo" to "12780")) as PushPayload.Disruption
        assertTrue(cancelled.cancelled)
        assertFalse(diverted.cancelled)
        assertEquals(AlertType.CANCEL, cancelled.type)
        assertEquals(AlertType.CANCEL, diverted.type)
    }

    @Test fun `missing required fields or unknown kind yield null`() {
        assertNull(PushPayload.parse(mapOf("kind" to "DELAY", "trainNo" to "12780"))) // no delayMin
        assertNull(PushPayload.parse(mapOf("kind" to "WHAT")))
        assertNull(PushPayload.parse(emptyMap()))
    }

    @Test fun `handler routes arrival to the alarm channel full-screen, others to alerts`() {
        val arrival = PushHandler.toSpec(PushPayload.ArrivalAlarm("12780", "NU", "10:30", 15))
        assertEquals(NotifChannel.ALARMS, arrival.channel)
        assertTrue(arrival.fullScreen)

        val delay = PushHandler.toSpec(PushPayload.Delay("12780", 20, "BPL"))
        assertEquals(NotifChannel.ALERTS, delay.channel)
        assertFalse(delay.fullScreen)
    }

    @Test fun `cancelled and diverted specs differ`() {
        val c = PushHandler.toSpec(PushPayload.Disruption(true, "12780", "2026-07-10"))
        val d = PushHandler.toSpec(PushPayload.Disruption(false, "12780", "2026-07-10"))
        assertFalse(c.titleRes == d.titleRes)
    }

    @Test fun `oem vendor detection groups families`() {
        assertEquals(OemVendor.XIAOMI, OemGuidance.vendorOf("Redmi"))
        assertEquals(OemVendor.XIAOMI, OemGuidance.vendorOf("POCO"))
        assertEquals(OemVendor.OPPO_REALME, OemGuidance.vendorOf("realme"))
        assertEquals(OemVendor.OPPO_REALME, OemGuidance.vendorOf("OnePlus"))
        assertEquals(OemVendor.VIVO, OemGuidance.vendorOf("iQOO"))
        assertEquals(OemVendor.OTHER, OemGuidance.vendorOf("Google"))
        assertFalse(OemGuidance.needsGuidance(OemVendor.OTHER))
        assertTrue(OemGuidance.needsGuidance(OemVendor.XIAOMI))
    }
}
