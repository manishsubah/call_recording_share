package com.funactivity.ppmr.domain.model

/** A candidate audio file found in the user's selected folder. */
data class RecordingFile(
    val documentUri: String,
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
)
