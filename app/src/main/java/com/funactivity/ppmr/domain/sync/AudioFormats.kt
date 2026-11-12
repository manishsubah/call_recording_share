package com.funactivity.ppmr.domain.sync

/** The audio types the app is allowed to upload. Everything else in the folder is ignored. */
object AudioFormats {
    private val MIME_BY_EXTENSION = mapOf(
        "m4a" to "audio/mp4",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "amr" to "audio/amr",
        "3gp" to "audio/3gpp",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "opus" to "audio/ogg",
    )

    /** The usual extension for an audio MIME type, used to name shared files that have none. */
    fun extensionFor(mimeType: String?): String? = when (mimeType?.lowercase()?.substringBefore(';')) {
        null -> null
        "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac-adts" -> "m4a"
        "audio/x-wav", "audio/wave", "audio/vnd.wave" -> "wav"
        "audio/3gpp", "video/3gpp" -> "3gp"
        "application/ogg", "audio/opus" -> "ogg"
        else -> MIME_BY_EXTENSION.entries.firstOrNull { it.value == mimeType.lowercase() }?.key
    }

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    /**
     * True for a visible file with a supported extension. Dot-files are excluded because Android
     * uses names like `.pending-123-call.m4a` and `.trashed-123-call.m4a` for files that are
     * still being written or are in the trash.
     */
    fun isSupported(fileName: String): Boolean =
        !fileName.startsWith(".") && extensionOf(fileName) in MIME_BY_EXTENSION

    /**
     * The upload MIME type. Prefers the provider's value when it is audio; providers often report
     * `.3gp` as video or unknown types as octet-stream, so those fall back to the extension.
     */
    fun mimeTypeFor(fileName: String, providerMimeType: String?): String =
        providerMimeType?.takeIf { it.startsWith("audio/") }
            ?: MIME_BY_EXTENSION[extensionOf(fileName)]
            ?: "application/octet-stream"
}
