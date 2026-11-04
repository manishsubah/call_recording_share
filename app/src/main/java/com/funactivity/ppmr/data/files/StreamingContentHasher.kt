package com.funactivity.ppmr.data.files

import com.funactivity.ppmr.domain.model.RecordingFile
import com.funactivity.ppmr.domain.repository.ContentHasher
import com.funactivity.ppmr.domain.repository.RecordingFolderRepository
import com.funactivity.ppmr.domain.sync.FileIdentity.toHex
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

class StreamingContentHasher @Inject constructor(
    private val folders: RecordingFolderRepository,
) : ContentHasher {

    override suspend fun hashOf(file: RecordingFile): String? {
        if (file.size > MAX_HASHED_BYTES) return null
        return withContext(Dispatchers.IO) {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                folders.open(file.documentUri).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                digest.digest().toHex()
            } catch (e: IOException) {
                Timber.d(e, "Could not hash file")
                null
            } catch (e: SecurityException) {
                Timber.d(e, "Could not hash file")
                null
            }
        }
    }

    private companion object {
        /** A recording of this size is hours long, and hashing it would cost real battery. */
        const val MAX_HASHED_BYTES = 64L * 1024 * 1024
        const val BUFFER_SIZE = 64 * 1024
    }
}
