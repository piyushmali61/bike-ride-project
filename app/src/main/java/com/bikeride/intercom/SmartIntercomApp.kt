package com.bikeride.intercom

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Smart Intercom application entry point.
 */
@HiltAndroidApp
class SmartIntercomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("Smart Intercom initialized")
    }
}
