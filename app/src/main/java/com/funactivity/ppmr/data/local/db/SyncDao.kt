package com.funactivity.ppmr.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.funactivity.ppmr.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    /** The newest event per file, so one upload is one row rather than three. */
    @Query(
        """
        SELECT * FROM sync_events
        WHERE id IN (SELECT MAX(id) FROM sync_events GROUP BY identityKey)
        ORDER BY id DESC
        LIMIT :limit
        """,
    )
    fun observeLatestEvents(limit: Int): Flow<List<SyncEventEntity>>

    @Query("SELECT COUNT(*) FROM synced_files WHERE status IN ('QUEUED', 'UPLOADING')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM synced_files WHERE status = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Query("SELECT * FROM synced_files WHERE identityKey = :identityKey")
    suspend fun find(identityKey: String): SyncedFileEntity?

    @Query("SELECT identityKey FROM synced_files")
    suspend fun allIdentityKeys(): List<String>

    @Query(
        """
        SELECT driveFileId FROM synced_files
        WHERE contentHash = :contentHash AND status = 'UPLOADED' AND driveFileId IS NOT NULL
        LIMIT 1
        """,
    )
    suspend fun driveFileIdForContent(contentHash: String): String?

    @Query(
        """
        SELECT * FROM synced_files
        WHERE status = 'QUEUED' AND nextAttemptAt <= :now
        ORDER BY nextAttemptAt, lastModified
        LIMIT 1
        """,
    )
    suspend fun nextDue(now: Long): SyncedFileEntity?

    @Query("SELECT MIN(nextAttemptAt) FROM synced_files WHERE status = 'QUEUED'")
    suspend fun earliestPendingAttempt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: SyncedFileEntity)

    @Insert
    suspend fun insertEvent(event: SyncEventEntity)

    @Query("UPDATE synced_files SET status = :status WHERE identityKey = :identityKey")
    suspend fun updateStatus(identityKey: String, status: SyncStatus)

    @Query(
        """
        UPDATE synced_files
        SET status = 'UPLOADED', driveFileId = :driveFileId, uploadedAt = :uploadedAt
        WHERE identityKey = :identityKey
        """,
    )
    suspend fun markUploaded(identityKey: String, driveFileId: String, uploadedAt: Long)

    @Query(
        """
        UPDATE synced_files
        SET status = :status, attempts = :attempts, nextAttemptAt = :nextAttemptAt
        WHERE identityKey = :identityKey
        """,
    )
    suspend fun updateAttempt(identityKey: String, status: SyncStatus, attempts: Int, nextAttemptAt: Long)

    @Query("SELECT * FROM synced_files WHERE status = :status")
    suspend fun findByStatus(status: SyncStatus): List<SyncedFileEntity>

    @Query("DELETE FROM synced_files WHERE identityKey = :identityKey")
    suspend fun deleteFile(identityKey: String)

    @Query("DELETE FROM synced_files WHERE status != 'UPLOADED'")
    suspend fun deleteUnfinishedFiles()

    @Query("DELETE FROM synced_files WHERE status = 'FAILED'")
    suspend fun deleteFailedFiles()

    @Query("DELETE FROM synced_files")
    suspend fun deleteAllFiles()

    @Query(
        """
        DELETE FROM sync_events
        WHERE identityKey NOT IN (SELECT identityKey FROM synced_files WHERE status = 'UPLOADED')
        """,
    )
    suspend fun deleteUnfinishedEvents()

    @Query("DELETE FROM sync_events")
    suspend fun deleteAllEvents()
}
