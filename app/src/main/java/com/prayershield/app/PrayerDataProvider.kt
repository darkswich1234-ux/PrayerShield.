package com.prayershield.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrayerDataProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.prayershield.app.prayerdata"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/today")
        private val COLUMNS = arrayOf(
            "date",
            "fajr_minutes",
            "fajr_time",
            "sync_enabled",
            "updated_at"
        )
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        if (uri.path != "/today") {
            throw IllegalArgumentException("Unknown URI: $uri")
        }

        val syncEnabled = PrayerManager.isSleepShieldSyncEnabled(context)
        val cursor = MatrixCursor(COLUMNS)

        if (syncEnabled) {
            val fajrMinutes = PrayerManager.getPrayerTimeMinutes(context, "Fajr")
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            cursor.addRow(
                arrayOf(
                    date,
                    fajrMinutes,
                    formatTime(fajrMinutes),
                    1, // true
                    System.currentTimeMillis()
                )
            )
        } else {
            cursor.addRow(
                arrayOf(
                    "",
                    0,
                    "",
                    0, // false
                    System.currentTimeMillis()
                )
            )
        }

        return cursor
    }

    private fun formatTime(minutes: Int): String {
        val hour = minutes / 60
        val minute = minutes % 60
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.prayershield.today"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = throw UnsupportedOperationException()
}
