package com.prayershield.app.ui

import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.prayershield.app.*
import com.prayershield.app.ui.theme.PrayerShieldTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ComposeDashboard(
    onSwitchToClassic: () -> Unit,
    onRequestLocation: () -> Unit
) {
    val context = LocalContext.current
    var isAmoled by remember { mutableStateOf(PrayerManager.isAmoledBlackEnabled(context)) }
    
    PrayerShieldTheme(useAmoled = isAmoled) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainContent(
                onSwitchToClassic = onSwitchToClassic,
                onRequestLocation = onRequestLocation,
                isAmoled = isAmoled,
                onAmoledToggle = {
                    isAmoled = it
                    PrayerManager.setAmoledBlackEnabled(context, it)
                }
            )
        }
    }
}

enum class DashboardTab { PRAYERS, APPS, PROTECT }

@Composable
fun MainContent(
    onSwitchToClassic: () -> Unit,
    onRequestLocation: () -> Unit,
    isAmoled: Boolean,
    onAmoledToggle: (Boolean) -> Unit
) {
    var currentTab by remember { mutableStateOf(DashboardTab.PRAYERS) }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                NavigationBarItem(
                    selected = currentTab == DashboardTab.PRAYERS,
                    onClick = { currentTab = DashboardTab.PRAYERS },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Prayers") },
                    label = { Text("Prayers") }
                )
                NavigationBarItem(
                    selected = currentTab == DashboardTab.APPS,
                    onClick = { currentTab = DashboardTab.APPS },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Apps") },
                    label = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = currentTab == DashboardTab.PROTECT,
                    onClick = { currentTab = DashboardTab.PROTECT },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "Protect") },
                    label = { Text("Protect") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentTab) {
                DashboardTab.PRAYERS -> PrayersTab(onRequestLocation)
                DashboardTab.APPS -> AppsTab()
                DashboardTab.PROTECT -> ProtectTab(onSwitchToClassic, isAmoled, onAmoledToggle)
            }
        }
    }
}

