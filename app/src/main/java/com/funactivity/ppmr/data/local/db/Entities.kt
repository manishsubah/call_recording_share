package com.funactivity.ppmr.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.funactivity.ppmr.domain.model.SyncStatus

/** Source of truth for dedup: one row per file identity the app has seen. */
@Entity(
    tableName = "synced_files",
    indices = [Index("status"), Index("contentHash")],
)
data class SyncedFileEntity(
    @PrimaryKey val identityKey: String,
    val documentUri: String,
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val contentHash: String?,
    val status: SyncStatus,
    val driveFileId: String? = null,
    val attempts: Int = 0,
    @ColumnInfo(defaultValue = "0") val nextAttemptAt: Long = 0,
    val uploadedAt: Long? = null,
)

/** Append-only log of status changes, shown as recent activity. */
@Entity(tableName = "sync_events", indices = [Index("identityKey")])
data class SyncEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identityKey: String,
    val fileName: String,
    val status: SyncStatus,
    val timestamp: Long,
    val message: String?,
)
