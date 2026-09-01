package com.prayershield.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class PrayerShieldDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Uninstall protection turned on.", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Uninstall protection turned off.", Toast.LENGTH_SHORT).show()
    }

    // Shown right before the user confirms turning off admin access (the step required
    // before uninstalling). Android always lets the user proceed after this warning —
    // there's no API for a normal app to block it outright — but this is the one place
    // we can inject a contextual nudge at the moment it matters most.
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val activePrayer = PrayerManager.activeUnprayedWindow()
        return if (activePrayer != null) {
            "$activePrayer hasn't been marked as prayed yet. Turning this off will let you uninstall Prayer Shield without completing it. Continue?"
        } else {
            "Turning this off removes uninstall protection for Prayer Shield. Continue?"
        }
    }
}