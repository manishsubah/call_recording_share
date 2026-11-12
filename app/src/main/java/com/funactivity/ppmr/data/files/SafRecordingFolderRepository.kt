package com.funactivity.ppmr.data.files

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import com.funactivity.ppmr.R
import com.funactivity.ppmr.domain.model.FolderListing
import com.funactivity.ppmr.domain.model.RecordingFile
import com.funactivity.ppmr.domain.repository.RecordingFolderRepository
import com.funactivity.ppmr.domain.sync.AudioFormats
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Lists files with direct [DocumentsContract] queries: `DocumentFile` issues one query per
 * attribute per file, which is noticeably slow on folders with years of recordings.
 */
@Singleton
class SafRecordingFolderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedCopies: FileSharedRecordingStore,
) : RecordingFolderRepository {

    private val resolver get() = context.contentResolver

    override fun takePermission(treeUri: String): Boolean = try {
        resolver.takePersistableUriPermission(treeUri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        Timber.w(e, "Could not persist folder permission")
        false
    }

    override fun releasePermission(treeUri: String) {
        try {
            resolver.releasePersistableUriPermission(treeUri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Timber.d(e, "Folder permission was already released")
        }
    }

    override fun hasPermission(treeUri: String): Boolean {
        val uri = treeUri.toUri()
        return resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    override fun displayNameOf(treeUri: String): String = try {
        formatTreeDocumentId(
            documentId = DocumentsContract.getTreeDocumentId(treeUri.toUri()),
            internalStorageLabel = context.getString(R.string.storage_internal),
            externalStorageLabel = context.getString(R.string.storage_external),
        )
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Unexpected tree URI")
        treeUri
    }

    override suspend fun listRecordings(
        treeUri: String,
        includeSubfolders: Boolean,
    ): FolderListing = withContext(Dispatchers.IO) {
        if (!hasPermission(treeUri)) return@withContext FolderListing.PermissionLost
        try {
            val tree = treeUri.toUri()
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val files = mutableListOf<RecordingFile>()
            val pending = ArrayDeque(listOf(FolderToVisit(rootId, depth = 0)))
            while (pending.isNotEmpty()) {
                ensureActive()
                val folder = pending.removeFirst()
                val isRoot = folder.depth == 0
                val readable = queryChildren(tree, folder.documentId) { cursor ->
                    val row = cursor.toRow()
                    when {
                        row.mimeType == Document.MIME_TYPE_DIR -> {
                            if (includeSubfolders && folder.depth < MAX_DEPTH) {
                                pending.addLast(FolderToVisit(row.documentId, folder.depth + 1))
                            }
                        }
                        AudioFormats.isSupported(row.name) -> {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, row.documentId)
                            files += row.toRecording(uri)
                        }
                    }
                }
                if (!readable && isRoot) {
                    return@withContext FolderListing.Failed("Folder could not be read")
                }
            }
            FolderListing.Success(files)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Timber.w(e, "Folder permission lost during listing")
            FolderListing.PermissionLost
        } catch (e: Exception) {
            // Providers can throw almost anything, e.g. when an SD card is removed mid-query.
            Timber.w(e, "Could not list folder")
            FolderListing.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    override suspend fun stat(documentUri: String): RecordingFile? = withContext(Dispatchers.IO) {
        if (sharedCopies.owns(documentUri)) {
            // Name and type are not stored on disk; the caller compares only size and time.
            return@withContext sharedCopies.stat(documentUri, displayName = "", mimeType = "")
        }
        val uri = documentUri.toUri()
        try {
            resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.toRow().toRecording(uri) else null
            }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            // ExternalStorageProvider reports a missing document this way on several versions.
            Timber.d(e, "Document no longer exists")
            null
        }
    }

    override fun open(documentUri: String): InputStream =
        if (sharedCopies.owns(documentUri)) sharedCopies.open(documentUri)
        else resolver.openInputStream(documentUri.toUri())
            ?: throw FileNotFoundException("Provider returned no stream")

    /** Returns false when the provider gave no cursor, meaning the folder is unreadable. */
    private inline fun queryChildren(tree: Uri, parentId: String, onRow: (Cursor) -> Unit): Boolean {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cursor = resolver.query(childrenUri, PROJECTION, null, null, null) ?: return false
        cursor.use { while (it.moveToNext()) onRow(it) }
        return true
    }

    private fun Cursor.toRow() = Row(
        documentId = getString(COLUMN_ID),
        name = getString(COLUMN_NAME).orEmpty(),
        mimeType = getString(COLUMN_MIME),
        size = if (isNull(COLUMN_SIZE)) 0L else getLong(COLUMN_SIZE),
        lastModified = if (isNull(COLUMN_MODIFIED)) 0L else getLong(COLUMN_MODIFIED),
    )

    private fun Row.toRecording(uri: Uri) = RecordingFile(
        documentUri = uri.toString(),
        displayName = name,
        size = size,
        lastModified = lastModified,
        mimeType = AudioFormats.mimeTypeFor(name, mimeType),
    )

    private data class FolderToVisit(val documentId: String, val depth: Int)

    private class Row(
        val documentId: String,
        val name: String,
        val mimeType: String?,
        val size: Long,
        val lastModified: Long,
    )

    private companion object {
        const val MAX_DEPTH = 8
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
        const val COLUMN_ID = 0
        const val COLUMN_NAME = 1
        const val COLUMN_MIME = 2
        const val COLUMN_SIZE = 3
        const val COLUMN_MODIFIED = 4
    }
}
