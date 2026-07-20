package app.railcast.feature.ambient

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.railcast.MainActivity
import app.railcast.R

/**
 * The lockscreen surface of the ambient layer (wireframe W1).
 *
 * The widget answers on the home screen; this answers on the lockscreen, which
 * is where a traveller actually looks — phone in hand, walking down a platform,
 * screen off until they glance. Both render from the same [Ambient] model so
 * the two surfaces cannot drift apart.
 *
 * Deliberate posture, because a permanent notification is easy to get wrong and
 * the product's law is "minimal, meaningful notifications only" (FR-7.4):
 *
 *  - **Silent.** `IMPORTANCE_LOW` — it never buzzes, never peeks, never makes a
 *    sound. It appears; it does not interrupt. Quiet hours are therefore not
 *    consulted: there is nothing to be quiet about.
 *  - **Dismissible.** `setOngoing(false)`. An undismissable notification is a
 *    dark pattern, and §7 lists those as product law. Swiping it away is a
 *    legitimate way to say "not now".
 *  - **Time-bounded.** Posted only while a journey is live, cancelled the
 *    moment it is not. It cannot become permanent furniture.
 *  - **One tap to stop.** A Mute action, because the user must be able to end
 *    the relationship from the surface that is bothering them.
 */
object LiveJourneyNotification {

    const val CHANNEL_LIVE = "live_journey"
    private const val NOTIFICATION_ID = 42_001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_LIVE,
            context.getString(R.string.live_channel_name),
            // LOW: shows in the shade and on the lockscreen, but never makes a
            // sound and never heads-up. This is the whole posture in one flag.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.live_channel_desc)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Renders [state], or clears the notification when there is nothing live to
     * say. Clearing on [AmbientState.Invitation] is what keeps this from
     * outliving the journey it describes.
     */
    fun render(context: Context, state: AmbientState, ageSeconds: Long) {
        val manager = NotificationManagerCompat.from(context)
        if (state !is AmbientState.Live) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        // areNotificationsEnabled() reflects the channel/app toggle, but from
        // API 33 POST_NOTIFICATIONS is a separate runtime grant. Both must
        // hold, and neither is asked for here: this surface appears only if the
        // user already allowed notifications elsewhere (FR-10.5 — no permission
        // walls for something they did not request).
        if (!manager.areNotificationsEnabled() || !hasPostPermission(context)) return

        ensureChannel(context)
        val j = state.journey

        // Title carries identity; the answer goes in the big text, because on a
        // lockscreen the title is what gets truncated first.
        val consequence = Ambient.consequenceLine(j)
        val platform = context.getString(R.string.track_platform, Ambient.platformLabel(j))
        val body = listOfNotNull(consequence, platform).joinToString("  ·  ")

        val builder = NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(android.R.drawable.stat_notify_more) // brand icon lands in M6
            .setContentTitle("${j.trainName} · ${j.trainNo}")
            .setContentText("${j.statusWord}  ·  $body")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(j.statusWord)
                        if (body.isNotBlank()) append("\n").append(body)
                        // Freshness is mandatory on ambient surfaces: this one
                        // updates only when the app has new data, so it must
                        // say how old that data is (FR-2.5).
                        append("\n").append(Ambient.freshnessLabel(ageSeconds))
                    },
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            // Not ongoing: the user may always swipe it away (§7, no dark patterns).
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // Full content on the lockscreen — this surface exists to be read
            // there. It carries no PNR and no personal data beyond a train and
            // a station, so there is nothing to redact.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(context))
            .addAction(
                0,
                context.getString(R.string.live_action_mute),
                muteIntent(context, j.trainNo),
            )

        // The permission is checked above, but it can be revoked between that
        // check and this call. This runs off a data refresh, not a user action,
        // so losing the notification is the right outcome — crashing is not.
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Notifications revoked mid-flight; nothing to do and nothing to say.
        }
    }

    /** Called when the journey ends or the user turns the surface off. */
    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun hasPostPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun muteIntent(context: Context, trainNo: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            trainNo.hashCode(),
            Intent(context, LiveJourneyMuteReceiver::class.java).putExtra(
                LiveJourneyMuteReceiver.EXTRA_TRAIN_NO,
                trainNo,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
