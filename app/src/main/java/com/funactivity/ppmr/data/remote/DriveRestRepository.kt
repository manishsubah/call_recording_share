package com.funactivity.ppmr.data.remote

import com.funactivity.ppmr.domain.model.DriveApiException
import com.funactivity.ppmr.domain.model.DriveUploadRequest
import com.funactivity.ppmr.domain.model.DriveErrorKind
import com.funactivity.ppmr.domain.repository.AccessTokenProvider
import com.funactivity.ppmr.domain.repository.DriveRepository
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DriveBaseUrl

/** Drive API v3 over REST. Scope is `drive.file`, so only this app's own files are visible. */
@Singleton
class DriveRestRepository @Inject constructor(
    private val client: OkHttpClient,
    private val tokens: AccessTokenProvider,
    @DriveBaseUrl private val baseUrl: String,
) : DriveRepository {

    /** Drive's "308 Resume Incomplete" must reach this code rather than be followed as a redirect. */
    private val uploadClient: OkHttpClient =
        client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    override suspend fun findOrCreateFolder(name: String): String {
        val query = "name='${name.escapeForQuery()}' and mimeType='$FOLDER_MIME' " +
            "and 'root' in parents and trashed=false"
        return search(query) ?: createFolder(name)
    }

    private suspend fun createFolder(name: String): String {
        val body = json.encodeToString(FileMetadata(name = name, mimeType = FOLDER_MIME))
        val url = filesUrl().newBuilder().addQueryParameter("fields", "id").build()
        return authorized { token ->
            Request.Builder().url(url).bearer(token).post(body.toRequestBody(JSON_MEDIA)).build()
        }.useBody { parseId(it) }
    }

    override suspend fun folderExists(folderId: String): Boolean {
        val url = filesUrl().newBuilder()
            .addPathSegment(folderId)
            .addQueryParameter("fields", "id,trashed")
            .build()
        return try {
            authorized { token -> Request.Builder().url(url).bearer(token).get().build() }
                .useBody { !json.decodeFromString<FileRef>(it).trashed }
        } catch (e: DriveApiException) {
            if (e.kind == DriveErrorKind.NOT_FOUND) false else throw e
        }
    }

    override suspend fun findUploaded(parentFolderId: String, identityKey: String): String? = search(
        "appProperties has { key='$IDENTITY_PROPERTY' and value='${identityKey.escapeForQuery()}' } " +
            "and '${parentFolderId.escapeForQuery()}' in parents and trashed=false",
    )

    private suspend fun search(query: String): String? {
        val url = filesUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("pageSize", "1")
            .addQueryParameter("fields", "files(id)")
            .build()
        return authorized { token -> Request.Builder().url(url).bearer(token).get().build() }
            .useBody { json.decodeFromString<FileList>(it).files.firstOrNull()?.id }
    }

    override suspend fun upload(
        request: DriveUploadRequest,
        content: InputStream,
        onProgress: (bytesUploaded: Long) -> Unit,
    ): String {
        val sessionUrl = startResumableSession(request)
        val buffer = ByteArray(CHUNK_SIZE)
        var committed = 0L
        while (true) {
            val chunkLength = readChunk(content, buffer, minOf(CHUNK_SIZE.toLong(), request.size - committed).toInt())
            when (val outcome = sendChunk(sessionUrl, buffer, chunkLength, committed, request.size, onProgress)) {
                is ChunkOutcome.Finished -> {
                    onProgress(request.size)
                    return outcome.fileId
                }
                is ChunkOutcome.Continue -> {
                    committed = outcome.committed
                    onProgress(committed)
                }
            }
        }
    }

    private suspend fun startResumableSession(request: DriveUploadRequest): String {
        val metadata = FileMetadata(
            name = request.name,
            mimeType = request.mimeType,
            parents = listOf(request.parentFolderId),
            modifiedTime = request.modifiedAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() },
            appProperties = mapOf(IDENTITY_PROPERTY to request.identityKey),
        )
        val body = json.encodeToString(metadata)
        val url = "$baseUrl/upload/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "resumable")
            .addQueryParameter("fields", "id")
            .build()
        val response = authorized { token ->
            Request.Builder()
                .url(url)
                .bearer(token)
                .header("X-Upload-Content-Type", request.mimeType)
                .header("X-Upload-Content-Length", request.size.toString())
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
        }
        return response.use {
            it.header("Location") ?: throw IOException("Drive did not return an upload session")
        }
    }

    /**
     * Sends one buffered chunk, surviving brief network problems by asking the server how much
     * arrived. Only bytes the server confirmed count as uploaded, so a chunk that half-arrived is
     * resent from the right offset.
     */
    private suspend fun sendChunk(
        sessionUrl: String,
        buffer: ByteArray,
        length: Int,
        chunkStart: Long,
        totalSize: Long,
        onProgress: (Long) -> Unit,
    ): ChunkOutcome {
        var committed = chunkStart
        var failures = 0
        var tokenRefreshed = false
        val chunkEnd = chunkStart + length
        while (true) {
            currentCoroutineContext().ensureActive()
            val failure: Exception = try {
                val offsetInBuffer = (committed - chunkStart).toInt()
                val body = buffer.toRequestBody(BINARY_MEDIA, offsetInBuffer, length - offsetInBuffer)
                val range = "bytes $committed-${chunkEnd - 1}/$totalSize"
                val token = tokens.accessToken()
                val call = uploadClient.newCall(
                    Request.Builder()
                        .url(sessionUrl)
                        .bearer(token)
                        .header("Content-Range", range)
                        .put(body)
                        .build(),
                )
                call.await().use { response ->
                    when {
                        response.isSuccessful -> return ChunkOutcome.Finished(parseId(response.bodyString()))
                        response.code == RESUME_INCOMPLETE -> {
                            committed = response.committedBytes()
                            onProgress(committed)
                            if (committed >= chunkEnd) return ChunkOutcome.Continue(committed)
                            failures = 0
                            null
                        }
                        response.code == 401 && !tokenRefreshed -> {
                            tokenRefreshed = true
                            tokens.invalidate(token)
                            null
                        }
                        else -> parseDriveError(response.code, response.bodyString())
                    }
                } ?: continue
            } catch (e: IOException) {
                e
            }

            // Only temporary problems are retried here; anything else goes to the caller.
            if (failure is DriveApiException && failure.kind != DriveErrorKind.TRANSIENT) throw failure
            failures++
            if (failures > MAX_CHUNK_RETRIES) throw failure
            delay(CHUNK_RETRY_BASE_DELAY_MS shl (failures - 1))
            val confirmed = queryCommitted(sessionUrl, totalSize)
            if (confirmed is SessionState.Complete) return ChunkOutcome.Finished(confirmed.fileId)
            if (confirmed is SessionState.Partial) {
                committed = confirmed.committed.coerceIn(chunkStart, chunkEnd)
                if (committed >= chunkEnd) return ChunkOutcome.Continue(committed)
            }
        }
    }

    /** Asks the server for upload progress. Returns Unknown when that also fails. */
    private suspend fun queryCommitted(sessionUrl: String, totalSize: Long): SessionState = try {
        val token = tokens.accessToken()
        uploadClient.newCall(
            Request.Builder()
                .url(sessionUrl)
                .bearer(token)
                .header("Content-Range", "bytes */$totalSize")
                .put(ByteArray(0).toRequestBody(null))
                .build(),
        ).await().use { response ->
            when {
                response.isSuccessful -> SessionState.Complete(parseId(response.bodyString()))
                response.code == RESUME_INCOMPLETE -> SessionState.Partial(response.committedBytes())
                else -> SessionState.Unknown
            }
        }
    } catch (e: IOException) {
        SessionState.Unknown
    }

    private fun Response.committedBytes(): Long =
        // Range looks like "bytes=0-1048575". No header means nothing has been stored yet.
        header("Range")?.substringAfterLast('-')?.toLongOrNull()?.plus(1) ?: 0L

    /** Runs a request, refreshing the token once on 401. Throws for any non-success status. */
    private suspend fun authorized(build: (token: String) -> Request): Response {
        val token = tokens.accessToken()
        val first = client.newCall(build(token)).await()
        if (first.code != 401) return first.requireSuccess()
        first.close()
        tokens.invalidate(token)
        return client.newCall(build(tokens.accessToken())).await().requireSuccess()
    }

    private suspend fun Response.requireSuccess(): Response {
        if (isSuccessful) return this
        val body = use { it.bodyString() }
        throw parseDriveError(code, body)
    }

    private suspend fun <T> Response.useBody(block: (String) -> T): T = use { block(it.bodyString()) }

    private suspend fun Response.bodyString(): String =
        withContext(Dispatchers.IO) { body.string() }

    private fun parseId(body: String): String = json.decodeFromString<FileRef>(body).id

    private fun filesUrl(): HttpUrl = "$baseUrl/drive/v3/files".toHttpUrl()

    private fun Request.Builder.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun String.escapeForQuery(): String = replace("\\", "\\\\").replace("'", "\\'")

    /** Fills [buffer] with exactly [length] bytes; a short file means it changed during upload. */
    private suspend fun readChunk(content: InputStream, buffer: ByteArray, length: Int): Int =
        withContext(Dispatchers.IO) {
            var read = 0
            while (read < length) {
                ensureActive()
                val count = content.read(buffer, read, length - read)
                if (count < 0) throw IOException("File ended earlier than expected")
                read += count
            }
            read
        }

    private sealed interface ChunkOutcome {
        data class Continue(val committed: Long) : ChunkOutcome
        data class Finished(val fileId: String) : ChunkOutcome
    }

    private sealed interface SessionState {
        data class Partial(val committed: Long) : SessionState
        data class Complete(val fileId: String) : SessionState
        data object Unknown : SessionState
    }

    @Serializable
    private data class FileMetadata(
        val name: String,
        val mimeType: String,
        val parents: List<String>? = null,
        val modifiedTime: String? = null,
        val appProperties: Map<String, String>? = null,
    )

    @Serializable
    private data class FileRef(val id: String, val trashed: Boolean = false)

    @Serializable
    private data class FileList(val files: List<FileRef> = emptyList())

    private companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val IDENTITY_PROPERTY = "funActivityIdentity"
        const val RESUME_INCOMPLETE = 308

        /** Must be a multiple of 256 KiB for all chunks but the last. */
        const val CHUNK_SIZE = 8 * 256 * 1024
        const val MAX_CHUNK_RETRIES = 3
        const val CHUNK_RETRY_BASE_DELAY_MS = 1_000L

        val JSON_MEDIA = "application/json; charset=UTF-8".toMediaType()
        val BINARY_MEDIA = "application/octet-stream".toMediaType()
    }
}

/** Suspends until the call completes and cancels it if the coroutine is cancelled. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        },
    )
}
