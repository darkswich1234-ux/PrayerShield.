package com.prayershield.app

import java.util.prefs.Preferences

class DesktopSettingsProvider : SettingsProvider {
    private val prefs = Preferences.userNodeForPackage(DesktopSettingsProvider::class.java)

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) { prefs.putInt(key, value) }

    override fun getString(key: String, default: String): String = prefs.get(key, default)
    override fun putString(key: String, value: String) { prefs.put(key, value) }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) { prefs.putBoolean(key, value) }

    override fun getStringSet(key: String, default: Set<String>): Set<String> {
        val s = prefs.get(key, "")
        return if (s.isEmpty()) default else s.split(",").toSet()
    }

    override fun putStringSet(key: String, value: Set<String>) {
        prefs.put(key, value.joinToString(","))
    }
}
