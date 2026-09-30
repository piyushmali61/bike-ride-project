package com.bikeride.intercom.feature.ui.nav

/**
 * Ride Destination model for AstraRide Smart Navigation.
 */
data class RideDestination(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val arrivalRadiusMeters: Double = 40.0
)
