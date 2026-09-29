package com.bikeride.intercom.spikes

import android.app.Application
import timber.log.Timber

class SpikeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Timber.i("Phase 0 Spike Test App initialized — ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT}")
    }
}
