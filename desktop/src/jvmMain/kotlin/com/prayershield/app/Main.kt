package com.prayershield.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    PrayerManager.init(DesktopSettingsProvider())
    
    Window(onCloseRequest = ::exitApplication, title = "Prayer Shield") {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var streak by remember { mutableStateOf(PrayerManager.getCurrentStreak()) }
    var activePrayer by remember { mutableStateOf(PrayerManager.activeUnprayedWindow()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Day Streak: $streak", style = MaterialTheme.typography.headlineLarge)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (activePrayer != null) {
            Text("Due Now: $activePrayer", color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                PrayerManager.markPrayed(activePrayer!!)
                activePrayer = PrayerManager.activeUnprayedWindow()
                streak = PrayerManager.getCurrentStreak()
            }) {
                Text("Mark as Prayed")
            }
        } else {
            Text("All caught up! 🎉")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Prayer Times", style = MaterialTheme.typography.titleMedium)
        
        PrayerManager.PRAYERS.forEach { prayer ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = SpaceBetween
            ) {
                Text(prayer)
                val isDone = PrayerManager.isPrayed(prayer)
                Text(if (isDone) "✓" else "○")
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(onClick = { /* Open Ko-fi */ }) {
            Text("Support the Developer")
        }
    }
}

private val SpaceBetween = Arrangement.SpaceBetween
