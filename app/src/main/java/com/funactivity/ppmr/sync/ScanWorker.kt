package com.funactivity.ppmr.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.domain.repository.SyncScheduler
import com.funactivity.ppmr.domain.usecase.EnqueueNewRecordingsUseCase
import com.funactivity.ppmr.domain.usecase.ScanResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/** Looks for new recordings, starts uploads, and plans the next wake-up if one is needed. */
@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val enqueueNewRecordings: EnqueueNewRecordingsUseCase,
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
    private val tracker: SyncProgressTracker,
    private val notifier: SyncNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = notifier.scanningForeground()

    override suspend fun doWork(): Result {
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val config = settings.settings.first()
        // With auto-sync off, a non-manual run only moves along what the user already started.
        if (manual || config.autoSync) {
            scan(manual)
        }
        startPendingUploads(manual, config.wifiOnly)
        return Result.success()
    }

    private suspend fun scan(manual: Boolean) {
        val uploading = tracker.progress.value is SyncProgress.Uploading
        if (!uploading) tracker.update(SyncProgress.Scanning)
        try {
            when (val result = enqueueNewRecordings()) {
                is ScanResult.Completed -> {
                    Timber.d("Scan queued %d file(s)", result.enqueued)
                    result.nextReadyAt?.let {
                        scheduler.scheduleScan(it - System.currentTimeMillis(), manual)
                    }
                }
                ScanResult.FolderAccessLost -> notifyFolderLostOnce()
                is ScanResult.Failed -> Timber.w("Scan failed: %s", result.reason)
                ScanResult.NothingToScan -> Unit
            }
        } finally {
            if (tracker.progress.value is SyncProgress.Scanning) tracker.update(SyncProgress.Idle)
        }
    }

    private suspend fun notifyFolderLostOnce() {
        if (!settings.appState.first().folderLostNotified) {
            notifier.folderAccessLost()
            settings.setFolderLostNotified(true)
        }
    }

    private suspend fun startPendingUploads(manual: Boolean, wifiOnly: Boolean) {
        val state = settings.appState.first()
        if (state.accountEmail == null || state.authRequired) return
        val now = System.currentTimeMillis()
        if (sync.nextDue(now) != null) {
            scheduler.startUploads(wifiOnly, expedited = manual)
        } else {
            // Everything left is in backoff. Wake up when the first one is due.
            sync.earliestPendingAttempt()?.let { scheduler.scheduleScan(it - now, manual = false) }
        }
    }

    companion object {
        const val KEY_MANUAL = "manual"
    }
}
