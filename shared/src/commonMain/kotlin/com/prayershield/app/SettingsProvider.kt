package com.prayershield.app

/**
 * Interface to abstract storage so we can support both Android (SharedPreferences)
 * and Desktop (Files/Preferences).
 */
interface SettingsProvider {
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getStringSet(key: String, default: Set<String>): Set<String>
    fun putStringSet(key: String, value: Set<String>)
}
