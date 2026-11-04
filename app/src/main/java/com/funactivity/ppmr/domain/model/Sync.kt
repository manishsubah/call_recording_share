package com.funactivity.ppmr.domain.model

enum class SyncStatus { QUEUED, UPLOADING, UPLOADED, FAILED }

/** One row of recent activity: the latest thing that happened to a file. */
data class SyncItem(
    val identityKey: String,
    val fileName: String,
    val status: SyncStatus,
    val timestamp: Long,
    val message: String?,
)

/** A file the sync engine knows about, with its upload state. */
data class TrackedFile(
    val identityKey: String,
    val documentUri: String,
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val contentHash: String?,
    val status: SyncStatus,
    val attempts: Int,
    val nextAttemptAt: Long,
)
