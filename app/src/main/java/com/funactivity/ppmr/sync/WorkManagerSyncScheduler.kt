package com.funactivity.ppmr.sync

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.funactivity.ppmr.domain.repository.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Three things start a scan:
 *  - a MediaStore change, via a content-URI trigger that fires even if the app is not running;
 *  - a 15-minute periodic scan, for folders MediaStore does not index;
 *  - an immediate request from the UI, or a delayed one for a retry or a too-new file.
 * Scans start the upload queue. WorkManager persists all of it, so it survives reboots.
 */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
) : SyncScheduler {

    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun setAutoSyncEnabled(enabled: Boolean) {
        if (enabled) {
            val periodic = PeriodicWorkRequestBuilder<ScanWorker>(15, TimeUnit.MINUTES).build()
            workManager.enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
            armContentTrigger(ExistingWorkPolicy.KEEP)
        } else {
            workManager.cancelUniqueWork(WORK_PERIODIC)
            workManager.cancelUniqueWork(WORK_CONTENT_TRIGGER)
        }
    }

    /**
     * Content-URI work fires once, so each run arms the next. APPEND_OR_REPLACE is the policy for
     * that: replacing unique work from inside its own worker would cancel the worker.
     */
    fun armContentTrigger(policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ContentTriggerWorker>()
            .setConstraints(
                Constraints.Builder()
                    .addContentUriTrigger(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true)
                    .setTriggerContentUpdateDelay(Duration.ofSeconds(5))
                    .setTriggerContentMaxDelay(Duration.ofSeconds(30))
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(WORK_CONTENT_TRIGGER, policy, request)
    }

    override fun requestScan(manual: Boolean) {
        val builder = OneTimeWorkRequestBuilder<ScanWorker>().setInputData(scanInput(manual))
        if (manual) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        // A manual request replaces a waiting scan so it runs now and with the manual flag.
        val policy = if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(WORK_SCAN, policy, builder.build())
    }

    override fun scheduleScan(delayMillis: Long, manual: Boolean) {
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(scanInput(manual))
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_DELAYED_SCAN, ExistingWorkPolicy.REPLACE, request)
    }

    override suspend fun startUploads(wifiOnly: Boolean, expedited: Boolean) {
        val running = workManager.getWorkInfosForUniqueWorkFlow(WORK_UPLOAD).first()
            .any { it.state == WorkInfo.State.RUNNING }
        // A running worker re-checks the queue and the network between files, so leave it alone.
        // Replacing a waiting one makes sure it has the current network setting.
        if (running) return
        workManager.enqueueUniqueWork(WORK_UPLOAD, ExistingWorkPolicy.REPLACE, uploadRequest(wifiOnly, expedited))
    }

    private fun uploadRequest(wifiOnly: Boolean, expedited: Boolean): OneTimeWorkRequest {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val builder = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
        if (expedited) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        return builder.build()
    }

    override fun cancelAll() {
        listOf(WORK_PERIODIC, WORK_CONTENT_TRIGGER, WORK_SCAN, WORK_DELAYED_SCAN, WORK_UPLOAD)
            .forEach(workManager::cancelUniqueWork)
    }

    private fun scanInput(manual: Boolean) = workDataOf(ScanWorker.KEY_MANUAL to manual)

    private companion object {
        const val WORK_PERIODIC = "periodic-scan"
        const val WORK_CONTENT_TRIGGER = "content-trigger"
        const val WORK_SCAN = "scan"
        const val WORK_DELAYED_SCAN = "delayed-scan"
        const val WORK_UPLOAD = "upload-queue"
    }
}
