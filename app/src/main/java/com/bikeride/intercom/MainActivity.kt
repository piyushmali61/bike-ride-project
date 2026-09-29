package com.bikeride.intercom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.bikeride.intercom.feature.ui.IntercomApp
import com.bikeride.intercom.feature.ui.theme.SmartIntercomTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the Smart Intercom Compose UI.
 * All screens are Compose destinations within [IntercomApp].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartIntercomTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IntercomApp()
                }
            }
        }
    }
}
