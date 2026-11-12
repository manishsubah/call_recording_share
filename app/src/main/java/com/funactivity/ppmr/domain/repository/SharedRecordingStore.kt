package com.funactivity.ppmr.domain.repository

import com.funactivity.ppmr.domain.model.RecordingFile

/**
 * Private copies of recordings shared to the app from other apps. A share only grants access
 * while the receiving screen is open, so the file is copied before it is queued.
 */
interface SharedRecordingStore {
    /** Copies a shared file. Returns null if it could not be read or is not audio. */
    suspend fun importCopy(sourceUri: String, mimeType: String?): RecordingFile?

    suspend fun delete(documentUri: String)

    /** Deletes every copy whose URI is not in [keep]. */
    suspend fun deleteAllExcept(keep: Set<String>)

    /** Runs [block] so imports and clean-up never interleave. */
    suspend fun <T> locked(block: suspend () -> T): T
}
