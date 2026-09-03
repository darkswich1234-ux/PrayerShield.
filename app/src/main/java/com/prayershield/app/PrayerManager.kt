package com.prayershield.app

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.*

/**
 * Central place for all app state: prayer times, which apps are blocked,
 * and whether each prayer has been marked "done" today.
 */
object PrayerManager {

    private const val PREFS = "prayer_shield_prefs"
    val PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    // Default times in "minutes since midnight"
    private val DEFAULT_TIMES = mapOf(
        "Fajr" to (5 * 60),
        "Dhuhr" to (13 * 60),
        "Asr" to (16 * 60 + 30),
        "Maghrib" to (19 * 60),
        "Isha" to (20 * 60 + 30),
    )

    // How long after a prayer's start time the block window stays active if not marked prayed
    const val GRACE_MINUTES = 6 * 60 

    // Packages that should never be blocked
    val ALWAYS_ALLOWED = setOf(
        "com.prayershield.app",
        "com.sleepshield.app",
        "com.android.systemui",
        "com.android.settings",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.phone",
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun getPrayerTimeMinutes(context: Context, prayer: String): Int {
        return prefs(context).getInt("time_$prayer", DEFAULT_TIMES[prayer] ?: 0)
    }

    fun setPrayerTimeMinutes(context: Context, prayer: String, minutes: Int) {
        prefs(context).edit { putInt("time_$prayer", minutes) }
    }

    fun isPrayed(context: Context, prayer: String): Boolean {
        val storedDate = prefs(context).getString("prayed_date_$prayer", "")
        return storedDate == todayKey()
    }

    fun markPrayed(context: Context, prayer: String) {
        prefs(context).edit { putString("prayed_date_$prayer", todayKey()) }
        recordDayCompletionIfNeeded(context)
    }

    fun allPrayedToday(context: Context): Boolean {
        return PRAYERS.all { isPrayed(context, it) }
    }

    private fun recordDayCompletionIfNeeded(context: Context) {
        if (!allPrayedToday(context)) return
        val completed = HashSet(prefs(context).getStringSet("completed_dates", emptySet()) ?: emptySet())
        completed.add(todayKey())
        prefs(context).edit { putStringSet("completed_dates", completed) }
    }

    fun getCurrentStreak(context: Context): Int {
        val completed = prefs(context).getStringSet("completed_dates", emptySet()) ?: emptySet()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        if (!completed.contains(todayKey())) {
            cal.add(Calendar.DATE, -1)
        }
        var streak = 0
        while (completed.contains(fmt.format(cal.time))) {
            streak++
            cal.add(Calendar.DATE, -1)
        }
        return streak
    }

    fun isProtectSettingsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean("protect_settings", false)
    }

    fun setProtectSettingsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean("protect_settings", enabled) }
    }

    fun getBlockedApps(context: Context): MutableSet<String> {
        return HashSet(prefs(context).getStringSet("blocked_apps", emptySet()) ?: emptySet())
    }

    fun setBlockedApps(context: Context, packages: Set<String>) {
        prefs(context).edit { putStringSet("blocked_apps", packages) }
    }

    fun activeUnprayedWindow(context: Context): String? {
        val nowMinutes = currentMinutes()
        for (prayer in PRAYERS) {
            val start = getPrayerTimeMinutes(context, prayer)
            if (isTimeInRange(nowMinutes, start, start + GRACE_MINUTES) && !isPrayed(context, prayer)) {
                return prayer
            }
        }
        return null
    }

    fun canMarkPrayed(context: Context, prayer: String): Boolean {
        if (isPrayed(context, prayer)) return false
        val nowMinutes = currentMinutes()
        val prayerStart = getPrayerTimeMinutes(context, prayer)
        return if (prayer == "Fajr") {
            val dhuhrStart = getPrayerTimeMinutes(context, "Dhuhr")
            val dhuhrEnd = dhuhrStart + GRACE_MINUTES
            isTimeInRange(nowMinutes, prayerStart, dhuhrEnd)
        } else {
            isTimeInRange(nowMinutes, prayerStart, prayerStart + GRACE_MINUTES)
        }
    }

    private fun currentMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun isTimeInRange(now: Int, start: Int, end: Int): Boolean {
        val dayMinutes = 24 * 60
        return if (end <= dayMinutes) {
            now in start until end
        } else {
            now >= start || now < (end - dayMinutes)
        }
    }

    fun isAppBlockedNow(context: Context, packageName: String): Boolean {
        if (packageName in ALWAYS_ALLOWED) return false
        if (packageName !in getBlockedApps(context)) return false
        return activeUnprayedWindow(context) != null
    }

    fun hasSeenTipDialog(context: Context): Boolean {
        return prefs(context).getBoolean("seen_tip_dialog", false)
    }

    fun setSeenTipDialog(context: Context) {
        prefs(context).edit { putBoolean("seen_tip_dialog", true) }
    }

    private const val SLEEP_SHIELD_SYNC_ENABLED = "sleep_shield_sync_enabled"
    private const val AUTO_LOCATION_ENABLED = "auto_location_enabled"
    private const val UI_STYLE = "ui_style"
    private const val AMOLED_BLACK = "amoled_black"

    fun isSleepShieldSyncEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(SLEEP_SHIELD_SYNC_ENABLED, false)
    }

    fun setSleepShieldSyncEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(SLEEP_SHIELD_SYNC_ENABLED, enabled) }
        if (enabled) {
            notifyPrayerTimesChanged(context)
        }
    }

    fun isAutoLocationEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(AUTO_LOCATION_ENABLED, false)
    }

    fun setAutoLocationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(AUTO_LOCATION_ENABLED, enabled) }
        if (enabled) {
            LocationUpdateWorker.schedule(context)
        }
    }

    fun getUiStyle(context: Context): String {
        return prefs(context).getString(UI_STYLE, "Classic") ?: "Classic"
    }

    fun setUiStyle(context: Context, style: String) {
        prefs(context).edit { putString(UI_STYLE, style) }
    }

    fun isAmoledBlackEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(AMOLED_BLACK, false)
    }

    fun setAmoledBlackEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(AMOLED_BLACK, enabled) }
    }

    fun notifyPrayerTimesChanged(context: Context) {
        val intent = Intent("com.prayershield.app.PRAYER_TIMES_CHANGED")
            .setPackage("com.sleepshield.app")
        context.sendBroadcast(intent)
    }

    fun resetToday(context: Context) {
        prefs(context).edit {
            PRAYERS.forEach { prayer ->
                remove("prayed_date_$prayer")
            }
        }
    }
}
