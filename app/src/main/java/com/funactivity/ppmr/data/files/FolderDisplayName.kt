package com.funactivity.ppmr.data.files

/**
 * Turns a SAF tree document ID such as `primary:Recordings/Call` into a readable path.
 * The ID format is an implementation detail of the storage provider, so anything that does not
 * look like `volume:path` is shown as it is.
 */
internal fun formatTreeDocumentId(
    documentId: String,
    internalStorageLabel: String,
    externalStorageLabel: String,
): String {
    val volume = documentId.substringBefore(':', missingDelimiterValue = "")
    if (volume.isEmpty()) return documentId
    val path = documentId.substringAfter(':')
    val root = if (volume == "primary") internalStorageLabel else "$externalStorageLabel ($volume)"
    return if (path.isEmpty()) root else "$root/$path"
}
