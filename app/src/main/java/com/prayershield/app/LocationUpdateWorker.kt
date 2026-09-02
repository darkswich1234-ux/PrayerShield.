package com.prayershield.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class LocationUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        if (!PrayerManager.isAutoLocationEnabled(applicationContext)) {
            return Result.success()
        }

        val hasFine = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return Result.failure()
        }

        val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = locationManager.getProviders(true)

        var bestLocation: Location? = null
        for (provider in providers) {
            val loc = try { locationManager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                bestLocation = loc
            }
        }

        bestLocation?.let {
            val times = PrayerTimeCalculator.calculateTodayMinutes(it.latitude, it.longitude)
            for ((prayer, minutes) in times) {
                PrayerManager.setPrayerTimeMinutes(applicationContext, prayer, minutes)
            }
            PrayerManager.notifyPrayerTimesChanged(applicationContext)
            return Result.success()
        }

        return Result.retry()
    }

    companion object {
        private const val WORK_NAME = "location_update_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<LocationUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
