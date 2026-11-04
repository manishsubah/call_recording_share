package com.funactivity.ppmr.domain.model

/** Result of enumerating the source folder. Enumeration never throws to callers. */
sealed interface FolderListing {
    data class Success(val files: List<RecordingFile>) : FolderListing

    /** The persisted permission is gone or was revoked: the user must re-pick the folder. */
    data object PermissionLost : FolderListing

    /** Recoverable failure, e.g. an SD card that is currently unmounted. */
    data class Failed(val reason: String) : FolderListing
}
