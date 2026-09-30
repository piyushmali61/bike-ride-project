package com.bikeride.intercom.feature.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import timber.log.Timber

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
}
