package com.prayershield.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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

    private fun yesterdayKey(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun getPrayerTimeMinutes(context: Context, prayer: String): Int {
        return prefs(context).getInt("time_$prayer", DEFAULT_TIMES[prayer] ?: 0)
    }

    fun setPrayerTimeMinutes(context: Context, prayer: String, minutes: Int) {
        prefs(context).edit { putInt("time_$prayer", minutes) }
    }

    fun isPrayed(context: Context, prayer: String): Boolean {
        return isPrayedOnDate(context, prayer, todayKey())
    }

    private fun isPrayedOnDate(context: Context, prayer: String, date: String): Boolean {
        val storedDate = prefs(context).getString("prayed_date_$prayer", "")
        return storedDate == date
    }

    fun markPrayed(context: Context, prayer: String) {
        // If we are currently in a wrap-around window from yesterday, mark it for yesterday.
        val nowMinutes = currentMinutes()
        val start = getPrayerTimeMinutes(context, prayer)
        val targetDate = if (nowMinutes < start && isTimeInRange(nowMinutes, start, start + GRACE_MINUTES)) {
            yesterdayKey()
        } else {
            todayKey()
        }
        
        prefs(context).edit { putString("prayed_date_$prayer", targetDate) }
        recordDayCompletionIfNeeded(context, targetDate)
    }

    fun allPrayedToday(context: Context): Boolean {
        return allPrayedOnDate(context, todayKey())
    }

    private fun allPrayedOnDate(context: Context, date: String): Boolean {
        return PRAYERS.all { isPrayedOnDate(context, it, date) }
    }

    private fun recordDayCompletionIfNeeded(context: Context, date: String) {
        if (!allPrayedOnDate(context, date)) return
        val completed = HashSet(prefs(context).getStringSet("completed_dates", emptySet()) ?: emptySet())
        completed.add(date)
        prefs(context).edit { putStringSet("completed_dates", completed) }
    }

    fun getCurrentStreak(context: Context): Int {
        val completed = prefs(context).getStringSet("completed_dates", emptySet()) ?: emptySet()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val nowMinutes = currentMinutes()

        // Decide which day to start the backward count from.
        if (completed.contains(todayKey())) {
            // Today is done! Start counting from today.
        } else if (nowMinutes < 4 * 60 && !allPrayedOnDate(context, yesterdayKey())) {
            // It's early morning and yesterday (e.g. Monday) isn't finished yet.
            // Don't break the streak! Start counting from the day before yesterday (Sunday).
            cal.add(Calendar.DATE, -2)
        } else {
            // Normal case: today isn't done, start checking from yesterday.
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
            val end = start + GRACE_MINUTES
            if (isTimeInRange(nowMinutes, start, end)) {
                // If nowMinutes < start, we are checking yesterday's instance of this prayer
                val checkDate = if (nowMinutes < start) yesterdayKey() else todayKey()
                if (!isPrayedOnDate(context, prayer, checkDate)) {
                    return prayer
                }
            }
        }
        return null
    }

    fun canMarkPrayed(context: Context, prayer: String): Boolean {
        val nowMinutes = currentMinutes()
        val prayerStart = getPrayerTimeMinutes(context, prayer)

        // Check if we are in the wrap-around window for YESTERDAY'S prayer
        if (nowMinutes < prayerStart && isTimeInRange(nowMinutes, prayerStart, prayerStart + GRACE_MINUTES)) {
            return !isPrayedOnDate(context, prayer, yesterdayKey())
        }

        // Normal check for TODAY
        if (isPrayedOnDate(context, prayer, todayKey())) return false
        
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
        
        // Don't block during Safe Times (e.g. School)
        if (isSafeTimeActive(context)) return false
        
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
    private const val SAFE_TIMES_ENABLED = "safe_times_enabled"
    private const val SAFE_START_MINUTES = "safe_start_minutes"
    private const val SAFE_END_MINUTES = "safe_end_minutes"

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

    fun isSafeTimesEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(SAFE_TIMES_ENABLED, false)
    }

    fun setSafeTimesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(SAFE_TIMES_ENABLED, enabled) }
    }

    fun getSafeStartTime(context: Context): Int {
        return prefs(context).getInt(SAFE_START_MINUTES, 8 * 60) // Default 8:00 AM
    }

    fun setSafeStartTime(context: Context, minutes: Int) {
        prefs(context).edit { putInt(SAFE_START_MINUTES, minutes) }
    }

    fun getSafeEndTime(context: Context): Int {
        return prefs(context).getInt(SAFE_END_MINUTES, 15 * 60) // Default 3:00 PM
    }

    fun setSafeEndTime(context: Context, minutes: Int) {
        prefs(context).edit { putInt(SAFE_END_MINUTES, minutes) }
    }

    fun isSafeTimeActive(context: Context): Boolean {
        if (!isSafeTimesEnabled(context)) return false
        val now = currentMinutes()
        return isTimeInRange(now, getSafeStartTime(context), getSafeEndTime(context))
    }

    /**
     * Logic for the "Smart Reminder": If Duhr window ends (start of Asr)
     * BEFORE safe time ends (school ends), and Duhr isn't prayed,
     * the user might miss Duhr while in school.
     */
    fun shouldNotifyForPrayerBreak(context: Context): Boolean {
        if (!isSafeTimeActive(context)) return false
        if (isPrayed(context, "Dhuhr")) return false

        val asrStart = getPrayerTimeMinutes(context, "Asr")
        val safeEnd = getSafeEndTime(context)
        
        // If Asr starts before school ends, you're going to miss Duhr!
        return asrStart < safeEnd
    }

    fun notifyPrayerTimesChanged(context: Context) {
        val intent = Intent("com.prayershield.app.PRAYER_TIMES_CHANGED")
            .setPackage("com.sleepshield.app")
        context.sendBroadcast(intent)
        
        // Check for prayer break reminder
        if (shouldNotifyForPrayerBreak(context)) {
            sendPrayerBreakNotification(context)
        }
    }

    private fun sendPrayerBreakNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val builder = NotificationCompat.Builder(context, "prayer_reminders")
            .setSmallIcon(R.drawable.ic_tab_prayer)
            .setContentTitle("Dhuhr Prayer Break")
            .setContentText("Dhuhr will end before school does. Ask to pray now!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notificationManager.notify(1001, builder.build())
        }
    }

    fun resetToday(context: Context) {
        prefs(context).edit {
            PRAYERS.forEach { prayer ->
                remove("prayed_date_$prayer")
            }
        }
    }
}
