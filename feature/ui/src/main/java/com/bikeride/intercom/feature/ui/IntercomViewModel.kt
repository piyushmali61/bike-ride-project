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
import com.bikeride.intercom.engine.audio.SpeechCommandSpotter
import com.bikeride.intercom.engine.audio.SpeechModelState
import com.bikeride.intercom.mesh.ConvoyMesh
import com.bikeride.intercom.mesh.LocationHelper
import com.bikeride.intercom.mesh.RiderProfile
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
    private val voiceCommandDetector: VoiceCommandDetector,
    private val convoyMesh: ConvoyMesh,
    private val meshAnnouncer: MeshAnnouncer,
    private val speechSpotter: SpeechCommandSpotter
) : ViewModel() {

    companion object {
        /** One-tap convoy alerts on the riding screen and in Mesh Chat. */
        val QUICK_ALERTS = listOf("🐢 Slow down", "👋 Hi", "🛑 Stop", "⛽ Pit stop")
    }

    val connectionState: StateFlow<MeshConnectionState> = meshTransport.state
    val connectedPeerName: StateFlow<String?> = meshTransport.connectedPeerName
    val connectedRiders: StateFlow<Map<String, ConnectedRider>> = meshTransport.connectedRiders
    val currentRoom: StateFlow<String> = meshTransport.currentRoom
    val latencyMs: StateFlow<Long> = meshTransport.latencyMs
    val peerIsMuted: StateFlow<Boolean> = meshTransport.peerMuted

    // Audio updates amplitude 50x/s; the UI only needs ~10x/s. Sampling keeps the whole screen
    // from recomposing 100 times a second on slower phones.
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val micAmplitude: StateFlow<Float> = audioEngine.micAmplitude
        .sample(100).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val peerAmplitude: StateFlow<Float> = audioEngine.peerAmplitude
        .sample(100).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)
    val isMuted: StateFlow<Boolean> = audioEngine.isMuted

    val isVoiceControlEnabled: StateFlow<Boolean> = voiceCommandDetector.isVoiceControlEnabled
    val lastVoiceCommand: StateFlow<String?> = voiceCommandDetector.lastDetectedCommand

    val currentAudioRoute: StateFlow<AudioRouteType> = audioRouteManager.currentRoute
    val isBluetoothConnected: StateFlow<Boolean> = audioRouteManager.isBluetoothConnected

    private val prefs = context.getSharedPreferences("astra_ride_prefs", Context.MODE_PRIVATE)

    private val _riderName = MutableStateFlow(prefs.getString("RIDER_NAME", "Rider") ?: "Rider")
    val riderName: StateFlow<String> = _riderName.asStateFlow()

    private val _bikeModel = MutableStateFlow(prefs.getString("BIKE_MODEL", "Yamaha R15 V4") ?: "Yamaha R15 V4")
    val bikeModel: StateFlow<String> = _bikeModel.asStateFlow()

    private val _volumeBoost = MutableStateFlow(1.0f) // 1.0x to 4.0x
    val volumeBoost: StateFlow<Float> = _volumeBoost.asStateFlow()

    private val _isRidingHudOpen = MutableStateFlow(false)
    val isRidingHudOpen: StateFlow<Boolean> = _isRidingHudOpen.asStateFlow()

    private val _isEmergencyAlertActive = MutableStateFlow(false)
    val isEmergencyAlertActive: StateFlow<Boolean> = _isEmergencyAlertActive.asStateFlow()

    private val _customRideCode = MutableStateFlow(prefs.getString("RIDE_CODE", "CONVOY 1") ?: "CONVOY 1")
    val customRideCode: StateFlow<String> = _customRideCode.asStateFlow()

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    // Offline mesh chat (Bluetooth multi-hop + internet relays)
    val riderProfile: StateFlow<RiderProfile> = convoyMesh.profile
    val meshUnread: StateFlow<Int> = convoyMesh.unread
    val meshBluetoothLinks: StateFlow<Int> = convoyMesh.bluetoothLinks
    val meshInternetRelays: StateFlow<Int> = convoyMesh.internetRelays
    val meshPeers = convoyMesh.peers

    // Voice SOS (offline speech) and spoken announcements
    val voiceSosState: StateFlow<SpeechModelState> = speechSpotter.state
    val voiceSosEnabled: StateFlow<Boolean> = speechSpotter.enabled
    val announceEnabled: StateFlow<Boolean> = meshAnnouncer.enabled

    /** Turns Voice SOS on (downloading the model the first time) or off. */
    fun setVoiceSos(on: Boolean) = speechSpotter.setEnabled(on)

    fun setAnnounce(on: Boolean) = meshAnnouncer.setEnabled(on)

    /** Sends a quick alert to the whole convoy and confirms it by voice. */
    fun sendQuickAlert(label: String) {
        convoyMesh.sendChat(label)
        meshAnnouncer.say("Sent: $label")
    }

    /** Shares location with the convoy and announces where the rider is. False if no location yet. */
    fun shareMyLocation(): Boolean {
        val loc = LocationHelper.lastKnown(context) ?: run {
            meshAnnouncer.say("Location not available. Turn on GPS.")
            return false
        }
        convoyMesh.sendLocation(loc.first, loc.second)
        viewModelScope.launch { meshAnnouncer.announceMyLocation(loc.first, loc.second) }
        return true
    }

    init {
        meshAnnouncer.start()
        meshTransport.setRiderName(_riderName.value)
        convoyMesh.setRoom(_customRideCode.value)

        // SOS sent over the mesh (even many hops away, or over the internet) sounds the horn here too
        viewModelScope.launch {
            convoyMesh.incomingSos.collect { triggerLocalHornAlert(fromRemote = true) }
        }

        // Collect remote emergency horn triggers
        viewModelScope.launch {
            meshTransport.emergencyAlert.collect {
                triggerLocalHornAlert(fromRemote = true)
            }
        }
    }

    fun setRiderName(name: String) {
        val sanitized = name.trim().take(20)
        if (sanitized.isNotBlank()) {
            _riderName.value = sanitized
            prefs.edit().putString("RIDER_NAME", sanitized).apply()
            meshTransport.setRiderName(sanitized)
            if (convoyMesh.profile.value.name != sanitized) {
                convoyMesh.updateProfile(convoyMesh.profile.value.copy(name = sanitized))
            }
        }
    }

    /** Saves name, status, avatar and colour; the name is shared with the voice intercom too. */
    fun updateProfile(profile: RiderProfile) {
        convoyMesh.updateProfile(profile)
        setRiderName(profile.name)
    }

    fun setBikeModel(model: String) {
        val sanitized = model.trim().take(30)
        if (sanitized.isNotBlank()) {
            _bikeModel.value = sanitized
            prefs.edit().putString("BIKE_MODEL", sanitized).apply()
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        _hasPermissions.value = granted
        // Start the offline mesh even if some permissions were refused; it uses whatever is allowed.
        convoyMesh.start()
    }

    fun setCustomRideCode(code: String) {
        val sanitized = code.trim().uppercase()
        if (sanitized.isNotBlank()) {
            _customRideCode.value = sanitized
            prefs.edit().putString("RIDE_CODE", sanitized).apply()
            convoyMesh.setRoom(sanitized)
            if (connectionState.value == MeshConnectionState.CONNECTED ||
                connectionState.value == MeshConnectionState.SEARCHING) {
                // Seamlessly switch room
                startRideSession(sanitized)
            }
        }
    }

    /** Deletes all stored chat, photos, and mesh data for the specified convoy. */
    fun deleteConvoy(roomName: String = _customRideCode.value) {
        val sanitized = roomName.trim().uppercase()
        convoyMesh.deleteConvoyData(sanitized)
        meshAnnouncer.say("Convoy data deleted.")
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
        // Also carry the SOS over the offline mesh / internet, with location when available
        val loc = LocationHelper.lastKnown(context)
        convoyMesh.sendSos(loc?.first, loc?.second)
    }

    private fun triggerLocalHornAlert(fromRemote: Boolean) {
        // The same SOS can arrive over Nearby, Hotspot and the mesh at once: sound it once.
        if (fromRemote && _isEmergencyAlertActive.value) return
        audioEngine.playEmergencyHorn(viewModelScope)
        viewModelScope.launch {
            _isEmergencyAlertActive.value = true
            delay(2200)
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
