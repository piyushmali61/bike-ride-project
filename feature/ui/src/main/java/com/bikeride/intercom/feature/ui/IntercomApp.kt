package com.bikeride.intercom.feature.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bikeride.intercom.feature.ui.screens.HomeScreen

/**
 * Root composable for the AstraRide Smart Intercom app.
 * Hosts the unified 1-Click Cockpit where all intercom features work seamlessly.
 */
@Composable
fun IntercomApp() {
    HomeScreen(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    )
}
