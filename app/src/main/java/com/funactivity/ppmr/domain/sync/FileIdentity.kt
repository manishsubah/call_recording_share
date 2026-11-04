package com.funactivity.ppmr.domain.sync

import java.security.MessageDigest

/** Identity of a file for dedup: the same name, size and modified time means the same file. */
object FileIdentity {
    fun keyOf(displayName: String, size: Long, lastModified: Long): String =
        // The separator cannot appear in a file name, so different fields cannot collide.
        sha256Hex("$displayName\u0000$size\u0000$lastModified".toByteArray())

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
