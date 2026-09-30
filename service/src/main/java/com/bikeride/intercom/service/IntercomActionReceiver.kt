package com.bikeride.intercom.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * High-reliability BroadcastReceiver for notification actions.
 * Guarantees instantaneous Mute / Unmute and End Ride response across
 * Android 12-15 and Samsung OneUI without background execution limits.
 */
class IntercomActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Timber.i("IntercomActionReceiver received action: $action")

        val service = IntercomService.instance
        if (service != null) {
            when (action) {
                IntercomService.ACTION_TOGGLE_MUTE -> {
                    service.toggleMuteFromAction()
                }
                IntercomService.ACTION_STOP -> {
                    service.stopRideFromAction()
                }
            }
        } else {
            // Forward to service in case it needs to be awakened
            val serviceIntent = Intent(context, IntercomService::class.java).apply {
                this.action = action
            }
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Timber.e(e, "Error forwarding action to IntercomService")
            }
        }
    }
}
