package com.funactivity.ppmr.domain.usecase

import com.funactivity.ppmr.domain.model.SyncItem
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.domain.repository.SyncScheduler
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** "Sync now": retries everything that is waiting or failed, then scans for new files. */
class ManualSyncUseCase @Inject constructor(
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke() {
        sync.retryAllNow()
        scheduler.requestScan(manual = true)
    }
}

class RetryUploadUseCase @Inject constructor(
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(identityKey: String) {
        sync.retry(identityKey)
        scheduler.requestScan(manual = true)
    }
}

class ObserveSyncEventsUseCase @Inject constructor(
    private val sync: SyncRepository,
) {
    operator fun invoke(limit: Int = 20): Flow<List<SyncItem>> = sync.observeRecentActivity(limit)
}
