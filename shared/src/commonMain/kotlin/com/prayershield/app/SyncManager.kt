package com.prayershield.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SyncData(
    val completedDates: Set<String>
)

object SyncManager {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Generates a JSON string containing the current streak data.
     */
    fun generateSyncPayload(): String {
        val data = SyncData(
            completedDates = PrayerManager.getCompletedDates()
        )
        return json.encodeToString(data)
    }

    /**
     * Merges incoming sync data with local data.
     * We use a merge strategy (Union of dates) so no data is ever lost.
     */
    fun applySyncPayload(payload: String): Boolean {
        return try {
            val incoming = json.decodeFromString<SyncData>(payload)
            val local = PrayerManager.getCompletedDates()
            
            // Merge both sets of dates
            val merged = local.toMutableSet().apply {
                addAll(incoming.completedDates)
            }
            
            PrayerManager.setCompletedDates(merged)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/** 
 * Cross-platform timestamp helper placeholder.
 */
fun currentTimeMillis(): Long = 0 
