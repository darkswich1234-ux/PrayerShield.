package com.prayershield.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AndroidSettingsProvider(context: Context) : SettingsProvider {
    private val prefs: SharedPreferences = context.getSharedPreferences("prayer_shield_prefs", Context.MODE_PRIVATE)

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) { prefs.edit { putInt(key, value) } }

    override fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default
    override fun putString(key: String, value: String) { prefs.edit { putString(key, value) } }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) { prefs.edit { putBoolean(key, value) } }

    override fun getStringSet(key: String, default: Set<String>): Set<String> = prefs.getStringSet(key, default) ?: default
    override fun putStringSet(key: String, value: Set<String>) { prefs.edit { putStringSet(key, value) } }
}
