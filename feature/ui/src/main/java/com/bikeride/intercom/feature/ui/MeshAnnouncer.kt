package com.bikeride.intercom.feature.ui

import android.content.Context
import com.bikeride.intercom.engine.audio.VoiceAnnouncer
import com.bikeride.intercom.mesh.ChatMessage
import com.bikeride.intercom.mesh.ConvoyMesh
import com.bikeride.intercom.mesh.LocationHelper
import com.bikeride.intercom.mesh.MeshType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads convoy messages aloud so riders keep their eyes on the road:
 * "Piyush: Slow down", "Piyush shared location, 1.2 kilometres north-east of you".
 * Lives for the whole app process, independent of which screen is open.
 */
@Singleton
class MeshAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mesh: ConvoyMesh,
    private val voice: VoiceAnnouncer
) {
    private val prefs = context.getSharedPreferences("astra_ride_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            mesh.incoming.collect { if (_enabled.value) announce(it) }
        }
    }

    /** Speaks where the rider is, for the Location button. */
    suspend fun announceMyLocation(lat: Double, lon: Double) {
        val place = LocationHelper.placeName(context, lat, lon)
        voice.say(if (place != null) "Location shared. You are near $place." else "Location shared with your convoy.")
    }

    fun say(text: String, urgent: Boolean = false) = voice.say(text, urgent)

    private suspend fun announce(m: ChatMessage) {
        val name = m.senderName
        when (m.type) {
            // Wording avoids "SOS/help/emergency" so Voice SOS never hears the phone and re-triggers
            MeshType.SOS -> voice.say("Alarm! $name needs assistance. ${whereIs(m)}", urgent = true)
            MeshType.LOCATION -> voice.say("$name shared location. ${whereIs(m)}")
            MeshType.IMAGE -> voice.say("$name sent a photo.")
            MeshType.CHAT -> voice.say("$name: ${m.text}")
            else -> Unit
        }
    }

    private suspend fun whereIs(m: ChatMessage): String {
        val lat = m.latitude ?: return ""
        val lon = m.longitude ?: return ""
        val me = LocationHelper.lastKnown(context)
        if (me != null) {
            val meters = LocationHelper.distanceMeters(me.first, me.second, lat, lon)
            val dir = LocationHelper.direction(me.first, me.second, lat, lon)
            return "${LocationHelper.spokenDistance(meters)} $dir of you."
        }
        return LocationHelper.placeName(context, lat, lon)?.let { "Near $it." } ?: ""
    }

    companion object {
        private const val KEY_ENABLED = "ANNOUNCE_MESSAGES"
    }
}
