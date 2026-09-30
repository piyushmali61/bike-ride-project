package com.bikeride.intercom.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.bikeride.intercom.bluetooth.AudioRouteManager
import com.bikeride.intercom.engine.audio.AudioEngine
import com.bikeride.intercom.engine.audio.VoiceCommand
import com.bikeride.intercom.engine.audio.VoiceCommandDetector
import com.bikeride.intercom.transport.local.nearby.MeshConnectionState
import com.bikeride.intercom.transport.local.nearby.NearbyMeshTransport
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground Service guaranteeing uninterrupted full-duplex intercom
 * across Samsung OneUI (M35, S25 FE) and all Android OEM battery restrictions.
 * Holds partial wake-lock, registers microphone & connected device FGS types,
 * and manages hands-free voice command recognition ("Say MUTE to Mute").
 */
@AndroidEntryPoint
class IntercomService : Service() {

    companion object {
        const val CHANNEL_ID = "astra_ride_intercom_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.bikeride.intercom.START"
        const val ACTION_STOP = "com.bikeride.intercom.STOP"
        const val ACTION_TOGGLE_MUTE = "com.bikeride.intercom.TOGGLE_MUTE"

        var isRunning = false
            private set

        var instance: IntercomService? = null
            private set
    }

    @Inject lateinit var audioEngine: AudioEngine
    @Inject lateinit var meshTransport: NearbyMeshTransport
    @Inject lateinit var audioRouteManager: AudioRouteManager
    @Inject lateinit var voiceCommandDetector: VoiceCommandDetector

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): IntercomService = this@IntercomService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        wireAudioAndTransport()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AstraRide::IntercomWakeLock").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hour ride maximum
            }
            Timber.i("WakeLock acquired for motorcycle ride")
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire wake lock")
        }
    }

    private fun wireAudioAndTransport() {
        // Feed incoming mesh audio to playback engine
        meshTransport.onAudioFrameReceived = { frame ->
            audioEngine.playIncomingFrame(frame)
        }

        // Pipe captured microphone audio to mesh transport
        serviceScope.launch(Dispatchers.IO) {
            audioEngine.outgoingFrames.collect { frame ->
                meshTransport.sendAudioFrame(frame)
            }
        }

        // Handle remote emergency horn alert
        serviceScope.launch {
            meshTransport.emergencyAlert.collect {
                audioEngine.playEmergencyHorn(serviceScope)
            }
        }

        // Hands-Free Voice Commands: "Say MUTE to Mute"
        voiceCommandDetector.onCommandRecognized = { command ->
            when (command) {
                VoiceCommand.MUTE -> {
                    Timber.i("Hands-Free Voice MUTE triggered")
                    audioEngine.setMuted(true, serviceScope)
                    meshTransport.sendMuteState(true)
                    updateNotification("🔇 MIC MUTED (Voice) · Ride Active")
                }
                VoiceCommand.UNMUTE -> {
                    Timber.i("Hands-Free Voice UNMUTE triggered")
                    audioEngine.setMuted(false, serviceScope)
                    meshTransport.sendMuteState(false)
                    updateNotification("🟢 MIC LIVE (Voice) · Ride Active")
                }
                VoiceCommand.HORN -> {
                    Timber.i("Hands-Free Voice HORN triggered")
                    audioEngine.playEmergencyHorn(serviceScope)
                    meshTransport.sendEmergencyHornAlert()
                }
            }
        }

        // Update notification based on mesh state & connected room riders
        serviceScope.launch {
            meshTransport.connectedRiders.collect { riders ->
                val room = meshTransport.currentRoom.value
                val count = riders.size
                val statusText = if (count > 0) {
                    "🟢 $count Biker${if (count > 1) "s" else ""} in Room [$room]"
                } else if (meshTransport.state.value == MeshConnectionState.SEARCHING) {
                    "🔍 Seeking Room [$room] riders..."
                } else {
                    "Room [$room] Ready"
                }
                updateNotification(statusText)
            }
        }

        // Refresh notification whenever mute state changes
        serviceScope.launch {
            audioEngine.isMuted.collect { muted ->
                val ridersCount = meshTransport.connectedRiders.value.size
                val room = meshTransport.currentRoom.value
                val status = if (muted) {
                    "🔇 MIC MUTED · $ridersCount in Room [$room]"
                } else {
                    "🟢 MIC LIVE · $ridersCount in Room [$room]"
                }
                updateNotification(status)
            }
        }
    }

    fun toggleMuteFromAction() {
        val next = !audioEngine.isMuted.value
        audioEngine.setMuted(next, serviceScope)
        meshTransport.sendMuteState(next)
        val room = meshTransport.currentRoom.value
        val ridersCount = meshTransport.connectedRiders.value.size
        val status = if (next) "🔇 MIC MUTED · $ridersCount in Room [$room]" else "🟢 MIC LIVE · $ridersCount in Room [$room]"
        updateNotification(status)
    }

    fun stopRideFromAction() {
        stopRide()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRideFromAction()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                toggleMuteFromAction()
            }
            ACTION_START, null -> {
                val rideCode = intent?.getStringExtra("RIDE_CODE") ?: "CONVOY 1"
                startForegroundWithNotification(rideCode)
                startRide(rideCode)
            }
        }
        return START_STICKY
    }

    private fun startRide(rideCode: String?) {
        isRunning = true
        acquireWakeLock()
        audioEngine.start(serviceScope)
        meshTransport.startOneClickMesh(serviceScope, rideCode)
        voiceCommandDetector.startListening()
    }

    private fun stopRide() {
        isRunning = false
        voiceCommandDetector.stopListening()
        audioEngine.stop()
        meshTransport.disconnect()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundWithNotification(roomCode: String) {
        val notification = buildNotification("Starting room mesh [$roomCode]...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true
    }

    private fun updateNotification(statusText: String) {
        if (!isRunning) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(statusText: String): Notification {
        // Broadcast PendingIntents for 100% reliable click response
        val stopIntent = Intent(this, IntercomActionReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getBroadcast(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, IntercomActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePending = PendingIntent.getBroadcast(
            this,
            2,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isCurrentlyMuted = audioEngine.isMuted.value
        val muteActionTitle = if (isCurrentlyMuted) "🎙️ Unmute" else "🔇 Mute"
        val muteActionIcon = if (isCurrentlyMuted) {
            android.R.drawable.ic_btn_speak_now
        } else {
            android.R.drawable.ic_lock_silent_mode
        }

        // Tap notification to open AstraRide app
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPending = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🏍️ AstraRide Intercom · Room Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Ride", stopPending)
            .addAction(muteActionIcon, muteActionTitle, mutePending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AstraRide Active Intercom",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full-duplex motorcycle intercom audio session"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        instance = null
        stopRide()
        serviceScope.cancel()
        super.onDestroy()
    }
}
