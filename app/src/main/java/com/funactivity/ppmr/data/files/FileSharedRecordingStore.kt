package com.funactivity.ppmr.data.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.funactivity.ppmr.domain.model.RecordingFile
import com.funactivity.ppmr.domain.repository.SharedRecordingStore
import com.funactivity.ppmr.domain.sync.AudioFormats
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Keeps shared copies in app-private storage, addressed by `file://` URIs. */
@Singleton
class FileSharedRecordingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SharedRecordingStore {

    private val mutex = Mutex()
    private val directory get() = File(context.filesDir, DIRECTORY)

    override suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock { block() }

    override suspend fun importCopy(sourceUri: String, mimeType: String?): RecordingFile? =
        withContext(Dispatchers.IO) {
            val uri = sourceUri.toUri()
            // A share must not be a way to make the app read its own private files.
            if (uri.scheme != "content") return@withContext null
            val resolver = context.contentResolver
            val meta = readMetadata(uri)
            val type = mimeType?.takeIf { it.startsWith("audio/") }
                ?: runCatching { resolver.getType(uri) }.getOrNull()
                ?: mimeType
            val name = displayNameFor(meta.name, type) ?: return@withContext null

            directory.mkdirs()
            val target = File(directory, UUID.randomUUID().toString())
            try {
                val input = resolver.openInputStream(uri) ?: return@withContext null
                input.use { source -> target.outputStream().use { source.copyTo(it) } }
            } catch (e: IOException) {
                Timber.w(e, "Could not copy shared file")
                target.delete()
                return@withContext null
            } catch (e: SecurityException) {
                Timber.w(e, "No access to shared file")
                target.delete()
                return@withContext null
            }
            if (meta.lastModified > 0) target.setLastModified(meta.lastModified)
            RecordingFile(
                documentUri = Uri.fromFile(target).toString(),
                displayName = name,
                size = target.length(),
                // Read back: the file system may round the time it was given.
                lastModified = target.lastModified(),
                mimeType = AudioFormats.mimeTypeFor(name, type),
            )
        }

    override suspend fun delete(documentUri: String) {
        withContext(Dispatchers.IO) { fileOf(documentUri)?.delete() }
    }

    override suspend fun deleteAllExcept(keep: Set<String>) {
        withContext(Dispatchers.IO) {
            directory.listFiles()?.forEach { file ->
                if (Uri.fromFile(file).toString() !in keep) file.delete()
            }
        }
    }

    /** Current metadata of a copy, or null if it is gone. */
    fun stat(documentUri: String, displayName: String, mimeType: String): RecordingFile? {
        val file = fileOf(documentUri)?.takeIf { it.isFile } ?: return null
        return RecordingFile(documentUri, displayName, file.length(), file.lastModified(), mimeType)
    }

    fun open(documentUri: String): InputStream {
        val file = fileOf(documentUri) ?: throw FileNotFoundException("Not a shared copy")
        return file.inputStream()
    }

    fun owns(documentUri: String): Boolean = fileOf(documentUri) != null

    /** The copy for a URI, only if it really is inside the copies folder. */
    private fun fileOf(documentUri: String): File? {
        val uri = documentUri.toUri()
        if (uri.scheme != "file") return null
        val file = File(uri.path ?: return null)
        return file.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile }
    }

    private fun readMetadata(uri: Uri): Metadata = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val modifiedColumn = cursor.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
            Metadata(
                name = if (nameColumn >= 0) cursor.getString(nameColumn) else null,
                lastModified = if (modifiedColumn >= 0 && !cursor.isNull(modifiedColumn)) {
                    cursor.getLong(modifiedColumn)
                } else {
                    0L
                },
            )
        } ?: Metadata(null, 0L)
    } catch (e: Exception) {
        // Some providers reject a null projection or unknown columns. The name is optional.
        Timber.d(e, "Could not read shared file details")
        Metadata(null, 0L)
    }

    /** A safe name with an audio extension, or null if the file does not look like audio. */
    private fun displayNameFor(providedName: String?, mimeType: String?): String? {
        val base = providedName?.substringAfterLast('/')?.trim()?.trimStart('.')?.takeIf { it.isNotEmpty() }
        if (base != null && AudioFormats.isSupported(base)) return base
        val extension = AudioFormats.extensionFor(mimeType) ?: return null
        val stem = base ?: "Call recording " + SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date())
        return "$stem.$extension"
    }

    private data class Metadata(val name: String?, val lastModified: Long)

    private companion object {
        const val DIRECTORY = "shared_recordings"
    }
}
