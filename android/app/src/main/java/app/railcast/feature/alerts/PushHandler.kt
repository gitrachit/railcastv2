package app.railcast.feature.alerts

import androidx.annotation.StringRes
import app.railcast.R

/** Which notification channel a push lands on. Alarms are high-importance +
 *  full-screen; alerts are default. */
enum class NotifChannel { ALERTS, ALARMS }

/** Everything the Android layer needs to post one notification. Title/body are
 *  string resources (localized at post time) with positional args. */
data class NotificationSpec(
    val channel: NotifChannel,
    val fullScreen: Boolean,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val args: List<String>,
    val entityKey: String,
)

/**
 * Maps a parsed push to its notification spec (FR-7.2/7.3). Pure → the routing
 * (which channel, full-screen or not) is unit-tested without Android. ARRIVAL
 * alarms are the only full-screen, ALARMS-channel type.
 */
object PushHandler {
    fun toSpec(payload: PushPayload): NotificationSpec = when (payload) {
        is PushPayload.ChartPrepared -> NotificationSpec(
            NotifChannel.ALERTS, false,
            R.string.alert_chart_title, R.string.alert_chart_body,
            listOf(payload.trainName, payload.pnrMasked), payload.entityKey,
        )
        is PushPayload.Delay -> NotificationSpec(
            NotifChannel.ALERTS, false,
            R.string.alert_delay_title, R.string.alert_delay_body,
            listOf(payload.trainNo, payload.delayMin.toString(), payload.nextStation), payload.entityKey,
        )
        is PushPayload.PlatformChange -> NotificationSpec(
            NotifChannel.ALERTS, false,
            R.string.alert_platform_title, R.string.alert_platform_body,
            listOf(payload.trainNo, payload.platform, payload.stationCode), payload.entityKey,
        )
        is PushPayload.Disruption -> NotificationSpec(
            NotifChannel.ALERTS, false,
            if (payload.cancelled) R.string.alert_cancelled_title else R.string.alert_diverted_title,
            if (payload.cancelled) R.string.alert_cancelled_body else R.string.alert_diverted_body,
            listOf(payload.trainNo), payload.entityKey,
        )
        is PushPayload.ArrivalAlarm -> NotificationSpec(
            NotifChannel.ALARMS, true,
            R.string.alert_arrival_title, R.string.alert_arrival_body,
            listOf(payload.trainNo, payload.stationCode, payload.leadMin.toString()), payload.entityKey,
        )
        is PushPayload.TatkalOpen -> NotificationSpec(
            NotifChannel.ALERTS, false,
            R.string.alert_tatkal_title, R.string.alert_tatkal_body,
            listOf(payload.trainNo, payload.runDate), payload.entityKey,
        )
    }
}
