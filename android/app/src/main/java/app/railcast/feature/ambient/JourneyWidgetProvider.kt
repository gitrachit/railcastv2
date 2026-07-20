package app.railcast.feature.ambient

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.railcast.MainActivity
import app.railcast.R

/**
 * The home-screen journey widget — the ambient surface the selected direction
 * is built on (docs/design/direction-study.md).
 *
 * The bet: for the common case the user never opens the app, because the answer
 * is already on their home screen. That makes time-to-answer ~0 rather than
 * merely fast, and it is a strategy an ad-funded competitor cannot copy —
 * "sessions avoided" is revenue destroyed for them.
 *
 * All the content decisions live in [Ambient], which is pure and unit-tested.
 * This class only binds them to RemoteViews.
 *
 * **Device-gated, and deliberately conservative.** Widget redraw cadence is
 * throttled by the OS and varies by OEM — aggressive battery managers on
 * Xiaomi/Oppo/Vivo are a known live risk (PRD §12 Q3). This has not been
 * verified on real hardware. Two consequences are baked in rather than assumed
 * away:
 *  - `updatePeriodMillis` is 30 min, the practical floor the framework honours;
 *    anything tighter must come from the Watcher's pushes, not from polling.
 *  - the freshness stamp is mandatory, so a stale widget says so instead of
 *    quietly presenting old data as current (FR-2.5, FR-9.1).
 */
class JourneyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // A throw inside onReceive kills the whole process, so a widget that
        // cannot render must degrade to the invitation rather than take the app
        // down with it. The known hazard is direct boot (AmbientRepository
        // guards it); this is the backstop for the ones we have not met.
        val state = runCatching { AmbientRepository.currentState(context) }
            .getOrDefault(AmbientState.Invitation)
        for (id in appWidgetIds) {
            runCatching { appWidgetManager.updateAppWidget(id, buildViews(context, state)) }
        }
    }

    private fun buildViews(context: Context, state: AmbientState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_journey)

        // Sunlight has no resource qualifier — it is a user choice, not a device
        // configuration — so the widget applies it by hand or the user who asked
        // for the high-contrast theme silently does not get it (FR-5.3).
        val sunlight = AmbientPalette.isSunlight(context)
        views.setInt(R.id.widget_root, "setBackgroundResource", AmbientPalette.boardBackground(sunlight))
        for (id in listOf(R.id.widget_title, R.id.widget_platform, R.id.widget_freshness)) {
            views.setTextColor(id, context.getColor(AmbientPalette.secondaryInk(sunlight)))
        }
        views.setTextColor(R.id.widget_consequence, context.getColor(AmbientPalette.primaryInk(sunlight)))

        when (state) {
            // Never blank. An empty widget is wasted home-screen real estate and
            // tells the user nothing; it always offers exactly one action.
            AmbientState.Invitation -> {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_empty))
                views.setTextViewText(R.id.widget_consequence, "")
                views.setTextViewText(R.id.widget_platform, "")
                views.setTextViewText(R.id.widget_freshness, "")
                views.setTextColor(R.id.widget_status, context.getColor(R.color.rc_board_ink))
            }

            is AmbientState.Live -> {
                val j = state.journey
                val title = buildString {
                    append(j.trainName).append(" · ").append(j.trainNo)
                    if (state.otherCount > 0) {
                        append("   ").append(context.getString(R.string.widget_others, state.otherCount))
                    }
                }
                views.setTextViewText(R.id.widget_title, title)

                // Status is icon + word; colour is the third signal, never the
                // first (FR-10.2). The board sub-palette is used because the
                // widget background is the board surface.
                views.setTextViewText(R.id.widget_status, j.statusWord)
                views.setTextColor(R.id.widget_status, context.getColor(AmbientPalette.statusColor(j, context)))

                views.setTextViewText(R.id.widget_consequence, Ambient.consequenceLine(j).orEmpty())
                views.setTextViewText(
                    R.id.widget_platform,
                    context.getString(R.string.track_platform, Ambient.platformLabel(j)),
                )
                views.setTextViewText(
                    R.id.widget_freshness,
                    Ambient.freshnessLabel(AmbientRepository.ageSeconds(context)),
                )
            }
        }

        // Tapping the widget expands into the app — the widget is the front
        // door, not a shortcut to one.
        val intent = Intent(context, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(
            R.id.widget_title,
            PendingIntent.getActivity(context, 0, intent, flags),
        )
        return views
    }

    companion object {
        /** Ask the framework to redraw every instance; call after a push lands. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, JourneyWidgetProvider::class.java),
            )
            if (ids.isNotEmpty()) {
                Intent(context, JourneyWidgetProvider::class.java).also {
                    it.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    it.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    context.sendBroadcast(it)
                }
            }
        }
    }
}
