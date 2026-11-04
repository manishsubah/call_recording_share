package com.funactivity.ppmr.domain.usecase

import com.funactivity.ppmr.domain.model.DriveUploadRequest
import com.funactivity.ppmr.domain.model.TrackedFile
import com.funactivity.ppmr.domain.repository.DriveRepository
import com.funactivity.ppmr.domain.repository.RecordingFolderRepository
import java.io.FileNotFoundException
import javax.inject.Inject

sealed interface UploadOutcome {
    data class Uploaded(val driveFileId: String, val alreadyInDrive: Boolean) : UploadOutcome

    /** The file changed after being queued. A later scan treats it as a new identity. */
    data object FileChanged : UploadOutcome
    data object FileMissing : UploadOutcome
    data object FolderAccessLost : UploadOutcome
}

/**
 * Uploads one file. Network and Drive failures are thrown for the worker to classify. The Drive
 * check first covers an earlier upload whose response was lost, or a reinstall with empty records.
 */
class UploadRecordingUseCase @Inject constructor(
    private val folders: RecordingFolderRepository,
    private val drive: DriveRepository,
) {
    suspend operator fun invoke(
        file: TrackedFile,
        folderId: String,
        onProgress: (Long) -> Unit,
    ): UploadOutcome {
        try {
            val current = folders.stat(file.documentUri) ?: return UploadOutcome.FileMissing
            if (current.size != file.size || current.lastModified != file.lastModified) {
                return UploadOutcome.FileChanged
            }
            drive.findUploaded(folderId, file.identityKey)?.let {
                return UploadOutcome.Uploaded(it, alreadyInDrive = true)
            }
            val request = DriveUploadRequest(
                name = file.displayName,
                mimeType = file.mimeType,
                size = file.size,
                modifiedAt = file.lastModified,
                parentFolderId = folderId,
                identityKey = file.identityKey,
            )
            val driveFileId = openSource(file).use { drive.upload(request, it, onProgress) }
            return UploadOutcome.Uploaded(driveFileId, alreadyInDrive = false)
        } catch (e: MissingSourceException) {
            return UploadOutcome.FileMissing
        } catch (e: SecurityException) {
            return UploadOutcome.FolderAccessLost
        }
    }

    /** Only a failure to open means the file is gone; later I/O errors are network or read errors. */
    private fun openSource(file: TrackedFile) = try {
        folders.open(file.documentUri)
    } catch (e: FileNotFoundException) {
        throw MissingSourceException()
    }

    private class MissingSourceException : Exception()
}
