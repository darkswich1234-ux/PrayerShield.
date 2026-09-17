package com.prayershield.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.android.material.color.DynamicColors

class PrayerShieldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        LocationUpdateWorker.schedule(this)
        // On Android 12+ this repaints the app's Material3 components using
        // colors extracted from the user's wallpaper (Material You).
        // On older Android versions it's a no-op and the fallback color in
        // themes.xml/colors.xml is used instead.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Prayer Reminders"
            val descriptionText = "Notifications for prayer breaks during safe times"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("prayer_reminders", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
