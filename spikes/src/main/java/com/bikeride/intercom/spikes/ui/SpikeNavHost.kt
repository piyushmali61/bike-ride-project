package com.bikeride.intercom.spikes.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bikeride.intercom.spikes.ui.spikes.*

@Composable
fun SpikeNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "launcher") {
        composable("launcher") {
            SpikeLauncher(
                onSpikeSelected = { route -> navController.navigate(route) }
            )
        }
        composable("spike_a_nearby") {
            SpikeANearbyScreen(onBack = { navController.popBackStack() })
        }
        composable("spike_b_wifidirect") {
            SpikeBWifiDirectScreen(onBack = { navController.popBackStack() })
        }
        composable("spike_c_coexistence") {
            SpikeCCoexistenceScreen(onBack = { navController.popBackStack() })
        }
        composable("spike_d_bluetooth") {
            SpikeDBluetoothScreen(onBack = { navController.popBackStack() })
        }
        composable("spike_e_adr") {
            SpikeEAdrScreen(onBack = { navController.popBackStack() })
        }
        composable("spike_f_datachannel") {
            SpikeFDataChannelScreen(onBack = { navController.popBackStack() })
        }
        composable("spike_g_fgs") {
            SpikeGFgsScreen(onBack = { navController.popBackStack() })
        }
        composable("spike_h_telecom") {
            SpikeHTelecomScreen(onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpikeLauncher(onSpikeSelected: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🧪 Phase 0 Spikes",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Smart Intercom Feasibility Tests",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                DeviceInfoCard()
            }

            item {
                Text(
                    "Feasibility Spikes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                SpikeCard(
                    letter = "A",
                    title = "Nearby Connections Range",
                    description = "Discovery time, range, RTT, throughput, HOL blocking, BYTES vs STREAM",
                    color = Color(0xFF4FDBC4),
                    icon = Icons.Filled.CellTower,
                    onClick = { onSpikeSelected("spike_a_nearby") }
                )
            }

            item {
                SpikeCard(
                    letter = "B",
                    title = "Wi-Fi Direct UDP",
                    description = "Raw UDP over Wi-Fi Direct, 2.4 GHz forcing, range comparison",
                    color = Color(0xFF5CB8FF),
                    icon = Icons.Filled.Wifi,
                    onClick = { onSpikeSelected("spike_b_wifidirect") }
                )
            }

            item {
                SpikeCard(
                    letter = "C",
                    title = "Wi-Fi + SCO Coexistence",
                    description = "2.4 GHz vs 5 GHz with Bluetooth SCO, glitch measurement",
                    color = Color(0xFFFFB951),
                    icon = Icons.Filled.SettingsInputAntenna,
                    onClick = { onSpikeSelected("spike_c_coexistence") }
                )
            }

            item {
                SpikeCard(
                    letter = "D",
                    title = "Bluetooth HFP/SCO",
                    description = "SCO setup time, latency, mSBC vs CVSD, A2DP behavior, media button",
                    color = Color(0xFF9B8AFF),
                    icon = Icons.Filled.Headset,
                    onClick = { onSpikeSelected("spike_d_bluetooth") }
                )
            }

            item {
                SpikeCard(
                    letter = "E",
                    title = "ADR-1: Audio Architecture",
                    description = "ADR-1A (unified pipeline + DataChannel) vs ADR-1B (WebRTC native track)",
                    color = Color(0xFFFF7EB3),
                    icon = Icons.Filled.GraphicEq,
                    onClick = { onSpikeSelected("spike_e_adr") }
                )
            }

            item {
                SpikeCard(
                    letter = "F",
                    title = "DataChannel Audio Quality",
                    description = "Opus over DataChannel under simulated loss/jitter/latency",
                    color = Color(0xFFFF6B6B),
                    icon = Icons.Filled.Speed,
                    onClick = { onSpikeSelected("spike_f_datachannel") }
                )
            }

            item {
                SpikeCard(
                    letter = "G",
                    title = "FGS Start Rules",
                    description = "Microphone foreground service start contexts per API/OEM",
                    color = Color(0xFF4ADE80),
                    icon = Icons.Filled.Security,
                    onClick = { onSpikeSelected("spike_g_fgs") }
                )
            }

            item {
                SpikeCard(
                    letter = "H",
                    title = "Self-Managed Telecom",
                    description = "ConnectionService benefits: BT routing, priority, call interop",
                    color = Color(0xFF38BDF8),
                    icon = Icons.Filled.PhoneInTalk,
                    onClick = { onSpikeSelected("spike_h_telecom") }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DeviceInfoCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SpikeCard(
    letter: String,
    title: String,
    description: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Letter badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    letter,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Icon(
                icon,
                contentDescription = null,
                tint = color.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
