package com.prayershield.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class BlockerAccessibilityService : AccessibilityService() {

    private var lastToastPackage: String? = null
    private var lastToastTime: Long = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (PrayerManager.isAppBlockedNow(packageName)) {
            blockAndNotify(packageName)
            return
        }

        // Always block the system uninstall-confirmation dialog while a prayer is due,
        // regardless of the user's blocked-app list.
        val isUninstallDialog = (packageName == "com.android.packageinstaller") ||
                (packageName == "com.google.android.packageinstaller")
        if (isUninstallDialog && PrayerManager.activeUnprayedWindow() != null) {
            blockAndNotify(packageName)
            return
        }

        // Optional stronger protection: block the whole Settings app while a prayer is due,
        // so the device-admin deactivation screen (Settings > Security > Device admin apps)
        // isn't reachable either. Off by default since it blocks all Settings access, not just that screen.
        val isSettingsApp = packageName == "com.android.settings"
        if (isSettingsApp && PrayerManager.isProtectSettingsEnabled() && PrayerManager.activeUnprayedWindow() != null) {
            blockAndNotify(packageName)
            return
        }
    }

    private fun blockAndNotify(packageName: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)

        val now = System.currentTimeMillis()
        if (packageName != lastToastPackage || now - lastToastTime > 3000) {
            val prayer = PrayerManager.activeUnprayedWindow()
            Toast.makeText(
                this,
                if (prayer != null) "Time for $prayer. Open Prayer Shield and mark it done to unlock this."
                else "Mark your current prayer as done to unlock this.",
                Toast.LENGTH_LONG,
            ).show()
            lastToastPackage = packageName
            lastToastTime = now
        }
    }

    override fun onInterrupt() {}
}