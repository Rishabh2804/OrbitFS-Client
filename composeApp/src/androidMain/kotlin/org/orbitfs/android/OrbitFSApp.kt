package org.orbitfs.android

import android.app.Application
import timber.log.Timber

class OrbitFSApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
