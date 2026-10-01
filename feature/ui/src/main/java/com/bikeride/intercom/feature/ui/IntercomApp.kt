package com.bikeride.intercom.feature.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.bikeride.intercom.feature.ui.components.PictureStopDialog
import com.bikeride.intercom.feature.ui.screens.ChatScreen
import com.bikeride.intercom.feature.ui.screens.HomeScreen
import com.bikeride.intercom.feature.ui.screens.RideMapScreen

/**
 * Root composable for the AstraRide Smart Intercom app.
 * Hosts the 1-Click Cockpit, offline Convoy Mesh Chat, Live HUD Navigation Map, and Picture Stop.
 */
@Composable
fun IntercomApp(
    viewModel: IntercomViewModel = hiltViewModel()
) {
    var currentScreen by rememberSaveable { mutableStateOf("home") } // "home", "chat", "map"
    var showPictureStop by rememberSaveable { mutableStateOf(false) }

    val riderName by viewModel.riderName.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val micAmplitude by viewModel.micAmplitude.collectAsState()
    val peerAmplitude by viewModel.peerAmplitude.collectAsState()

    when (currentScreen) {
        "chat" -> {
            ChatScreen(
                onBack = { currentScreen = "home" }
            )
        }
        "map" -> {
            RideMapScreen(
                navManager = viewModel.navManager,
                geocoder = viewModel.geocoder,
                isMuted = isMuted,
                onToggleMute = { viewModel.toggleMute() },
                onShareLocation = { viewModel.shareMyLocation() },
                onTriggerSos = { viewModel.triggerEmergencyHorn() },
                onPictureStop = { showPictureStop = true },
                onBack = { currentScreen = "home" },
                isActive = connectionState != com.bikeride.intercom.transport.local.nearby.MeshConnectionState.IDLE &&
                    connectionState != com.bikeride.intercom.transport.local.nearby.MeshConnectionState.DISCONNECTED,
                amplitude = if (micAmplitude > 0.05f) micAmplitude else peerAmplitude
            )
        }
        else -> {
            HomeScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                viewModel = viewModel,
                onOpenChat = { currentScreen = "chat" },
                onOpenMap = { currentScreen = "map" },
                onPictureStop = { showPictureStop = true }
            )
        }
    }

    if (showPictureStop) {
        PictureStopDialog(
            riderName = riderName,
            onDismiss = { showPictureStop = false },
            onSendPhoto = { jpeg, locName, lat, lon ->
                viewModel.sendPictureStop(jpeg, locName, lat, lon)
                showPictureStop = false
            }
        )
    }
}
