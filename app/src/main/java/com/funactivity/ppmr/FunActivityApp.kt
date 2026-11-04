package com.funactivity.ppmr

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.funactivity.ppmr.sync.SyncBootstrapper
import com.funactivity.ppmr.sync.SyncNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class FunActivityApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncBootstrapper: SyncBootstrapper
    @Inject lateinit var syncNotifier: SyncNotifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        syncNotifier.createChannels()
        syncBootstrapper.start()
    }
}
