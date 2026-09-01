package com.prayershield.app

import java.text.SimpleDateFormat
import java.util.*

/**
 * Central place for all app state: prayer times, which apps are blocked,
 * and whether each prayer has been marked "done" today.
 */
object PrayerManager {

    private var settings: SettingsProvider? = null

    fun init(provider: SettingsProvider) {
        settings = provider
    }

    private fun getSettings(): SettingsProvider {
        return settings ?: throw IllegalStateException("PrayerManager not initialized with a SettingsProvider")
    }

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
    const val GRACE_MINUTES = 6 * 60 // 6 hour window, plenty of margin before next prayer

    // Packages that should never be blocked (this app itself, launcher, system UI, dialer, settings)
    val ALWAYS_ALLOWED = setOf(
        "com.prayershield.app",
        "com.android.systemui",
        "com.android.settings",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.phone",
    )

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun getPrayerTimeMinutes(prayer: String): Int {
        return getSettings().getInt("time_$prayer", DEFAULT_TIMES[prayer] ?: 0)
    }

    fun setPrayerTimeMinutes(prayer: String, minutes: Int) {
        getSettings().putInt("time_$prayer", minutes)
    }

    fun isPrayed(prayer: String): Boolean {
        val storedDate = getSettings().getString("prayed_date_$prayer", "")
        return storedDate == todayKey()
    }

    fun markPrayed(prayer: String) {
        getSettings().putString("prayed_date_$prayer", todayKey())
        recordDayCompletionIfNeeded()
    }

    fun allPrayedToday(): Boolean {
        return PRAYERS.all { isPrayed(it) }
    }

    /** Call after marking a prayer; if every prayer is now done for today, record it for the streak. */
    private fun recordDayCompletionIfNeeded() {
        if (!allPrayedToday()) return
        val completed = HashSet(getSettings().getStringSet("completed_dates", emptySet()))
        completed.add(todayKey())
        getSettings().putStringSet("completed_dates", completed)
    }

    fun getCompletedDates(): Set<String> {
        return getSettings().getStringSet("completed_dates", emptySet())
    }

    fun setCompletedDates(dates: Set<String>) {
        getSettings().putStringSet("completed_dates", dates)
    }

    /**
     * Current streak in days. If today is already fully complete, today counts;
     * otherwise counts backward starting from yesterday (today isn't "missed" until it ends).
     */
    fun getCurrentStreak(): Int {
        val completed = getSettings().getStringSet("completed_dates", emptySet())
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

    fun isProtectSettingsEnabled(): Boolean {
        return getSettings().getBoolean("protect_settings", false)
    }

    fun setProtectSettingsEnabled(enabled: Boolean) {
        getSettings().putBoolean("protect_settings", enabled)
    }

    fun getBlockedApps(): MutableSet<String> {
        return HashSet(getSettings().getStringSet("blocked_apps", emptySet()))
    }

    fun setBlockedApps(packages: Set<String>) {
        getSettings().putStringSet("blocked_apps", packages)
    }

    /**
     * Returns the name of the prayer whose "window" is currently active and unprayed,
     * or null if nothing should be blocked right now.
     */
    fun activeUnprayedWindow(): String? {
        val nowMinutes = currentMinutes()

        for (prayer in PRAYERS) {
            val start = getPrayerTimeMinutes(prayer)
            if (isTimeInRange(nowMinutes, start, start + GRACE_MINUTES) && !isPrayed(prayer)) {
                return prayer
            }
        }
        return null
    }

    /**
     * Returns true if the given prayer is allowed to be marked as prayed right now.
     */
    fun canMarkPrayed(prayer: String): Boolean {
        if (isPrayed(prayer)) return false

        val nowMinutes = currentMinutes()
        val prayerStart = getPrayerTimeMinutes(prayer)

        return if (prayer == "Fajr") {
            val dhuhrStart = getPrayerTimeMinutes("Dhuhr")
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
            // Wraps midnight
            now >= start || now < (end - dayMinutes)
        }
    }

    fun isAppBlockedNow(packageName: String): Boolean {
        if (packageName in ALWAYS_ALLOWED) return false
        if (packageName !in getBlockedApps()) return false
        return activeUnprayedWindow() != null
    }

    fun hasSeenTipDialog(): Boolean {
        return getSettings().getBoolean("seen_tip_dialog", false)
    }

    fun setSeenTipDialog() {
        getSettings().putBoolean("seen_tip_dialog", true)
    }
}
