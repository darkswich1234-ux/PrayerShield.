package com.prayershield.app

import android.Manifest
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val locationPermissionRequestCode = 42

    private val prayerTimeButtons = mutableMapOf<String, Button>()
    private val prayerStatusViews = mutableMapOf<String, TextView>()
    private val prayedButtons = mutableListOf<Button>()
    private lateinit var appListContainer: LinearLayout
    private val checkBoxes = mutableListOf<Pair<CheckBox, String>>() // checkbox, packageName

    private lateinit var lockBanner: TextView
    private lateinit var streakText: TextView
    private lateinit var dateText: TextView
    private lateinit var btnUseLocation: Button
    private lateinit var btnSelectAll: Button
    private lateinit var btnSelectExceptEssential: Button
    private lateinit var btnSaveApps: Button
    private lateinit var deviceAdminComponent: ComponentName
    private lateinit var devicePolicyManager: DevicePolicyManager

    private lateinit var streakRing: CircularProgressIndicator
    private lateinit var ringStreakNumber: TextView
    private lateinit var nextGoalTitle: TextView
    private lateinit var nextGoalSubtitle: TextView
    private lateinit var prayerCardsRow: LinearLayout
    private val prayerCards = mutableMapOf<String, MaterialCardView>()

    private val deviceAdminLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        setupProtectionTab()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        deviceAdminComponent = ComponentName(this, PrayerShieldDeviceAdminReceiver::class.java)
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val prayerContainer = findViewById<LinearLayout>(R.id.prayerContainer)
        appListContainer = findViewById(R.id.appListContainer)
        lockBanner = findViewById(R.id.lockBanner)
        streakText = findViewById(R.id.streakText)
        dateText = findViewById(R.id.dateText)
        btnUseLocation = findViewById(R.id.btnUseLocation)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnSelectExceptEssential = findViewById(R.id.btnSelectExceptEssential)
        btnSaveApps = findViewById(R.id.btnSaveApps)
        streakRing = findViewById(R.id.streakRing)
        ringStreakNumber = findViewById(R.id.ringStreakNumber)
        nextGoalTitle = findViewById(R.id.nextGoalTitle)
        nextGoalSubtitle = findViewById(R.id.nextGoalSubtitle)
        prayerCardsRow = findViewById(R.id.prayerCardsRow)

        dateText.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())

        val prayerTabContent = findViewById<LinearLayout>(R.id.prayerTabContent)
        val appsTabContent = findViewById<LinearLayout>(R.id.appsTabContent)
        val protectionTabContent = findViewById<LinearLayout>(R.id.protectionTabContent)
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)

        bottomNav.setOnItemSelectedListener { item ->
            prayerTabContent.visibility = if (item.itemId == R.id.nav_prayers) View.VISIBLE else View.GONE
            appsTabContent.visibility = if (item.itemId == R.id.nav_apps) View.VISIBLE else View.GONE
            protectionTabContent.visibility = if (item.itemId == R.id.nav_protect) View.VISIBLE else View.GONE
            true
        }

        // Build the horizontal "check your prayers" cards
        for (prayer in PrayerManager.PRAYERS) {
            val card = MaterialCardView(this)
            val cardParams = LinearLayout.LayoutParams(100.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
            cardParams.marginEnd = 12.dp()
            card.layoutParams = cardParams
            card.radius = 20f.dp()
            card.cardElevation = 0f
            card.strokeWidth = 0

            val inner = LinearLayout(this)
            inner.orientation = LinearLayout.VERTICAL
            inner.setPadding(14.dp(), 16.dp(), 14.dp(), 16.dp())

            val nameView = TextView(this)
            nameView.text = prayer
            nameView.textSize = 14f
            nameView.setTypeface(nameView.typeface, android.graphics.Typeface.BOLD)

            val timeView = TextView(this)
            timeView.textSize = 12f
            timeView.setPadding(0, 4.dp(), 0, 8.dp())

            val statusView = TextView(this)
            statusView.textSize = 16f

            inner.addView(nameView)
            inner.addView(timeView)
            inner.addView(statusView)
            card.addView(inner)

            card.setOnClickListener {
                if (PrayerManager.canMarkPrayed(this, prayer)) {
                    PrayerManager.markPrayed(this, prayer)
                    refreshAll()
                    Toast.makeText(this, getString(R.string.prayed_toast_format, prayer), Toast.LENGTH_SHORT).show()
                } else if (!PrayerManager.isPrayed(this, prayer)) {
                    if (prayer == "Fajr") {
                        Toast.makeText(this, getString(R.string.fajr_restriction_toast), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.prayer_restriction_toast_format, prayer), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            card.setOnLongClickListener {
                showTimePicker(prayer, null)
                true
            }

            prayerCardsRow.addView(card)
            prayerCards[prayer] = card
            // stash the name/time/status TextViews via tag for later color+text updates
            card.tag = Triple(nameView, timeView, statusView)
        }

        // Build one row per prayer in the "Edit times" section
        for (prayer in PrayerManager.PRAYERS) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, 16, 0, 16)

            val label = TextView(this)
            label.text = prayer
            label.textSize = 16f
            label.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val timeBtn = Button(this)
            timeBtn.text = minutesToLabel(PrayerManager.getPrayerTimeMinutes(this, prayer))
            timeBtn.setOnClickListener { showTimePicker(prayer, timeBtn) }
            prayerTimeButtons[prayer] = timeBtn

            val status = TextView(this)
            status.setPadding(16, 0, 16, 0)
            prayerStatusViews[prayer] = status

            val prayedBtn = Button(this)
            prayedBtn.text = getString(R.string.mark_prayed_label)
            prayedBtn.setOnClickListener {
                if (PrayerManager.canMarkPrayed(this, prayer)) {
                    PrayerManager.markPrayed(this, prayer)
                    refreshAll()
                    Toast.makeText(this, getString(R.string.prayed_toast_format, prayer), Toast.LENGTH_SHORT).show()
                } else {
                    if (prayer == "Fajr") {
                        Toast.makeText(this, getString(R.string.fajr_restriction_toast), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.prayer_restriction_toast_format, prayer), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            prayedButtons.add(prayedBtn)

            row.addView(label)
            row.addView(timeBtn)
            row.addView(status)
            row.addView(prayedBtn)
            prayerContainer.addView(row)
        }

        buildAppList()
        refreshAll()

        btnSaveApps.setOnClickListener {
            val selected = checkBoxes.filter { it.first.isChecked }.map { it.second }.toSet()
            PrayerManager.setBlockedApps(this, selected)
            refreshWidgets()
            Toast.makeText(this, getString(R.string.apps_saved_toast), Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnEnableAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, getString(R.string.accessibility_instruction), Toast.LENGTH_LONG).show()
        }

        btnUseLocation.setOnClickListener {
            requestLocationAndCalculate()
        }

        btnSelectAll.setOnClickListener {
            checkBoxes.forEach { it.first.isChecked = true }
        }

        btnSelectExceptEssential.setOnClickListener {
            checkBoxes.forEach { (cb, pkg) -> cb.isChecked = !isEssentialApp(pkg) }
        }

        setupProtectionTab()
        
        findViewById<Button>(R.id.btnTipDeveloper).setOnClickListener {
            openKofi()
        }

        findViewById<Button>(R.id.btnResetToday).setOnClickListener {
            PrayerManager.resetToday(this)
            refreshAll()
            Toast.makeText(this, "Today's progress reset", Toast.LENGTH_SHORT).show()
        }

        if (!PrayerManager.hasSeenTipDialog(this)) {
            showTipDialog()
        }
    }

    private fun showTipDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.tip_title)
            .setMessage(R.string.tip_message)
            .setPositiveButton(R.string.tip_button) { _, _ ->
                openKofi()
                PrayerManager.setSeenTipDialog(this)
            }
            .setNegativeButton(R.string.dismiss_label) { _, _ ->
                PrayerManager.setSeenTipDialog(this)
            }
            .setCancelable(false)
            .show()
    }

    private fun openKofi() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, getString(R.string.kofi_url).toUri())
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_tip -> {
                openKofi()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dp(): Float = this * resources.displayMetrics.density

    // ---------- Dashboard: ring, next goal, prayer cards ----------

    private fun refreshAll() {
        refreshStatuses()
        updateLockState()
        updateStreak()
        updatePrayerCards()
        updateNextGoal()
        refreshWidgets()
    }

    private fun refreshWidgets() {
        PrayerWidgetProvider.refreshAll(this)
        PrayerGridWidgetProvider.refreshAll(this)
    }

    private fun updateStreak() {
        val streak = PrayerManager.getCurrentStreak(this)
        streakText.text = streak.toString()
        ringStreakNumber.text = streak.toString()
        val doneToday = PrayerManager.PRAYERS.count { PrayerManager.isPrayed(this, it) }
        streakRing.progress = doneToday
    }

    private fun updatePrayerCards() {
        val activePrayer = PrayerManager.activeUnprayedWindow(this)
        for (prayer in PrayerManager.PRAYERS) {
            val card = prayerCards[prayer] ?: continue
            @Suppress("UNCHECKED_CAST")
            val cardTag = card.tag as Triple<TextView, TextView, TextView>
            val nameView = cardTag.first
            val timeView = cardTag.second
            val statusView = cardTag.third
            timeView.text = minutesToLabel(PrayerManager.getPrayerTimeMinutes(this, prayer))

            val prayed = PrayerManager.isPrayed(this, prayer)
            val bgAttr: Int
            val textAttr: Int
            when {
                prayed -> {
                    statusView.text = "✓"
                    bgAttr = com.google.android.material.R.attr.colorPrimaryContainer
                    textAttr = com.google.android.material.R.attr.colorOnPrimaryContainer
                }
                prayer == activePrayer -> {
                    statusView.text = getString(R.string.due_now)
                    bgAttr = com.google.android.material.R.attr.colorErrorContainer
                    textAttr = com.google.android.material.R.attr.colorOnErrorContainer
                }
                else -> {
                    statusView.text = "○"
                    bgAttr = com.google.android.material.R.attr.colorSurfaceContainerHigh
                    textAttr = com.google.android.material.R.attr.colorOnSurface
                }
            }
            card.setCardBackgroundColor(getThemeColor(bgAttr))
            val textColor = getThemeColor(textAttr)
            nameView.setTextColor(textColor)
            timeView.setTextColor(textColor)
            statusView.setTextColor(textColor)
        }
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun updateNextGoal() {
        val activePrayer = PrayerManager.activeUnprayedWindow(this)
        if (activePrayer != null) {
            nextGoalTitle.text = getString(R.string.next_prayer_due_format, activePrayer)
            nextGoalSubtitle.text = getString(R.string.lock_banner_text)
            return
        }

        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        var bestPrayer: String? = null
        var bestDiff = Int.MAX_VALUE
        for (prayer in PrayerManager.PRAYERS) {
            if (PrayerManager.isPrayed(this, prayer)) continue
            val t = PrayerManager.getPrayerTimeMinutes(this, prayer)
            val diff = if (t >= nowMinutes) t - nowMinutes else (t + 24 * 60) - nowMinutes
            if (diff < bestDiff) {
                bestDiff = diff
                bestPrayer = prayer
            }
        }

        if (bestPrayer == null) {
            nextGoalTitle.text = getString(R.string.all_caught_up_emoji)
            nextGoalSubtitle.text = getString(R.string.all_caught_up_sub)
        } else {
            val hours = bestDiff / 60
            val mins = bestDiff % 60
            
            val timeStr = buildString {
                if (hours > 0) {
                    append(resources.getQuantityString(R.plurals.time_unit_hour, hours, hours))
                }
                if (hours > 0 && mins > 0) {
                    append(getString(R.string.time_unit_and))
                }
                if (mins > 0 || hours == 0) {
                    append(resources.getQuantityString(R.plurals.time_unit_minute, mins, mins))
                }
            }
            
            nextGoalTitle.text = getString(R.string.next_prayer_in_format, bestPrayer, timeStr)
            nextGoalSubtitle.text = getString(R.string.get_ready)
        }
    }

    // ---------- Locking settings while a prayer is overdue ----------

    private fun updateLockState() {
        val locked = PrayerManager.activeUnprayedWindow(this) != null
        lockBanner.visibility = if (locked) View.VISIBLE else View.GONE

        prayerTimeButtons.values.forEach { it.isEnabled = !locked }
        btnUseLocation.isEnabled = !locked
        btnSelectAll.isEnabled = !locked
        btnSelectExceptEssential.isEnabled = !locked
        btnSaveApps.isEnabled = !locked
        checkBoxes.forEach { it.first.isEnabled = !locked }
        // Mark-prayed buttons and prayer cards stay enabled on purpose - that's the way out of the lock.
    }

    // ---------- Protection tab: device admin + settings blocking ----------

    private fun setupProtectionTab() {
        val btnEnableDeviceAdmin = findViewById<Button>(R.id.btnEnableDeviceAdmin)
        val deviceAdminStatus = findViewById<TextView>(R.id.deviceAdminStatus)
        val checkboxProtectSettings = findViewById<CheckBox>(R.id.checkboxProtectSettings)

        checkboxProtectSettings.isChecked = PrayerManager.isProtectSettingsEnabled(this)
        checkboxProtectSettings.setOnCheckedChangeListener { _, checked ->
            PrayerManager.setProtectSettingsEnabled(this, checked)
        }

        fun refreshDeviceAdminStatus() {
            val active = devicePolicyManager.isAdminActive(deviceAdminComponent)
            deviceAdminStatus.text = if (active) getString(R.string.status_enabled) else getString(R.string.status_not_enabled)
            btnEnableDeviceAdmin.text = if (active) getString(R.string.uninstall_protection_on) else getString(R.string.enable_uninstall_protection)
            btnEnableDeviceAdmin.isEnabled = !active
        }
        refreshDeviceAdminStatus()

        btnEnableDeviceAdmin.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.device_admin_explanation)
            )
            deviceAdminLauncher.launch(intent)
        }
    }

    // ---------- Essential-app heuristic for "select all except essential" ----------

    private val essentialKeywords = listOf(
        "dialer", "contacts", "messaging", "mms", "sms", "camera",
        "deskclock", "clock", "settings", "phone", "maps", "gmail",
        "prayershield"
    )

    private fun isEssentialApp(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return essentialKeywords.any { lower.contains(it) }
    }

    // ---------- Location-based prayer time calculation ----------

    private fun requestLocationAndCalculate() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                locationPermissionRequestCode
            )
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = locationManager.getProviders(true)

        var bestLocation: Location? = null
        for (provider in providers) {
            val loc = try { locationManager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                bestLocation = loc
            }
        }

        if (bestLocation != null) {
            applyLocation(bestLocation)
            return
        }

        if (providers.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_location_provider), Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, getString(R.string.finding_location), Toast.LENGTH_SHORT).show()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.getCurrentLocation(
                    providers[0],
                    null,
                    ContextCompat.getMainExecutor(this)
                ) { location ->
                    if (location != null) applyLocation(location)
                }
            } else {
                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(providers[0], { location ->
                    applyLocation(location)
                }, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.location_permission_needed), Toast.LENGTH_LONG).show()
        }
    }

    private fun applyLocation(location: Location) {
        val times = PrayerTimeCalculator.calculateTodayMinutes(location.latitude, location.longitude)
        for ((prayer, minutes) in times) {
            PrayerManager.setPrayerTimeMinutes(this, prayer, minutes)
            prayerTimeButtons[prayer]?.text = minutesToLabel(minutes)
        }
        refreshAll()
        Toast.makeText(
            this,
            getString(R.string.location_success),
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequestCode && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            requestLocationAndCalculate()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun showTimePicker(prayer: String, button: Button?) {
        val current = PrayerManager.getPrayerTimeMinutes(this, prayer)
        val h = current / 60
        val m = current % 60
        TimePickerDialog(this, { _, hour, minute ->
            val minutes = hour * 60 + minute
            PrayerManager.setPrayerTimeMinutes(this, prayer, minutes)
            button?.text = minutesToLabel(minutes)
            refreshAll()
        }, h, m, false).show()
    }

    private fun minutesToLabel(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val amPm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return getString(R.string.time_format, h12, m, amPm)
    }

    private fun refreshStatuses() {
        for (prayer in PrayerManager.PRAYERS) {
            val prayed = PrayerManager.isPrayed(this, prayer)
            prayerStatusViews[prayer]?.text = if (prayed) getString(R.string.prayed_status) else getString(R.string.not_yet_status)
            
            // Find the "Mark prayed" button for this row and update its enabled state
            val btnIdx = PrayerManager.PRAYERS.indexOf(prayer)
            if (btnIdx < prayedButtons.size) {
                prayedButtons[btnIdx].isEnabled = PrayerManager.canMarkPrayed(this, prayer)
            }
        }
    }

    private fun buildAppList() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val alreadyBlocked = PrayerManager.getBlockedApps(this)

        for (app in apps) {
            val pkg = app.activityInfo.packageName
            if (pkg == packageName) continue

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, 8, 0, 8)

            val cb = CheckBox(this)
            cb.isChecked = alreadyBlocked.contains(pkg)

            val label = TextView(this)
            label.text = app.loadLabel(pm).toString()
            label.setPadding(16, 0, 0, 0)

            row.addView(cb)
            row.addView(label)
            appListContainer.addView(row)

            checkBoxes.add(Pair(cb, pkg))
        }
    }
}
