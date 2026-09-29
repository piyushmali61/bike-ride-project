package com.bikeride.intercom.spikes.fgs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import timber.log.Timber

/**
 * Test Foreground Service for Spike G.
 * Validates FOREGROUND_SERVICE_MICROPHONE + FOREGROUND_SERVICE_CONNECTED_DEVICE
 * start rules across Android 14/15 and aggressive OEM background policies.
 */
class SpikeFgsService : Service() {

    companion object {
        const val CHANNEL_ID = "spike_fgs_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.bikeride.intercom.spikes.fgs.START"
        const val ACTION_STOP = "com.bikeride.intercom.spikes.fgs.STOP"
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundWithTypes()
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithTypes() {
        val notification = buildNotification("Microphone FGS Active (Spike G)")

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
        Timber.i("SpikeFgsService started in foreground")
    }

    private fun stopForegroundService() {
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.i("SpikeFgsService stopped")
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Intercom Spike FGS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Phase 0 Foreground Service feasibility test"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Intercom Spike")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
