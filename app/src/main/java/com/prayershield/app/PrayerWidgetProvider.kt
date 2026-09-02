package com.prayershield.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.prayershield.app.R

class PrayerWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_MARK_PRAYED = "com.prayershield.app.ACTION_MARK_PRAYED_WIDGET"
        const val EXTRA_PRAYER = "extra_prayer"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, PrayerWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, PrayerWidgetProvider::class.java)
                intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MARK_PRAYED) {
            val prayer = intent.getStringExtra(EXTRA_PRAYER)
            if (prayer != null && PrayerManager.canMarkPrayed(context, prayer)) {
                PrayerManager.markPrayed(context, prayer)
                refreshAll(context)
                PrayerGridWidgetProvider.refreshAll(context)
            }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.prayer_widget)

        val activePrayer = PrayerManager.activeUnprayedWindow(context)
        val streak = PrayerManager.getCurrentStreak(context)
        views.setTextViewText(R.id.widgetStreak, context.getString(R.string.streak_format, streak.toString()))

        val dotIds = mapOf(
            "Fajr" to R.id.dotFajr,
            "Dhuhr" to R.id.dotDhuhr,
            "Asr" to R.id.dotAsr,
            "Maghrib" to R.id.dotMaghrib,
            "Isha" to R.id.dotIsha
        )
        for (prayer in PrayerManager.PRAYERS) {
            val dotId = dotIds[prayer] ?: continue
            val symbol = when {
                PrayerManager.isPrayed(context, prayer) -> "✓"
                prayer == activePrayer -> "●"
                else -> "○"
            }
            views.setTextViewText(dotId, symbol)
        }

        val currentMarkable = PrayerManager.PRAYERS.find { PrayerManager.canMarkPrayed(context, it) }

        if (currentMarkable != null) {
            views.setTextViewText(R.id.widgetTitle, context.getString(R.string.widget_ready_format, currentMarkable))
            views.setTextViewText(R.id.widgetSubtitle, context.getString(R.string.widget_mark_prompt))
            views.setViewVisibility(R.id.widgetMarkButton, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widgetMarkButton, context.getString(R.string.widget_mark_button_format, currentMarkable))

            val markIntent = Intent(context, PrayerWidgetProvider::class.java)
            markIntent.action = ACTION_MARK_PRAYED
            markIntent.putExtra(EXTRA_PRAYER, currentMarkable)
            val markPending = PendingIntent.getBroadcast(
                context, currentMarkable.hashCode(), markIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetMarkButton, markPending)
        } else {
            val allPrayedToday = PrayerManager.PRAYERS.all { PrayerManager.isPrayed(context, it) }
            val nextPrayer = nextUpcomingPrayer(context)
            if (allPrayedToday) {
                views.setTextViewText(R.id.widgetTitle, context.getString(R.string.all_caught_up))
                views.setTextViewText(
                    R.id.widgetSubtitle,
                    if (nextPrayer != null) context.getString(R.string.widget_next_format, nextPrayer) else context.getString(R.string.all_caught_up_sub)
                )
            } else {
                val markable = PrayerManager.PRAYERS.find { PrayerManager.canMarkPrayed(context, it) }
                views.setTextViewText(R.id.widgetTitle, if (markable != null) context.getString(R.string.widget_ready_format, markable) else context.getString(R.string.not_yet_status))
                views.setTextViewText(
                    R.id.widgetSubtitle,
                    if (markable != null) context.getString(R.string.widget_mark_prompt) else if (nextPrayer != null) context.getString(R.string.widget_next_format, nextPrayer) else context.getString(R.string.widget_no_prayer_set)
                )
            }
            views.setViewVisibility(R.id.widgetMarkButton, android.view.View.GONE)
        }

        // Tapping the widget body opens the app
        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openPending)

        manager.updateAppWidget(widgetId, views)
    }

    private fun nextUpcomingPrayer(context: Context): String? {
        val cal = java.util.Calendar.getInstance()
        val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        var best: String? = null
        var bestDiff = Int.MAX_VALUE
        for (prayer in PrayerManager.PRAYERS) {
            val t = PrayerManager.getPrayerTimeMinutes(context, prayer)
            val diff = if (t >= nowMinutes) t - nowMinutes else (t + 24 * 60) - nowMinutes
            if (diff < bestDiff) {
                bestDiff = diff
                best = prayer
            }
        }
        return best
    }
}