@Composable
fun PrayersTab(onRequestLocation: () -> Unit) {
    val context = LocalContext.current
    var streak by remember { mutableIntStateOf(PrayerManager.getCurrentStreak(context)) }
    var activePrayer by remember { mutableStateOf(PrayerManager.activeUnprayedWindow(context)) }
    
    val doneToday = PrayerManager.PRAYERS.count { PrayerManager.isPrayed(context, it) }
    val allDone = doneToday == PrayerManager.PRAYERS.size
    
    val scrollState = rememberScrollState()
    val dateStr = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(dateStr, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(Modifier.height(32.dp))
        
        // Streak Ring
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            CircularProgressIndicator(
                progress = { doneToday / 5f },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(streak.toString(), fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Text("Day Streak", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(48.dp))
        
        NextGoalCard(activePrayer, allDone)

        Spacer(Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrayerManager.PRAYERS.forEach { prayer ->
                PrayerCard(prayer, activePrayer) {
                    streak = PrayerManager.getCurrentStreak(context)
                    activePrayer = PrayerManager.activeUnprayedWindow(context)
                    PrayerWidgetProvider.refreshAll(context)
                    PrayerGridWidgetProvider.refreshAll(context)
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onRequestLocation,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Update Location")
        }

        Spacer(Modifier.height(24.dp))

        Text("Edit prayer times", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))

        PrayerManager.PRAYERS.forEach { prayer ->
            EditTimeRow(prayer) {
                activePrayer = PrayerManager.activeUnprayedWindow(context)
            }
        }
    }
}

@Composable
fun NextGoalCard(activePrayer: String?, allDone: Boolean) {
    val context = LocalContext.current
    val cardColor = if (activePrayer != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary
    val onCardColor = if (activePrayer != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
    
    val nextPrayerName = remember(activePrayer, allDone) {
        if (activePrayer != null || allDone) null
        else {
            val nowMinutes = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
            PrayerManager.PRAYERS.find { !PrayerManager.isPrayed(context, it) && PrayerManager.getPrayerTimeMinutes(context, it) > nowMinutes }
                ?: PrayerManager.PRAYERS.firstOrNull { !PrayerManager.isPrayed(context, it) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = when {
                    activePrayer != null -> "$activePrayer is due now"
                    allDone -> "All caught up! 🎉"
                    nextPrayerName != null -> "Next: $nextPrayerName"
                    else -> "Prepare for prayer"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = onCardColor
            )
            Text(
                text = when {
                    activePrayer != null -> "Complete it to unlock your apps."
                    allDone -> "Every prayer marked for today"
                    nextPrayerName != null -> "Starts at ${minutesToLabel(PrayerManager.getPrayerTimeMinutes(context, nextPrayerName))}"
                    else -> "Check your schedule below"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = onCardColor
            )
        }
    }
}

@Composable
fun PrayerCard(prayer: String, activePrayer: String?, onMarked: () -> Unit) {
    val context = LocalContext.current
    val isPrayed = PrayerManager.isPrayed(context, prayer)
    val isActive = prayer == activePrayer
    
    val bgColor = when {
        isPrayed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isActive -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val onColor = when {
        isPrayed -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.width(110.dp).clickable(enabled = !isPrayed) {
            if (PrayerManager.canMarkPrayed(context, prayer)) {
                PrayerManager.markPrayed(context, prayer)
                onMarked()
                Toast.makeText(context, "$prayer marked as prayed!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Not time for $prayer yet.", Toast.LENGTH_SHORT).show()
            }
        },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(prayer, fontWeight = FontWeight.Bold, color = onColor)
            Spacer(Modifier.height(4.dp))
            Text(
                text = minutesToLabel(PrayerManager.getPrayerTimeMinutes(context, prayer)),
                style = MaterialTheme.typography.bodySmall,
                color = onColor.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Text(if (isPrayed) "✓ Done" else if (isActive) "Due" else "○ Wait", style = MaterialTheme.typography.labelSmall, color = onColor)
        }
    }
}

@Composable
fun EditTimeRow(prayer: String, onTimeChanged: () -> Unit) {
    val context = LocalContext.current
    var minutes by remember { mutableIntStateOf(PrayerManager.getPrayerTimeMinutes(context, prayer)) }
    
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(prayer, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        
        TextButton(
            onClick = {
                val h = minutes / 60
                val m = minutes % 60
                TimePickerDialog(context, { _, hour, minute ->
                    val newMinutes = hour * 60 + minute
                    PrayerManager.setPrayerTimeMinutes(context, prayer, newMinutes)
                    minutes = newMinutes
                    onTimeChanged()
                }, h, m, false).show()
            },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(minutesToLabel(minutes), fontWeight = FontWeight.Bold)
        }
    }
}

data class AppInfo(val name: String, val packageName: String, val icon: Drawable)

@Composable
fun AppsTab() {
    val context = LocalContext.current
    val pm = context.packageManager
    
    val essentialKeywords = remember {
        listOf(
            "dialer", "contacts", "messaging", "mms", "sms", "camera",
            "deskclock", "clock", "settings", "phone", "maps", "gmail",
            "prayershield"
        )
    }

    fun isEssential(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return essentialKeywords.any { lower.contains(it) }
    }
    
    val installedApps = remember {
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { 
                AppInfo(
                    it.loadLabel(pm).toString(),
                    it.activityInfo.packageName,
                    it.loadIcon(pm)
                )
            }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName } // Don't block self
            .sortedBy { it.name.lowercase() }
    }
    
    val blockedApps = remember { mutableStateListOf<String>().apply { addAll(PrayerManager.getBlockedApps(context)) } }
    
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Protected Apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val currentlyBlocked = PrayerManager.getBlockedApps(context)
                val allPkgs = installedApps.map { it.packageName }.toSet()
                
                if (currentlyBlocked.size >= allPkgs.size) {
                    PrayerManager.setBlockedApps(context, emptySet())
                    blockedApps.clear()
                } else {
                    PrayerManager.setBlockedApps(context, allPkgs)
                    blockedApps.clear()
                    blockedApps.addAll(allPkgs)
                }
                PrayerWidgetProvider.refreshAll(context)
                PrayerGridWidgetProvider.refreshAll(context)
            }) {
                Text(if (blockedApps.size >= installedApps.size) "Clear All" else "Select All")
            }
        }
        
        Text("Selected apps will be restricted during prayer times.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val nonEssential = installedApps.filter { !isEssential(it.packageName) }.map { it.packageName }.toSet()
                PrayerManager.setBlockedApps(context, nonEssential)
                blockedApps.clear()
                blockedApps.addAll(nonEssential)
                PrayerWidgetProvider.refreshAll(context)
                PrayerGridWidgetProvider.refreshAll(context)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        ) {
            Text("Block All Except Essential", fontSize = 14.sp)
        }
        
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(installedApps) { app ->
                val isBlocked = blockedApps.contains(app.packageName)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val newSet = PrayerManager.getBlockedApps(context)
                        if (isBlocked) {
                            newSet.remove(app.packageName)
                            blockedApps.remove(app.packageName)
                        } else {
                            newSet.add(app.packageName)
                            blockedApps.add(app.packageName)
                        }
                        PrayerManager.setBlockedApps(context, newSet)
                        PrayerWidgetProvider.refreshAll(context)
                        PrayerGridWidgetProvider.refreshAll(context)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = if (isBlocked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            bitmap = app.icon.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.name, fontWeight = if (isBlocked) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Checkbox(
                            checked = isBlocked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProtectTab(onSwitchToClassic: () -> Unit, isAmoled: Boolean, onAmoledToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    var isSleepSync by remember { mutableStateOf(PrayerManager.isSleepShieldSyncEnabled(context)) }
    var isProtectSettings by remember { mutableStateOf(PrayerManager.isProtectSettingsEnabled(context)) }
    var isAutoLocation by remember { mutableStateOf(PrayerManager.isAutoLocationEnabled(context)) }
    
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComp = ComponentName(context, PrayerShieldDeviceAdminReceiver::class.java)
    var isAdminActive by remember { mutableStateOf(dpm.isAdminActive(adminComp)) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Personalization", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        
        SettingsCard {
            ToggleRow("AMOLED Black", "True black background for OLED screens.", isAmoled, onAmoledToggle)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    PrayerManager.setUiStyle(context, "Classic")
                    onSwitchToClassic()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Switch to Classic UI")
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("Integrations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        SettingsCard {
            ToggleRow("Sleep Shield Sync", "Let Sleep Shield read your Fajr time.", isSleepSync) {
                isSleepSync = it
                PrayerManager.setSleepShieldSyncEnabled(context, it)
            }
            Spacer(Modifier.height(16.dp))
            ToggleRow("Auto Location", "Update prayer times in the background.", isAutoLocation) {
                isAutoLocation = it
                PrayerManager.setAutoLocationEnabled(context, it)
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("Security", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        SettingsCard {
            ToggleRow("Protect Settings", "Block Settings app during prayer times.", isProtectSettings) {
                isProtectSettings = it
                PrayerManager.setProtectSettingsEnabled(context, it)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (!isAdminActive) {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects the app from being uninstalled.")
                        }
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isAdminActive
            ) {
                Text(if (isAdminActive) "Uninstall Protection is ON" else "Enable Uninstall Protection")
            }
        }

        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = { 
                PrayerManager.resetToday(context)
                PrayerWidgetProvider.refreshAll(context)
                PrayerGridWidgetProvider.refreshAll(context)
                Toast.makeText(context, "Progress reset for today", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Reset Today's Progress")
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.background == Color.Black) Color(0xFF111111) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun ToggleRow(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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
    return "$h12:${m.toString().padStart(2, '0')} $amPm"
}
