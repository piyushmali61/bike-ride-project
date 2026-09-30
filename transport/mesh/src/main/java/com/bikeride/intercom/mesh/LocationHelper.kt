package com.bikeride.intercom.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.*

/** Best recent location from any provider, without Play Services or a live GPS fix. */
object LocationHelper {
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Pair<Double, Double>? = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.getProviders(true)
            .mapNotNull { lm.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    } catch (e: SecurityException) {
        Timber.w("Location permission missing")
        null
    }

    /** Great-circle distance in metres. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** Compass direction from point 1 to point 2, e.g. "north-east". */
    fun direction(lat1: Double, lon1: Double, lat2: Double, lon2: Double): String {
        val y = sin(Math.toRadians(lon2 - lon1)) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
            sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(Math.toRadians(lon2 - lon1))
        val bearing = (Math.toDegrees(atan2(y, x)) + 360) % 360
        val names = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
        return names[((bearing + 22.5) / 45).toInt() % 8]
    }

    /** "850 metres" / "3.4 kilometres", for speech. */
    fun spokenDistance(meters: Double): String = when {
        meters < 1000 -> "${(meters / 10).roundToInt() * 10} metres"
        else -> "%.1f kilometres".format(Locale.US, meters / 1000)
    }

    /**
     * Street / area name for a coordinate. Uses the phone's geocoder (usually needs data);
     * returns null offline or after 3 s so callers can fall back to coordinates.
     */
    @Suppress("DEPRECATION")
    suspend fun placeName(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return withTimeoutOrNull(3_000) {
            try {
                val address = if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1) { cont.resume(it.firstOrNull()) }
                    }
                } else {
                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                }
                address?.let { listOfNotNull(it.thoroughfare, it.subLocality ?: it.locality).distinct().joinToString(", ") }
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
        }
    }
}
