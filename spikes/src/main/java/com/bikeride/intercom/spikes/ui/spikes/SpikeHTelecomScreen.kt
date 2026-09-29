package com.bikeride.intercom.spikes.ui.spikes

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
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
import com.bikeride.intercom.spikes.measurement.MeasurementCollector
import com.bikeride.intercom.spikes.telecom.SpikeConnectionService
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Spike H: Self-Managed Telecom Call Integration
 *
 * Validates Android Telecom framework:
 * - Registers PhoneAccount with CAPABILITY_SELF_MANAGED
 * - Checks Bluetooth SCO auto-prioritization
 * - Inspects cellular call coexistence (call swapping/holding)
 * - Evaluates process priority boost
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpikeHTelecomScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val collector = remember { MeasurementCollector("SpikeH-Telecom") }
    val clipboard = LocalClipboardManager.current
    val logLines by collector.log.collectAsState()

    val telecomManager = remember {
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    }

    var isAccountRegistered by remember { mutableStateOf(false) }
    var isCallActive by remember { mutableStateOf(false) }
    var phoneAccountHandle by remember { mutableStateOf<PhoneAccountHandle?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            collector.addLog("MANAGE_OWN_CALLS permission granted")
        } else {
            collector.recordError("MANAGE_OWN_CALLS permission denied")
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 26) {
            permissionLauncher.launch(Manifest.permission.MANAGE_OWN_CALLS)
        }

        if (telecomManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val componentName = ComponentName(context, SpikeConnectionService::class.java)
            val handle = PhoneAccountHandle(componentName, "IntercomVoipAccount")
            phoneAccountHandle = handle

            val existingAccount = telecomManager.getPhoneAccount(handle)
            isAccountRegistered = existingAccount != null
            collector.addLog("Telecom initialized. Existing account: ${existingAccount != null}")
        } else {
            collector.recordError("TelecomManager not available or API < 26")
        }
    }

    fun registerPhoneAccount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null && phoneAccountHandle != null) {
            try {
                val account = PhoneAccount.builder(phoneAccountHandle, "Smart Intercom Ride")
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .setShortDescription("Motorcycle Group Intercom")
                    .build()

                telecomManager.registerPhoneAccount(account)
                isAccountRegistered = true
                collector.recordEvent("PHONE_ACCOUNT_REGISTERED")
                collector.addLog("✅ Self-managed PhoneAccount successfully registered")
                Toast.makeText(context, "PhoneAccount registered!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                collector.recordError("Failed to register PhoneAccount: ${e.message}")
            }
        }
    }

    fun startTestVoipCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null && phoneAccountHandle != null) {
            try {
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                    putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                }

                val uri = Uri.fromParts("tel", "Rider-Mesh-1", null)
                telecomManager.addNewIncomingCall(phoneAccountHandle, extras)
                isCallActive = true
                collector.recordEvent("INCOMING_CALL_TRIGGERED")
                collector.addLog("📞 Test Self-Managed Intercom Call initiated")
            } catch (e: Exception) {
                collector.recordError("Failed to initiate call: ${e.message}")
            }
        }
    }

    fun endTestVoipCall() {
        try {
            SpikeConnectionService.activeConnection?.onDisconnect()
            isCallActive = false
            collector.recordEvent("CALL_ENDED")
            collector.addLog("Call ended")
        } catch (e: Exception) {
            collector.recordError("Error ending call: ${e.message}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Spike H: Self-Managed Telecom", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (isCallActive) "VoIP Call Active" else if (isAccountRegistered) "Account Registered" else "Unregistered",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCallActive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        endTestVoipCall()
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

            // Overview Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C4A6E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Android Telecom Framework (Self-Managed)", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Integrating ConnectionService allows the app to be treated as a native voice call. The OS prioritizes helmet Bluetooth SCO and properly arbitrates when a real phone call arrives.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
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
                        Text("Telecom Actions", fontWeight = FontWeight.SemiBold)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { registerPhoneAccount() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAccountRegistered) Color(0xFF1E3A5F) else Color(0xFF38BDF8)
                                )
                            ) {
                                Text(if (isAccountRegistered) "Registered ✓" else "1. Register")
                            }

                            Button(
                                onClick = { if (isCallActive) endTestVoipCall() else startTestVoipCall() },
                                enabled = isAccountRegistered,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCallActive) Color(0xFFDC2626) else Color(0xFF4ADE80)
                                )
                            ) {
                                Text(if (isCallActive) "Hang Up" else "2. Start Call")
                            }
                        }
                    }
                }
            }

            // Benefits Checklist Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Architecture Evaluation Criteria", fontWeight = FontWeight.SemiBold)
                        Text("• Audio Focus: Automatically grabs VOIP audio focus without manual ducking logic", fontSize = 12.sp)
                        Text("• BT Routing: Telecom natively hands off to helmet SCO without flakey reflection", fontSize = 12.sp)
                        Text("• Cellular Interop: Pauses intercom smoothly when cell call rings; resumes on hangup", fontSize = 12.sp)
                        Text("• Process Priority: Granted foreground telecom process score (lowest OOM kill chance)", fontSize = 12.sp)
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
                            Toast.makeText(context, "Telecom log copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy CSV")
                    }

                    OutlinedButton(
                        onClick = {
                            collector.clear()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }
                }
            }

            // Logs
            item {
                Text("Telecom Event Log", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }

            items(logLines.takeLast(25)) { line ->
                Text(
                    line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = when {
                        "ERROR" in line -> Color(0xFFFF6B6B)
                        "SUCCESS" in line || "REGISTERED" in line -> Color(0xFF4ADE80)
                        "EVENT" in line -> Color(0xFF38BDF8)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
