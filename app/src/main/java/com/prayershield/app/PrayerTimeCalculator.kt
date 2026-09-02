package com.prayershield.app

import java.util.*
import kotlin.math.*

/**
 * Computes prayer times from latitude/longitude/date.
 */
object PrayerTimeCalculator {

    private const val FAJR_ANGLE = 15.0
    private const val ISHA_ANGLE = 15.0
    private const val ASR_SHADOW_FACTOR = 1.0 // 1.0 = standard/Shafi, 2.0 = Hanafi

    /** Returns minutes-since-midnight (local time) for each prayer, for "today". */
    fun calculateTodayMinutes(latitude: Double, longitude: Double): Map<String, Int> {
        val cal = Calendar.getInstance()
        val jd = julianDate(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH])

        val tzOffsetHours = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3600000.0

        val (declination, eqTimeMinutes) = sunPosition(jd - (longitude / 360.0))

        val dhuhrHours = 12.0 + tzOffsetHours - longitude / 15.0 - eqTimeMinutes / 60.0

        fun hourAngle(angleDeg: Double): Double {
            val lat = Math.toRadians(latitude)
            val decl = Math.toRadians(declination)
            val angle = Math.toRadians(angleDeg)
            val cosH = (-sin(angle) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
            val clamped = cosH.coerceIn(-1.0, 1.0)
            return Math.toDegrees(acos(clamped)) / 15.0
        }

        fun asrHourAngle(shadowFactor: Double): Double {
            val lat = Math.toRadians(latitude)
            val decl = Math.toRadians(declination)
            val angle = -atan(1.0 / (shadowFactor + tan(abs(lat - decl))))
            val cosH = (-sin(angle) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
            val clamped = cosH.coerceIn(-1.0, 1.0)
            return Math.toDegrees(acos(clamped)) / 15.0
        }

        val fajrHours = dhuhrHours - hourAngle(FAJR_ANGLE)
        val sunsetHours = dhuhrHours + hourAngle(0.833) // ~sunset, accounts for refraction
        val ishaHours = dhuhrHours + hourAngle(ISHA_ANGLE)
        val asrHours = dhuhrHours + asrHourAngle(ASR_SHADOW_FACTOR)

        return mapOf(
            "Fajr" to hoursToMinutes(fajrHours),
            "Dhuhr" to hoursToMinutes(dhuhrHours),
            "Asr" to hoursToMinutes(asrHours),
            "Maghrib" to hoursToMinutes(sunsetHours),
            "Isha" to hoursToMinutes(ishaHours),
        )
    }

    private fun hoursToMinutes(hours: Double): Int {
        var h = hours
        while (h < 0) h += 24.0
        while (h >= 24) h -= 24.0
        return (h * 60).roundToInt().coerceIn(0, 24 * 60 - 1)
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Returns (declination in degrees, equation of time in minutes) */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = Math.toRadians(fixAngle(357.529 + 0.98560028 * d))
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = Math.toRadians(fixAngle(q + 1.915 * sin(g) + 0.020 * sin(2 * g)))
        val e = Math.toRadians(23.439 - 0.00000036 * d)

        val ra = Math.toDegrees(atan2(cos(e) * sin(l), cos(l))) / 15.0
        val decl = Math.toDegrees(asin(sin(e) * sin(l)))
        val eqTimeMinutes = (q / 15.0 - fixHour(ra)) * 60.0

        return Pair(decl, eqTimeMinutes)
    }

    private fun fixAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a < 0) a += 360.0
        return a
    }

    private fun fixHour(hour: Double): Double {
        var h = hour % 24.0
        if (h < 0) h += 24.0
        return h
    }
}
