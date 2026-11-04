package com.funactivity.ppmr.domain.repository

import com.funactivity.ppmr.domain.model.RecordingFile

interface ContentHasher {
    /** A hash of the content, or null if the file is very large or could not be read. */
    suspend fun hashOf(file: RecordingFile): String?
}
