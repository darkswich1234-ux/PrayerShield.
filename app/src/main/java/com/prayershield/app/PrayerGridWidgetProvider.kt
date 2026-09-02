package com.prayershield.app

import com.prayershield.app.R
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class PrayerGridWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_MARK_PRAYED = "com.prayershield.app.ACTION_MARK_PRAYED_GRID_WIDGET"
        const val EXTRA_PRAYER = "extra_prayer"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, PrayerGridWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, PrayerGridWidgetProvider::class.java)
                intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (PrayerManager.isAutoLocationEnabled(context)) {
            LocationUpdateWorker.schedule(context)
        }
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
                PrayerWidgetProvider.refreshAll(context)
            }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.prayer_widget_grid)

        val dotIds = mapOf(
            "Fajr" to R.id.dotFajr,
            "Dhuhr" to R.id.dotDhuhr,
            "Asr" to R.id.dotAsr,
            "Maghrib" to R.id.dotMaghrib,
            "Isha" to R.id.dotIsha
        )

        val activePrayer = PrayerManager.activeUnprayedWindow(context)

        for (prayer in PrayerManager.PRAYERS) {
            val dotId = dotIds[prayer] ?: continue
            val symbol = when {
                PrayerManager.isPrayed(context, prayer) -> "✓"
                prayer == activePrayer -> "●"
                else -> "○"
            }
            views.setTextViewText(dotId, symbol)
        }

        val streak = PrayerManager.getCurrentStreak(context)
        views.setTextViewText(R.id.gridStreak, context.getString(R.string.streak_format, streak.toString()))

        val currentMarkable = PrayerManager.PRAYERS.find { PrayerManager.canMarkPrayed(context, it) }

        if (currentMarkable != null) {
            views.setTextViewText(R.id.gridWidgetStatus, context.getString(R.string.widget_ready_format, currentMarkable))
            views.setViewVisibility(R.id.gridMarkButton, View.VISIBLE)
            views.setTextViewText(R.id.gridMarkButton, context.getString(R.string.widget_mark_button_format, currentMarkable))

            val markIntent = Intent(context, PrayerGridWidgetProvider::class.java).apply {
                action = ACTION_MARK_PRAYED
                putExtra(EXTRA_PRAYER, currentMarkable)
            }
            val markPending = PendingIntent.getBroadcast(
                context, currentMarkable.hashCode(), markIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.gridMarkButton, markPending)
        } else {
            views.setTextViewText(R.id.gridWidgetStatus, context.getString(R.string.app_name))
            views.setViewVisibility(R.id.gridMarkButton, View.GONE)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.gridWidgetRoot, openPending)

        manager.updateAppWidget(widgetId, views)
    }
}