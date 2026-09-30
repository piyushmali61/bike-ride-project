package com.bikeride.intercom.feature.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.bikeride.intercom.mesh.ChatMessage
import com.bikeride.intercom.mesh.ConvoyMesh
import com.bikeride.intercom.mesh.MeshPeer
import com.bikeride.intercom.mesh.RiderProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mesh: ConvoyMesh
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = mesh.messages
    val peers: StateFlow<Map<Long, MeshPeer>> = mesh.peers
    val room: StateFlow<String> = mesh.room
    val profile: StateFlow<RiderProfile> = mesh.profile
    val bluetoothLinks: StateFlow<Int> = mesh.bluetoothLinks
    val bluetoothRunning: StateFlow<Boolean> = mesh.bluetoothRunning
    val internetRelays: StateFlow<Int> = mesh.internetRelays

    fun onVisible(visible: Boolean) = mesh.setChatVisible(visible)

    fun send(text: String) = mesh.sendChat(text)

    /** Returns false when no location is known yet. */
    fun shareLocation(): Boolean {
        val loc = LocationHelper.lastKnown(context) ?: return false
        mesh.sendLocation(loc.first, loc.second)
        return true
    }

    fun sendSos() {
        val loc = LocationHelper.lastKnown(context)
        mesh.sendSos(loc?.first, loc?.second)
    }

    fun retryBluetooth() = mesh.start()
}
