package com.funactivity.ppmr.sync

import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.domain.repository.SyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps scheduled work in line with the settings for as long as the process is alive. */
@Singleton
class SyncBootstrapper @Inject constructor(
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            settings.settings.map { it.autoSync }.distinctUntilChanged().collect { enabled ->
                // Scheduling is a no-op when already enqueued, so this is safe on each process start.
                if (enabled && settings.appState.first().accountEmail != null) {
                    scheduler.setAutoSyncEnabled(true)
                    scheduler.requestScan(manual = false)
                } else if (!enabled) {
                    scheduler.setAutoSyncEnabled(false)
                }
            }
        }
        scope.launch {
            // A waiting upload must pick up a changed Wi-Fi setting.
            settings.settings.map { it.wifiOnly }.distinctUntilChanged().drop(1).collect { wifiOnly ->
                val state = settings.appState.first()
                if (state.accountEmail != null && !state.authRequired &&
                    sync.nextDue(System.currentTimeMillis()) != null
                ) {
                    scheduler.startUploads(wifiOnly, expedited = false)
                }
            }
        }
    }
}
