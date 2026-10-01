package com.bikeride.intercom.bluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio routing across Bluetooth headsets/helmets, phone loudspeaker, and earpiece.
 * Implements Android 12+ (API 31-35) [AudioManager.setCommunicationDevice] architecture,
 * with automatic fallback to SCO on legacy Android releases.
 */
@Singleton
class AudioRouteManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _currentRoute = MutableStateFlow(AudioRouteType.LOUDSPEAKER)
    val currentRoute: StateFlow<AudioRouteType> = _currentRoute.asStateFlow()

    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED == intent?.action) {
                val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                Timber.d("SCO Audio state changed: $state")
                updateBluetoothState()
            }
        }
    }

    init {
        try {
            val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            context.registerReceiver(scoReceiver, filter)
        } catch (e: Exception) {
            Timber.w(e, "Failed to register SCO receiver")
        }
        updateBluetoothState()
        // Default to Bluetooth if already connected, else Loudspeaker. Applied when a ride starts
        // (beginRideAudio), not at app launch, so the app does not hold the phone in call mode.
        _currentRoute.value = if (_isBluetoothConnected.value) AudioRouteType.HELMET_BLUETOOTH else AudioRouteType.LOUDSPEAKER
    }

    private var focusRequest: android.media.AudioFocusRequest? = null

    /**
     * Starts a call-style audio session for a ride: takes audio focus, switches the phone into
     * communication mode, re-applies the chosen output (speaker / earpiece / helmet) and makes sure
     * the call volume is not at zero. Without this, phones (especially Samsung) can play the
     * incoming voice to no output at all.
     */
    fun beginRideAudio() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = req
                val result = audioManager.requestAudioFocus(req)
                Timber.i("Ride audio focus: ${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "granted" else "denied ($result)"}")
            }
            // Call volume at 0 makes the intercom silent; raise it to a usable level
            val stream = AudioManager.STREAM_VOICE_CALL
            val max = audioManager.getStreamMaxVolume(stream)
            if (audioManager.getStreamVolume(stream) < max / 2) {
                audioManager.setStreamVolume(stream, (max * 0.8).toInt().coerceAtLeast(1), 0)
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not start ride audio session")
        }
        updateBluetoothState()
        setRoute(_currentRoute.value)
    }

    /** Ends the ride audio session so the phone's normal sound (music, calls) is restored. */
    fun endRideAudio() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            }
            focusRequest = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                run {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Timber.w(e, "Could not end ride audio session")
        }
    }

    fun updateBluetoothState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val hasBt = devices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
            _isBluetoothConnected.value = hasBt
        } else {
            @Suppress("DEPRECATION")
            _isBluetoothConnected.value = audioManager.isBluetoothScoAvailableOffCall || audioManager.isBluetoothA2dpOn
        }
    }

    fun cycleRoute() {
        updateBluetoothState()
        val next = when (_currentRoute.value) {
            AudioRouteType.LOUDSPEAKER -> {
                if (_isBluetoothConnected.value) AudioRouteType.HELMET_BLUETOOTH else AudioRouteType.EARPIECE
            }
            AudioRouteType.HELMET_BLUETOOTH -> AudioRouteType.LOUDSPEAKER
            AudioRouteType.EARPIECE -> {
                if (_isBluetoothConnected.value) AudioRouteType.HELMET_BLUETOOTH else AudioRouteType.LOUDSPEAKER
            }
        }
        setRoute(next)
    }

    fun setRoute(route: AudioRouteType) {
        _currentRoute.value = route
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (route) {
                    AudioRouteType.HELMET_BLUETOOTH -> {
                        val btDevice = audioManager.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                        }
                        if (btDevice != null) {
                            audioManager.setCommunicationDevice(btDevice)
                            Timber.i("Routed audio to Bluetooth device: ${btDevice.productName}")
                        } else {
                            // Fallback to speaker if BT not ready
                            audioManager.clearCommunicationDevice()
                            audioManager.isSpeakerphoneOn = true
                        }
                    }
                    AudioRouteType.LOUDSPEAKER -> {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                        val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        }
                        if (speakerDevice != null) {
                            audioManager.setCommunicationDevice(speakerDevice)
                        } else {
                            audioManager.clearCommunicationDevice()
                            audioManager.isSpeakerphoneOn = true
                        }
                    }
                    AudioRouteType.EARPIECE -> {
                        val earpieceDevice = audioManager.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                        }
                        if (earpieceDevice != null) {
                            audioManager.setCommunicationDevice(earpieceDevice)
                        } else {
                            audioManager.clearCommunicationDevice()
                            audioManager.isSpeakerphoneOn = false
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                when (route) {
                    AudioRouteType.HELMET_BLUETOOTH -> {
                        audioManager.isSpeakerphoneOn = false
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                    }
                    AudioRouteType.LOUDSPEAKER -> {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                        audioManager.isSpeakerphoneOn = true
                    }
                    AudioRouteType.EARPIECE -> {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                        audioManager.isSpeakerphoneOn = false
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply audio route: $route")
        }
    }
}
