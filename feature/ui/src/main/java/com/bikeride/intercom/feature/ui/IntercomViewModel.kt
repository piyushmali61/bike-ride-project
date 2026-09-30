package com.bikeride.intercom.feature.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bikeride.intercom.bluetooth.AudioRouteManager
import com.bikeride.intercom.bluetooth.AudioRouteType
import com.bikeride.intercom.engine.audio.AudioEngine
import com.bikeride.intercom.engine.audio.VoiceCommandDetector
import com.bikeride.intercom.service.IntercomService
import com.bikeride.intercom.transport.local.nearby.ConnectedRider
import com.bikeride.intercom.transport.local.nearby.MeshConnectionState
import com.bikeride.intercom.transport.local.nearby.NearbyMeshTransport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class IntercomViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioEngine: AudioEngine,
    private val meshTransport: NearbyMeshTransport,
    private val audioRouteManager: AudioRouteManager,
    private val voiceCommandDetector: VoiceCommandDetector
) : ViewModel() {

    val connectionState: StateFlow<MeshConnectionState> = meshTransport.state
    val connectedPeerName: StateFlow<String?> = meshTransport.connectedPeerName
    val connectedRiders: StateFlow<Map<String, ConnectedRider>> = meshTransport.connectedRiders
    val currentRoom: StateFlow<String> = meshTransport.currentRoom
    val latencyMs: StateFlow<Long> = meshTransport.latencyMs
    val peerIsMuted: StateFlow<Boolean> = meshTransport.peerMuted

    val micAmplitude: StateFlow<Float> = audioEngine.micAmplitude
    val peerAmplitude: StateFlow<Float> = audioEngine.peerAmplitude
    val isMuted: StateFlow<Boolean> = audioEngine.isMuted

    val isVoiceControlEnabled: StateFlow<Boolean> = voiceCommandDetector.isVoiceControlEnabled
    val lastVoiceCommand: StateFlow<String?> = voiceCommandDetector.lastDetectedCommand

    val currentAudioRoute: StateFlow<AudioRouteType> = audioRouteManager.currentRoute
    val isBluetoothConnected: StateFlow<Boolean> = audioRouteManager.isBluetoothConnected

    private val _volumeBoost = MutableStateFlow(1.0f) // 1.0x to 4.0x
    val volumeBoost: StateFlow<Float> = _volumeBoost.asStateFlow()

    private val _isRidingHudOpen = MutableStateFlow(false)
    val isRidingHudOpen: StateFlow<Boolean> = _isRidingHudOpen.asStateFlow()

    private val _isEmergencyAlertActive = MutableStateFlow(false)
    val isEmergencyAlertActive: StateFlow<Boolean> = _isEmergencyAlertActive.asStateFlow()

    private val _customRideCode = MutableStateFlow("CONVOY 1")
    val customRideCode: StateFlow<String> = _customRideCode.asStateFlow()

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    init {
        // Collect remote emergency horn triggers
        viewModelScope.launch {
            meshTransport.emergencyAlert.collect {
                triggerLocalHornAlert(fromRemote = true)
            }
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        _hasPermissions.value = granted
    }

    fun setCustomRideCode(code: String) {
        val sanitized = code.trim().uppercase()
        if (sanitized.isNotBlank()) {
            _customRideCode.value = sanitized
            if (connectionState.value == MeshConnectionState.CONNECTED ||
                connectionState.value == MeshConnectionState.SEARCHING) {
                // Seamlessly switch room
                startRideSession(sanitized)
            }
        }
    }

    fun toggleVoiceControl(enabled: Boolean) {
        voiceCommandDetector.setVoiceControlEnabled(enabled)
    }

    fun onOneClickConnectToggle() {
        when (connectionState.value) {
            MeshConnectionState.IDLE, MeshConnectionState.DISCONNECTED -> {
                startRideSession(_customRideCode.value)
            }
            MeshConnectionState.SEARCHING, MeshConnectionState.CONNECTING, MeshConnectionState.CONNECTED -> {
                endRideSession()
            }
        }
    }

    fun startRideSession(roomName: String = _customRideCode.value) {
        Timber.i("Starting Ride Session for Room: $roomName")
        try {
            val intent = Intent(context, IntercomService::class.java).apply {
                action = IntercomService.ACTION_START
                putExtra("RIDE_CODE", roomName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting IntercomService")
        }
    }

    fun endRideSession() {
        Timber.i("Ending Ride Session")
        try {
            val service = IntercomService.instance
            if (service != null) {
                service.stopRideFromAction()
            } else {
                val intent = Intent(context, IntercomService::class.java).apply {
                    action = IntercomService.ACTION_STOP
                }
                context.startService(intent)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping IntercomService")
        }
        _isRidingHudOpen.value = false
    }

    fun toggleMute() {
        val next = !isMuted.value
        audioEngine.setMuted(next, viewModelScope)
        meshTransport.sendMuteState(next)
    }

    fun cycleAudioRoute() {
        audioRouteManager.cycleRoute()
    }

    fun setAudioRoute(route: AudioRouteType) {
        audioRouteManager.setRoute(route)
    }

    fun triggerEmergencyHorn() {
        triggerLocalHornAlert(fromRemote = false)
        meshTransport.sendEmergencyHornAlert()
    }

    private fun triggerLocalHornAlert(fromRemote: Boolean) {
        audioEngine.playEmergencyHorn(viewModelScope)
        viewModelScope.launch {
            _isEmergencyAlertActive.value = true
            delay(1500)
            _isEmergencyAlertActive.value = false
        }
    }

    fun setVolumeBoost(multiplier: Float) {
        _volumeBoost.value = multiplier
        audioEngine.setVolumeBoost(multiplier)
    }

    fun toggleRidingHud(open: Boolean) {
        _isRidingHudOpen.value = open
    }
}
