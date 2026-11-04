package com.funactivity.ppmr.domain.model

enum class DriveErrorKind {
    /** Token rejected or scope missing; the user has to re-authorize. */
    AUTH,

    /** The user's Drive is full. Retrying will not help until they free up space. */
    QUOTA_EXCEEDED,

    /** Rate limits and server errors: worth retrying later. */
    TRANSIENT,

    /** The destination folder or upload session no longer exists. */
    NOT_FOUND,

    /** The request itself was refused, e.g. Drive API not enabled for the Cloud project. */
    PERMANENT,
}

class DriveApiException(
    val kind: DriveErrorKind,
    val httpCode: Int,
    message: String,
) : Exception(message)

/** What to upload and where. [identityKey] is stored on the Drive file to detect repeats. */
data class DriveUploadRequest(
    val name: String,
    val mimeType: String,
    val size: Long,
    val modifiedAt: Long,
    val parentFolderId: String,
    val identityKey: String,
)
