package app.railcast.feature.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.railcast.R

/**
 * Posts a [NotificationSpec] as a system notification (FR-7.2/7.3). This is the
 * binding point the FCM service will call once google-services.json is added —
 * it stays Firebase-free and buildable today. Alarms use a high-importance
 * channel + full-screen intent into [AlarmActivity]; alerts are default.
 */
class NotificationPoster(private val context: Context) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.alert_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, context.getString(R.string.alert_channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.alert_channel_alarms_desc)
            },
        )
    }

    fun post(spec: NotificationSpec, notificationId: Int) {
        ensureChannels()
        val channelId = if (spec.channel == NotifChannel.ALARMS) CHANNEL_ALARMS else CHANNEL_ALERTS
        val title = context.getString(spec.titleRes, *spec.args.toTypedArray())
        val body = context.getString(spec.bodyRes, *spec.args.toTypedArray())

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more) // placeholder until brand icon (M6)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(if (spec.fullScreen) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)

        if (spec.fullScreen) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(alarmIntent(spec, notificationId), true)
        }

        // POST_NOTIFICATIONS (API 33+) is requested from the Alerts screen; guard here.
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    private fun alarmIntent(spec: NotificationSpec, notificationId: Int): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(AlarmActivity.EXTRA_TITLE_RES, spec.titleRes)
            putExtra(AlarmActivity.EXTRA_BODY_RES, spec.bodyRes)
            putStringArrayListExtra(AlarmActivity.EXTRA_ARGS, ArrayList(spec.args))
        }
        return PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_ALARMS = "alarms"
    }
}
