package com.funactivity.ppmr.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs when MediaStore reports an audio change: asks for a scan and re-arms the trigger. */
@HiltWorker
class ContentTriggerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: WorkManagerSyncScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        scheduler.requestScan(manual = false)
        scheduler.armContentTrigger(ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
