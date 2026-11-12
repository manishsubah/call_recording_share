package com.funactivity.ppmr.domain.repository

import com.funactivity.ppmr.domain.model.RecordingFile
import com.funactivity.ppmr.domain.model.SyncItem
import com.funactivity.ppmr.domain.model.TrackedFile
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun observeRecentActivity(limit: Int): Flow<List<SyncItem>>
    fun observePendingCount(): Flow<Int>
    fun observeFailedCount(): Flow<Int>

    suspend fun allIdentityKeys(): Set<String>

    /** Source URIs of files not uploaded yet, including failed ones that may be retried. */
    suspend fun unfinishedDocumentUris(): Set<String>

    /** The Drive file ID of an uploaded file with this content, used to spot renamed copies. */
    suspend fun driveFileIdForContent(contentHash: String): String?

    /** True if a file with this content is queued or uploading right now. */
    suspend fun hasPendingContent(contentHash: String): Boolean

    suspend fun enqueue(file: RecordingFile, identityKey: String, contentHash: String?)
    suspend fun recordAsDuplicate(
        file: RecordingFile,
        identityKey: String,
        contentHash: String?,
        driveFileId: String,
        message: String,
    )

    suspend fun nextDue(now: Long): TrackedFile?
    suspend fun earliestPendingAttempt(): Long?

    suspend fun markUploading(identityKey: String)
    suspend fun markUploaded(identityKey: String, driveFileId: String, message: String?)
    suspend fun scheduleRetry(identityKey: String, attempts: Int, nextAttemptAt: Long, message: String)
    suspend fun markFailed(identityKey: String, attempts: Int, message: String)

    /** Returns UPLOADING rows to QUEUED after an interruption, without using up an attempt. */
    suspend fun resetInterrupted()

    /** Puts one failed file back in the queue with a fresh set of attempts. */
    suspend fun retry(identityKey: String)

    /** Requeues failed files and cancels the wait on files in backoff. */
    suspend fun retryAllNow()

    /** Forgets a queued file, e.g. because it changed and will be rediscovered as a new identity. */
    suspend fun forget(identityKey: String)

    /**
     * Forgets everything not yet uploaded, except shared copies, which belong to no folder.
     * Used when a source folder is removed or the user signs out.
     */
    suspend fun clearUnfinished()

    /** Clears the visible history and failed rows. Uploaded records stay so nothing re-uploads. */
    suspend fun clearHistory()

    /** Forgets everything, including upload records. Used when the Google account changes. */
    suspend fun clearAll()
}
