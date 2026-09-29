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
import com.bikeride.intercom.transport.local.nearby.NearbyMeshTransport
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground Service guaranteeing uninterrupted full-duplex intercom
 * across Samsung OneUI (M35, S25 FE) and all Android OEM battery restrictions.
 * Holds partial wake-lock and registers microphone & connected device FGS types.
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
    }

    @Inject lateinit var audioEngine: AudioEngine
    @Inject lateinit var meshTransport: NearbyMeshTransport
    @Inject lateinit var audioRouteManager: AudioRouteManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): IntercomService = this@IntercomService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
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

        // Update notification based on mesh state
        serviceScope.launch {
            meshTransport.state.collect { state ->
                val peer = meshTransport.connectedPeerName.value ?: "Rider"
                val text = when (state) {
                    com.bikeride.intercom.transport.local.nearby.MeshConnectionState.CONNECTED -> "🟢 Connected to $peer"
                    com.bikeride.intercom.transport.local.nearby.MeshConnectionState.SEARCHING -> "🔍 Searching for nearby rider..."
                    com.bikeride.intercom.transport.local.nearby.MeshConnectionState.CONNECTING -> "⚡ Linking with $peer..."
                    else -> "Ready to ride"
                }
                updateNotification(text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRide()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                val newMuted = !audioEngine.isMuted.value
                audioEngine.setMuted(newMuted)
                meshTransport.sendMuteState(newMuted)
            }
            ACTION_START, null -> {
                startForegroundWithNotification()
                val rideCode = intent?.getStringExtra("RIDE_CODE")
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
    }

    private fun stopRide() {
        isRunning = false
        audioEngine.stop()
        meshTransport.disconnect()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Starting ride mesh...")
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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(statusText: String): Notification {
        val stopIntent = Intent(this, IntercomService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val muteIntent = Intent(this, IntercomService::class.java).apply { action = ACTION_TOGGLE_MUTE }
        val mutePending = PendingIntent.getService(this, 2, muteIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🏍️ AstraRide Intercom")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Ride", stopPending)
            .addAction(android.R.drawable.ic_lock_silent_mode, "Mute", mutePending)
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
        stopRide()
        serviceScope.cancel()
        super.onDestroy()
    }
}
