package com.bikeride.intercom.feature.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bikeride.intercom.mesh.ChatMessage
import com.bikeride.intercom.mesh.ConvoyMesh
import com.bikeride.intercom.mesh.LocationHelper
import com.bikeride.intercom.mesh.MeshPeer
import com.bikeride.intercom.mesh.RiderProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mesh: ConvoyMesh,
    private val announcer: MeshAnnouncer
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = mesh.messages
    val peers: StateFlow<Map<Long, MeshPeer>> = mesh.peers
    val room: StateFlow<String> = mesh.room
    val profile: StateFlow<RiderProfile> = mesh.profile
    val bluetoothLinks: StateFlow<Int> = mesh.bluetoothLinks
    val bluetoothRunning: StateFlow<Boolean> = mesh.bluetoothRunning
    val internetRelays: StateFlow<Int> = mesh.internetRelays
    val announceEnabled: StateFlow<Boolean> = announcer.enabled

    fun onVisible(visible: Boolean) = mesh.setChatVisible(visible)

    fun send(text: String) = mesh.sendChat(text)

    fun sendQuickAlert(label: String) {
        mesh.sendChat(label)
        announcer.say("Sent: $label")
    }

    fun setAnnounce(on: Boolean) = announcer.setEnabled(on)

    /** Returns false when no location is known yet. */
    fun shareLocation(): Boolean {
        val loc = LocationHelper.lastKnown(context) ?: run {
            announcer.say("Location not available. Turn on GPS.")
            return false
        }
        mesh.sendLocation(loc.first, loc.second)
        viewModelScope.launch { announcer.announceMyLocation(loc.first, loc.second) }
        return true
    }

    fun sendSos() {
        val loc = LocationHelper.lastKnown(context)
        mesh.sendSos(loc?.first, loc?.second)
        announcer.say("Alert sent to your convoy.", urgent = true)
    }

    fun retryBluetooth() = mesh.start()

    /** Deletes all stored chat and photos for the current room. Returns true on success. */
    suspend fun deleteConvoyData(): Boolean {
        val ok = mesh.deleteConvoyData()
        if (ok) announcer.say("Convoy data deleted.")
        return ok
    }

    /** Deletes a single message by unique key from storage and active state. */
    suspend fun deleteMessage(key: String): Boolean {
        return mesh.deleteMessage(key)
    }

    fun sendPictureStop(jpeg: ByteArray, locationName: String?, lat: Double?, lon: Double?) {
        mesh.sendPhoto(jpeg, locationName = locationName, lat = lat, lon = lon, isPictureStop = true)
        announcer.say("Picture stop shared.")
    }

    /** Compresses the picked photo to a few KB and sends it. Calls [onResult] with false on failure. */
    fun sendPhoto(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.Default) { compress(uri) }
            val ok = jpeg != null && mesh.sendImage(jpeg)
            if (ok) announcer.say("Photo sent.")
            onResult(ok)
        }
    }

    private fun compress(uri: Uri): ByteArray? {
        try {
            for (maxSide in intArrayOf(360, 280, 200)) {
                // ImageDecoder applies EXIF rotation, so photos arrive the right way up
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = maxSide.toFloat() / maxOf(w, h)
                    if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                for (quality in intArrayOf(60, 45, 30)) {
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    if (out.size() <= com.bikeride.intercom.mesh.MAX_PHOTO_BYTES) {
                        bitmap.recycle()
                        return out.toByteArray()
                    }
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not read photo")
        }
        return null
    }
}
