package com.funactivity.ppmr.domain.repository

import com.funactivity.ppmr.domain.model.FolderListing
import com.funactivity.ppmr.domain.model.RecordingFile
import java.io.InputStream

interface RecordingFolderRepository {
    /** Takes a persistable read grant so the folder stays accessible across restarts. */
    fun takePermission(treeUri: String): Boolean
    fun releasePermission(treeUri: String)
    fun hasPermission(treeUri: String): Boolean
    fun displayNameOf(treeUri: String): String

    suspend fun listRecordings(treeUri: String, includeSubfolders: Boolean): FolderListing

    /** Current metadata for one file, or null if it no longer exists. */
    suspend fun stat(documentUri: String): RecordingFile?

    /** Opens a file for reading. The caller closes the stream. */
    fun open(documentUri: String): InputStream
}
