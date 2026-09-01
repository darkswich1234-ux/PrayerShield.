package com.prayershield.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.application
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

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
    var showQr by remember { mutableStateOf(false) }

    if (showQr) {
        DialogWindow(onCloseRequest = { showQr = false }, title = "Sync with Phone", state = rememberDialogState(width = 400.dp, height = 500.dp)) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Scan this QR in the phone app", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val qrBitmap = remember {
                        val writer = QRCodeWriter()
                        val matrix = writer.encode(SyncManager.generateSyncPayload(), BarcodeFormat.QR_CODE, 400, 400)
                        MatrixToImageWriter.toBufferedImage(matrix).toComposeImageBitmap()
                    }
                    
                    androidx.compose.foundation.Image(
                        bitmap = qrBitmap,
                        contentDescription = "Sync QR",
                        modifier = Modifier.size(250.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showQr = false }) {
                        Text("Done")
                    }
                }
            }
        }
    }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val chooser = JFileChooser()
                    chooser.dialogTitle = "Save Sync File"
                    chooser.fileFilter = FileNameExtensionFilter("JSON Files", "json")
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        var file = chooser.selectedFile
                        if (!file.name.endsWith(".json")) {
                            file = File(file.absolutePath + ".json")
                        }
                        file.writeText(SyncManager.generateSyncPayload())
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Export Streak")
            }

            Button(
                onClick = { showQr = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("QR Sync")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { /* Open Ko-fi */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Support the Developer")
        }
    }
}

private val SpaceBetween = Arrangement.SpaceBetween
