package com.bikeride.intercom.feature.ui.nav

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import com.bikeride.intercom.mesh.LocationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DestinationGeocoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): List<RideDestination> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        // 1. Try Nominatim (online OpenStreetMap geocoder)
        val onlineResults = searchNominatim(trimmed)
        if (onlineResults.isNotEmpty()) return@withContext onlineResults

        // 2. Fall back to Android system Geocoder
        searchSystemGeocoder(trimmed)
    }

    private fun searchNominatim(query: String): List<RideDestination> = try {
        val url = "https://nominatim.openstreetmap.org/search?format=json&q=${Uri.encode(query)}&limit=5&addressdetails=1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AstraRide/1.3 (Smart Motorcycle Intercom; Android)")
            .build()

        val response = client.newCall(request).execute()
        response.use { res ->
            if (!res.isSuccessful) return emptyList()
            val body = res.body?.string() ?: return emptyList()
            val arr = JSONArray(body)
            val list = mutableListOf<RideDestination>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val displayName = obj.optString("display_name", "")
                val lat = obj.optDouble("lat", Double.NaN)
                val lon = obj.optDouble("lon", Double.NaN)
                if (!lat.isNaN() && !lon.isNaN() && displayName.isNotBlank()) {
                    // Extract short title and area
                    val shortName = displayName.split(",").take(3).joinToString(", ").trim()
                    list.add(RideDestination(name = shortName, latitude = lat, longitude = lon))
                }
            }
            list
        }
    } catch (e: Exception) {
        Timber.d("Nominatim search failed: ${e.message}")
        emptyList()
    }

    @Suppress("DEPRECATION")
    private suspend fun searchSystemGeocoder(query: String): List<RideDestination> = try {
        if (!Geocoder.isPresent()) emptyList()
        else {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 5) { cont.resume(it) }
                }
            } else {
                geocoder.getFromLocationName(query, 5)
            }
            addresses?.mapNotNull { addr ->
                val name = listOfNotNull(addr.featureName, addr.thoroughfare, addr.locality ?: addr.adminArea)
                    .distinct().joinToString(", ").takeIf { it.isNotBlank() } ?: query
                RideDestination(name = name, latitude = addr.latitude, longitude = addr.longitude)
            } ?: emptyList()
        }
    } catch (e: Exception) {
        Timber.d("System geocoder search failed: ${e.message}")
        emptyList()
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): String? {
        val place = LocationHelper.placeName(context, lat, lon)
        if (place != null) return place

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f".format(Locale.US, lat, lon)
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AstraRide/1.3 (Smart Motorcycle Intercom; Android)")
                    .build()
                val response = client.newCall(req).execute()
                response.use { res ->
                    if (!res.isSuccessful) return@withContext null
                    val body = res.body?.string() ?: return@withContext null
                    val json = org.json.JSONObject(body)
                    val name = json.optString("display_name", "")
                    name.split(",").take(2).joinToString(", ").trim().takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
