package com.bikeride.intercom.feature.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bikeride.intercom.bluetooth.AudioRouteManager
import com.bikeride.intercom.bluetooth.AudioRouteType
import com.bikeride.intercom.engine.audio.AudioEngine
import com.bikeride.intercom.service.IntercomService
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
    private val audioRouteManager: AudioRouteManager
) : ViewModel() {

    val connectionState: StateFlow<MeshConnectionState> = meshTransport.state
    val connectedPeerName: StateFlow<String?> = meshTransport.connectedPeerName
    val latencyMs: StateFlow<Long> = meshTransport.latencyMs
    val peerIsMuted: StateFlow<Boolean> = meshTransport.peerMuted

    val micAmplitude: StateFlow<Float> = audioEngine.micAmplitude
    val peerAmplitude: StateFlow<Float> = audioEngine.peerAmplitude
    val isMuted: StateFlow<Boolean> = audioEngine.isMuted

    val currentAudioRoute: StateFlow<AudioRouteType> = audioRouteManager.currentRoute
    val isBluetoothConnected: StateFlow<Boolean> = audioRouteManager.isBluetoothConnected

    private val _volumeBoost = MutableStateFlow(1.0f) // 1.0x to 4.0x
    val volumeBoost: StateFlow<Float> = _volumeBoost.asStateFlow()

    private val _isRidingHudOpen = MutableStateFlow(false)
    val isRidingHudOpen: StateFlow<Boolean> = _isRidingHudOpen.asStateFlow()

    private val _isEmergencyAlertActive = MutableStateFlow(false)
    val isEmergencyAlertActive: StateFlow<Boolean> = _isEmergencyAlertActive.asStateFlow()

    private val _customRideCode = MutableStateFlow("ASTRA")
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
        _customRideCode.value = code.uppercase().filter { it.isLetterOrDigit() }.take(6)
    }

    fun onOneClickConnectToggle() {
        when (connectionState.value) {
            MeshConnectionState.IDLE, MeshConnectionState.DISCONNECTED -> {
                Timber.i("1-Click Connect triggered with code ${_customRideCode.value}")
                val intent = Intent(context, IntercomService::class.java).apply {
                    action = IntercomService.ACTION_START
                    putExtra("RIDE_CODE", _customRideCode.value)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
            MeshConnectionState.SEARCHING, MeshConnectionState.CONNECTING, MeshConnectionState.CONNECTED -> {
                Timber.i("Disconnect triggered by user")
                val intent = Intent(context, IntercomService::class.java).apply {
                    action = IntercomService.ACTION_STOP
                }
                context.startService(intent)
                _isRidingHudOpen.value = false
            }
        }
    }

    fun toggleMute() {
        val next = !isMuted.value
        audioEngine.setMuted(next)
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
