package com.funactivity.ppmr.data.local.db

import androidx.room.withTransaction
import com.funactivity.ppmr.domain.model.RecordingFile
import com.funactivity.ppmr.domain.model.SyncItem
import com.funactivity.ppmr.domain.model.SyncStatus
import com.funactivity.ppmr.domain.model.TrackedFile
import com.funactivity.ppmr.domain.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomSyncRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: SyncDao,
) : SyncRepository {

    override fun observeRecentActivity(limit: Int): Flow<List<SyncItem>> =
        dao.observeLatestEvents(limit).map { events -> events.map { it.toItem() } }

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    override fun observeFailedCount(): Flow<Int> = dao.observeFailedCount()

    override suspend fun statusOf(identityKey: String): SyncStatus? = dao.find(identityKey)?.status

    override suspend fun allIdentityKeys(): Set<String> = dao.allIdentityKeys().toSet()

    override suspend fun driveFileIdForContent(contentHash: String): String? =
        dao.driveFileIdForContent(contentHash)

    override suspend fun enqueue(file: RecordingFile, identityKey: String, contentHash: String?) {
        database.withTransaction {
            dao.upsertFile(file.toEntity(identityKey, contentHash, SyncStatus.QUEUED))
            log(identityKey, file.displayName, SyncStatus.QUEUED, null)
        }
    }

    override suspend fun recordAsDuplicate(
        file: RecordingFile,
        identityKey: String,
        contentHash: String?,
        driveFileId: String,
        message: String,
    ) {
        database.withTransaction {
            dao.upsertFile(
                file.toEntity(identityKey, contentHash, SyncStatus.UPLOADED).copy(
                    driveFileId = driveFileId,
                    uploadedAt = System.currentTimeMillis(),
                ),
            )
            log(identityKey, file.displayName, SyncStatus.UPLOADED, message)
        }
    }

    override suspend fun nextDue(now: Long): TrackedFile? = dao.nextDue(now)?.toTracked()

    override suspend fun earliestPendingAttempt(): Long? = dao.earliestPendingAttempt()

    override suspend fun markUploading(identityKey: String) = transition(identityKey) {
        dao.updateStatus(identityKey, SyncStatus.UPLOADING)
        log(identityKey, it.displayName, SyncStatus.UPLOADING, null)
    }

    override suspend fun markUploaded(identityKey: String, driveFileId: String, message: String?) =
        transition(identityKey) {
            dao.markUploaded(identityKey, driveFileId, System.currentTimeMillis())
            log(identityKey, it.displayName, SyncStatus.UPLOADED, message)
        }

    override suspend fun scheduleRetry(
        identityKey: String,
        attempts: Int,
        nextAttemptAt: Long,
        message: String,
    ) = transition(identityKey) {
        dao.updateAttempt(identityKey, SyncStatus.QUEUED, attempts, nextAttemptAt)
        log(identityKey, it.displayName, SyncStatus.QUEUED, message)
    }

    override suspend fun markFailed(identityKey: String, attempts: Int, message: String) =
        transition(identityKey) {
            dao.updateAttempt(identityKey, SyncStatus.FAILED, attempts, nextAttemptAt = 0)
            log(identityKey, it.displayName, SyncStatus.FAILED, message)
        }

    override suspend fun resetInterrupted() {
        database.withTransaction {
            dao.findByStatus(SyncStatus.UPLOADING).forEach { file ->
                dao.updateStatus(file.identityKey, SyncStatus.QUEUED)
                log(file.identityKey, file.displayName, SyncStatus.QUEUED, null)
            }
        }
    }

    override suspend fun retry(identityKey: String) = transition(identityKey) {
        requeue(it)
    }

    override suspend fun retryAllFailed() {
        database.withTransaction { dao.findByStatus(SyncStatus.FAILED).forEach { requeue(it) } }
    }

    override suspend fun forget(identityKey: String) = dao.deleteFile(identityKey)

    override suspend fun clearUnfinished() = dao.deleteUnfinishedFiles()

    override suspend fun clearHistory() {
        // Failed rows go too, so the badge agrees with the now-empty list. Those files are
        // simply rediscovered by the next scan if they still exist.
        database.withTransaction {
            dao.deleteFailedFiles()
            dao.deleteAllEvents()
        }
    }

    override suspend fun clearAll() {
        database.withTransaction {
            dao.deleteAllFiles()
            dao.deleteAllEvents()
        }
    }

    private suspend fun requeue(file: SyncedFileEntity) {
        dao.updateAttempt(file.identityKey, SyncStatus.QUEUED, attempts = 0, nextAttemptAt = 0)
        log(file.identityKey, file.displayName, SyncStatus.QUEUED, null)
    }

    private suspend fun transition(identityKey: String, block: suspend (SyncedFileEntity) -> Unit) {
        database.withTransaction {
            dao.find(identityKey)?.let { block(it) }
        }
    }

    private suspend fun log(identityKey: String, fileName: String, status: SyncStatus, message: String?) {
        dao.insertEvent(
            SyncEventEntity(
                identityKey = identityKey,
                fileName = fileName,
                status = status,
                timestamp = System.currentTimeMillis(),
                message = message,
            ),
        )
    }

    private fun RecordingFile.toEntity(identityKey: String, contentHash: String?, status: SyncStatus) =
        SyncedFileEntity(
            identityKey = identityKey,
            documentUri = documentUri,
            displayName = displayName,
            size = size,
            lastModified = lastModified,
            mimeType = mimeType,
            contentHash = contentHash,
            status = status,
        )

    private fun SyncEventEntity.toItem() = SyncItem(
        identityKey = identityKey,
        fileName = fileName,
        status = status,
        timestamp = timestamp,
        message = message,
    )

    private fun SyncedFileEntity.toTracked() = TrackedFile(
        identityKey = identityKey,
        documentUri = documentUri,
        displayName = displayName,
        size = size,
        lastModified = lastModified,
        mimeType = mimeType,
        contentHash = contentHash,
        status = status,
        attempts = attempts,
        nextAttemptAt = nextAttemptAt,
    )
}
