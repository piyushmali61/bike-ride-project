package com.bikeride.intercom.feature.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.bikeride.intercom.feature.ui.screens.ChatScreen
import com.bikeride.intercom.feature.ui.screens.HomeScreen

/**
 * Root composable for the AstraRide Smart Intercom app.
 * Hosts the 1-Click Cockpit and the offline Convoy Mesh Chat.
 */
@Composable
fun IntercomApp() {
    var showChat by rememberSaveable { mutableStateOf(false) }

    if (showChat) {
        ChatScreen(onBack = { showChat = false })
    } else {
        HomeScreen(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            onOpenChat = { showChat = true }
        )
    }
}
