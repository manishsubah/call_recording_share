package com.funactivity.ppmr.domain.repository

import com.funactivity.ppmr.domain.model.DriveUploadRequest
import java.io.InputStream

/**
 * Google Drive operations. All of them can throw [java.io.IOException],
 * [com.funactivity.ppmr.domain.model.DriveApiException] and
 * [com.funactivity.ppmr.domain.model.AuthRequiredException].
 */
interface DriveRepository {
    suspend fun findOrCreateFolder(name: String): String
    suspend fun folderExists(folderId: String): Boolean

    /** The ID of a file this app already uploaded with [identityKey], if there is one. */
    suspend fun findUploaded(parentFolderId: String, identityKey: String): String?

    /** Uploads the content and returns the new file's ID. */
    suspend fun upload(
        request: DriveUploadRequest,
        content: InputStream,
        onProgress: (bytesUploaded: Long) -> Unit,
    ): String
}
