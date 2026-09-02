package com.prayershield.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class PrayerShieldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocationUpdateWorker.schedule(this)
        // On Android 12+ this repaints the app's Material3 components using
        // colors extracted from the user's wallpaper (Material You).
        // On older Android versions it's a no-op and the fallback color in
        // themes.xml/colors.xml is used instead.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
