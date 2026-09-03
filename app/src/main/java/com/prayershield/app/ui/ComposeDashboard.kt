package com.prayershield.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prayershield.app.PrayerManager
import com.prayershield.app.ui.theme.PrayerShieldTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ComposeDashboard(onSwitchToClassic: () -> Unit) {
    val context = LocalContext.current
    val isAmoled = remember { PrayerManager.isAmoledBlackEnabled(context) }
    
    PrayerShieldTheme(useAmoled = isAmoled) {
        MainContent(onSwitchToClassic)
    }
}

enum class DashboardTab { PRAYERS, APPS, PROTECT }

@Composable
fun MainContent(onSwitchToClassic: () -> Unit) {
    var currentTab by remember { mutableStateOf(DashboardTab.PRAYERS) }
    
    Scaffold(
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
                    icon = { Icon(Icons.Default.Menu, contentDescription = "Apps") },
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
                DashboardTab.PRAYERS -> PrayersTab()
                DashboardTab.APPS -> AppsTab()
                DashboardTab.PROTECT -> ProtectTab(onSwitchToClassic)
            }
        }
    }
}

@Composable
fun PrayersTab() {
    val context = LocalContext.current
    var streak by remember { mutableStateOf(PrayerManager.getCurrentStreak(context)) }
    var activePrayer by remember { mutableStateOf(PrayerManager.activeUnprayedWindow(context)) }
    val dateStr = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(dateStr, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(Modifier.height(32.dp))
        
        // Streak Ring
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            val doneToday = PrayerManager.PRAYERS.count { PrayerManager.isPrayed(context, it) }
            CircularProgressIndicator(
                progress = { doneToday / 5f },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp,
                trackColor = MaterialTheme.colorScheme.surface,
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(streak.toString(), fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Text("Day Streak", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(48.dp))
        
        NextGoalCard(activePrayer)

        Spacer(Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrayerManager.PRAYERS.forEach { prayer ->
                PrayerCard(prayer, activePrayer) {
                    streak = PrayerManager.getCurrentStreak(context)
                    activePrayer = PrayerManager.activeUnprayedWindow(context)
                }
            }
        }
    }
}

@Composable
fun NextGoalCard(activePrayer: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (activePrayer != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (activePrayer != null) "$activePrayer is due now" else "All caught up! 🎉",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (activePrayer != null) "Complete it to unlock your apps." else "Every prayer marked for today",
                style = MaterialTheme.typography.bodyMedium
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
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.width(110.dp).clickable(enabled = !isPrayed) {
            if (PrayerManager.canMarkPrayed(context, prayer)) {
                PrayerManager.markPrayed(context, prayer)
                onMarked()
            }
        },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(prayer, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(if (isPrayed) "Done" else if (isActive) "Due" else "Wait", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AppsTab() {
    val context = LocalContext.current
    val blockedApps = remember { PrayerManager.getBlockedApps(context) }
    
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Protected Apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        
        Text("These apps will be blocked during prayer windows.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(Modifier.height(24.dp))
        
        // This is a simplified list for the sake of the example
        // In a real app, you'd fetch the installed apps list.
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(blockedApps.toList()) { pkg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(pkg, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun ProtectTab(onSwitchToClassic: () -> Unit) {
    val context = LocalContext.current
    var isAmoled by remember { mutableStateOf(PrayerManager.isAmoledBlackEnabled(context)) }
    
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("UI Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AMOLED Black", modifier = Modifier.weight(1f))
                    Switch(checked = isAmoled, onCheckedChange = {
                        isAmoled = it
                        PrayerManager.setAmoledBlackEnabled(context, it)
                    })
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    PrayerManager.setUiStyle(context, "Classic")
                    onSwitchToClassic()
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("Switch to Classic UI")
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text("System", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = { PrayerManager.resetToday(context) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Reset Today's Progress")
        }
    }
}
