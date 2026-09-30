package com.bikeride.intercom.feature.ui.nav

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bikeride.intercom.engine.audio.VoiceAnnouncer
import com.bikeride.intercom.mesh.ConvoyMesh
import com.bikeride.intercom.mesh.LocationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Smart Navigation & Automatic Arrival Detection Manager for AstraRide.
 *
 * Continuously tracks GPS position during active ride navigation.
 * Evaluates distance to destination, ETA, and checks GPS accuracy.
 * Automatically triggers:
 * 1. Arrival detection without requiring rider manual confirmation.
 * 2. Stops active arrival monitoring upon arrival.
 * 3. Haptic vibration alert.
 * 4. Spoken helmet voice prompt: "You have reached your destination."
 * 5. Broadcasts encrypted convoy message over BLE mesh and internet fallback:
 *    "📍 DESTINATION REACHED \n🏍️ {Rider} has reached the destination.\n📍 {Destination Name}"
 */
@Singleton
class NavigationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val convoyMesh: ConvoyMesh,
    private val voiceAnnouncer: VoiceAnnouncer,
    private val geocoder: DestinationGeocoder
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _destination = MutableStateFlow<RideDestination?>(null)
    val destination: StateFlow<RideDestination?> = _destination.asStateFlow()

    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

    private val _distanceRemainingMeters = MutableStateFlow<Double?>(null)
    val distanceRemainingMeters: StateFlow<Double?> = _distanceRemainingMeters.asStateFlow()

    private val _etaMinutes = MutableStateFlow<Int?>(null)
    val etaMinutes: StateFlow<Int?> = _etaMinutes.asStateFlow()

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _hasArrived = MutableStateFlow(false)
    val hasArrived: StateFlow<Boolean> = _hasArrived.asStateFlow()

    private val _arrivalAlert = MutableSharedFlow<RideDestination>(extraBufferCapacity = 2)
    val arrivalAlert: SharedFlow<RideDestination> = _arrivalAlert.asSharedFlow()

    private var consecutiveArrivalHits = 0
    private var isGpsListening = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocationUpdate(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    init {
        // Prime current location from cache
        LocationHelper.lastKnown(context)?.let {
            _currentLocation.value = it
        }
    }

    /**
     * Set a new destination and start navigation and arrival monitoring.
     */
    fun startNavigation(destination: RideDestination) {
        Timber.i("Starting navigation to: ${destination.name} (${destination.latitude}, ${destination.longitude})")
        _destination.value = destination
        _isNavigating.value = true
        _hasArrived.value = false
        consecutiveArrivalHits = 0

        // Calculate initial distance & ETA if location is known
        _currentLocation.value?.let { loc ->
            val dist = LocationHelper.distanceMeters(loc.first, loc.second, destination.latitude, destination.longitude)
            _distanceRemainingMeters.value = dist
            _etaMinutes.value = ((dist / 11.1) / 60.0).roundToInt().coerceAtLeast(1)
        }

        startGpsUpdates()
    }

    fun endNavigation() {
        Timber.i("Ending ride navigation")
        _isNavigating.value = false
        _hasArrived.value = false
        _destination.value = null
        _distanceRemainingMeters.value = null
        _etaMinutes.value = null
        consecutiveArrivalHits = 0
        stopGpsUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startGpsUpdates() {
        if (isGpsListening || locationManager == null) return
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    2.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
            isGpsListening = true
        } catch (e: SecurityException) {
            Timber.w("Location permissions missing for navigation GPS updates")
        } catch (e: Exception) {
            Timber.w(e, "Could not start GPS updates")
        }
    }

    private fun stopGpsUpdates() {
        if (!isGpsListening || locationManager == null) return
        try {
            locationManager.removeUpdates(locationListener)
            isGpsListening = false
        } catch (e: Exception) {
            Timber.w(e, "Error stopping location updates")
        }
    }

    private fun handleLocationUpdate(loc: Location) {
        val lat = loc.latitude
        val lon = loc.longitude
        _currentLocation.value = lat to lon

        val dest = _destination.value ?: return
        if (!_isNavigating.value || _hasArrived.value) return

        val distance = LocationHelper.distanceMeters(lat, lon, dest.latitude, dest.longitude)
        _distanceRemainingMeters.value = distance

        // Approximate ETA based on current speed or ~40 km/h average motorcycle speed (11.1 m/s)
        val speedMps = if (loc.hasSpeed() && loc.speed > 2.0f) loc.speed.toDouble() else 11.1
        val etaMinutes = ((distance / speedMps) / 60.0).roundToInt().coerceAtLeast(1)
        _etaMinutes.value = etaMinutes

        // ── Smart Arrival Detection ──
        // Prevent false arrivals: ignore readings with GPS accuracy worse than 50 meters
        if (loc.hasAccuracy() && loc.accuracy > 50f) {
            Timber.d("Ignoring GPS fix for arrival check due to low accuracy (${loc.accuracy}m)")
            return
        }

        if (distance <= dest.arrivalRadiusMeters) {
            consecutiveArrivalHits++
            Timber.d("Arrival proximity hit $consecutiveArrivalHits/2 (distance: ${distance}m, radius: ${dest.arrivalRadiusMeters}m)")
            if (consecutiveArrivalHits >= 2) {
                triggerArrival(dest)
            }
        } else {
            consecutiveArrivalHits = 0
        }
    }

    private fun triggerArrival(dest: RideDestination) {
        Timber.i("🎯 ARRIVAL DETECTED at destination: ${dest.name}")
        _hasArrived.value = true
        _isNavigating.value = false
        stopGpsUpdates()

        // 1. Trigger haptic vibration
        triggerVibration()

        // 2. Announce through helmet audio
        voiceAnnouncer.say("You have reached your destination.", urgent = true)

        // 3. Automatically send convoy message over mesh and internet fallback
        convoyMesh.sendDestinationReached(dest.name, dest.latitude, dest.longitude)

        _arrivalAlert.tryEmit(dest)
    }

    private fun triggerVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 400), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }
        } catch (e: Exception) {
            Timber.w(e, "Error triggering arrival vibration")
        }
    }
}
