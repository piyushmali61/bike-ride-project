package com.bikeride.intercom.spikes.ui.spikes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bikeride.intercom.spikes.fgs.SpikeFgsService
import com.bikeride.intercom.spikes.measurement.MeasurementCollector
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Spike G: Foreground Service (FGS) Start Rules & Doze Test
 *
 * Verifies Android 14+ (API 34) and Android 15 restrictions:
 * - FOREGROUND_SERVICE_MICROPHONE cannot be launched from background without exemption.
 * - Tests direct launch vs delayed background launch.
 * - Checks battery optimization whitelist status.
 * - Provides instructions for testing deep Doze (`dumpsys deviceidle force-idle`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpikeGFgsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val collector = remember { MeasurementCollector("SpikeG-FGS") }
    val clipboard = LocalClipboardManager.current
    val logLines by collector.log.collectAsState()
    val scope = rememberCoroutineScope()

    var isServiceRunning by remember { mutableStateOf(SpikeFgsService.isRunning) }
    var countdownSec by remember { mutableIntStateOf(0) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var isIgnoringBatteryOptimizations by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            collector.addLog("Permissions granted for Mic + Notifications")
        } else {
            collector.recordError("Permissions denied: ${results.filter { !it.value }.keys}")
        }
    }

    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
        collector.addLog("Spike G initialized. OS API: ${Build.VERSION.SDK_INT}")
    }

    fun startForegroundServiceDirectly() {
        lastError = null
        try {
            val intent = Intent(context, SpikeFgsService::class.java).apply {
                action = SpikeFgsService.ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
            isServiceRunning = true
            collector.recordEvent("FGS_DIRECT_START_SUCCESS")
            collector.addLog("✅ Started FGS from foreground activity")
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            collector.recordError("FGS Direct Start Failed: $lastError")
        }
    }

    fun stopForegroundService() {
        try {
            val intent = Intent(context, SpikeFgsService::class.java).apply {
                action = SpikeFgsService.ACTION_STOP
            }
            context.startService(intent)
            isServiceRunning = false
            collector.recordEvent("FGS_STOPPED")
            collector.addLog("FGS stop command sent")
        } catch (e: Exception) {
            collector.recordError("FGS stop error: ${e.message}")
        }
    }

    fun testDelayedBackgroundStart() {
        lastError = null
        countdownSec = 8
        collector.addLog("⏳ Delayed start triggered: Press HOME now to test background launch in 8 seconds!")

        scope.launch {
            while (countdownSec > 0) {
                delay(1000)
                countdownSec--
            }
            try {
                val intent = Intent(context, SpikeFgsService::class.java).apply {
                    action = SpikeFgsService.ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
                isServiceRunning = true
                collector.recordEvent("FGS_BACKGROUND_START_SUCCESS")
                collector.addLog("⚠️ Background start SUCCEEDED (OEM allowed background start)")
            } catch (e: Exception) {
                // Expected on Android 14+ ForegroundServiceStartNotAllowedException
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                collector.recordEvent("FGS_BACKGROUND_START_BLOCKED", mapOf("exception" to (e.javaClass.simpleName)))
                collector.addLog("❌ Expected Failure: $lastError")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Spike G: FGS Start Rules & Doze", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (isServiceRunning) "FGS Running (Mic+Device)" else "FGS Stopped",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceRunning) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        stopForegroundService()
                        onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Android Version & Policy Notice Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Android 14+ FGS Restrictions", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Android 14+ forbids launching FOREGROUND_SERVICE_MICROPHONE from the background (e.g. FCM push or alarm) without a visible Activity or CompanionDevice exemption.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            "Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})",
                            fontSize = 12.sp,
                            color = Color(0xFF4FDBC4),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Battery Optimization Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isIgnoringBatteryOptimizations) Color(0xFF132A13) else Color(0xFF332000)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Battery Optimization Status", fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text(
                                    if (isIgnoringBatteryOptimizations) "✅ Whitelisted (Exempt from Doze network cut)"
                                    else "⚠️ Optimized (App network may cut when phone sleeps)",
                                    fontSize = 12.sp,
                                    color = if (isIgnoringBatteryOptimizations) Color(0xFF4ADE80) else Color(0xFFFFB951)
                                )
                            }
                        }

                        if (!isIgnoringBatteryOptimizations) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB951))
                            ) {
                                Text("Request Battery Exemption", color = Color.Black)
                            }
                        }
                    }
                }
            }

            // Controls Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("FGS Start Tests", fontWeight = FontWeight.SemiBold)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { if (isServiceRunning) stopForegroundService() else startForegroundServiceDirectly() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isServiceRunning) Color(0xFFDC2626) else Color(0xFF4ADE80)
                                )
                            ) {
                                Text(if (isServiceRunning) "Stop FGS" else "1. Foreground Start")
                            }

                            Button(
                                onClick = { testDelayedBackgroundStart() },
                                enabled = countdownSec == 0,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B8AFF))
                            ) {
                                Text(if (countdownSec > 0) "Home Now! ($countdownSec)" else "2. Background Test")
                            }
                        }

                        if (lastError != null) {
                            Text(
                                "Last Result: $lastError",
                                fontSize = 12.sp,
                                color = Color(0xFFFF6B6B),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Doze Test Command Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Deep Doze Test via ADB", fontWeight = FontWeight.SemiBold)
                        Text("1. Start the FGS using 'Foreground Start'", fontSize = 12.sp)
                        Text("2. Turn screen OFF via power button", fontSize = 12.sp)
                        Text("3. Run the following command in terminal:", fontSize = 12.sp)

                        val adbCmd = "adb shell dumpsys deviceidle force-idle"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                adbCmd,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Text("4. Verify audio recording & Wi-Fi transmission continue uninterrupted.", fontSize = 12.sp)
                    }
                }
            }

            // Export & Reset
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val csv = collector.exportCsv()
                            clipboard.setText(AnnotatedString(csv))
                            Toast.makeText(context, "FGS test log copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy CSV")
                    }

                    OutlinedButton(
                        onClick = {
                            collector.clear()
                            lastError = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }
                }
            }

            // Logs
            item {
                Text("FGS Event Log", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }

            items(logLines.takeLast(25)) { line ->
                Text(
                    line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = when {
                        "ERROR" in line || "BLOCKED" in line -> Color(0xFFFF6B6B)
                        "SUCCESS" in line -> Color(0xFF4ADE80)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
