package com.funactivity.ppmr.data

import com.funactivity.ppmr.data.remote.DriveRestRepository
import com.funactivity.ppmr.data.remote.parseDriveError
import com.funactivity.ppmr.domain.model.DriveApiException
import com.funactivity.ppmr.domain.model.DriveErrorKind
import com.funactivity.ppmr.domain.model.DriveUploadRequest
import com.funactivity.ppmr.domain.repository.AccessTokenProvider
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val MIB = 1024 * 1024
private const val CHUNK = 2 * MIB

class DriveRestRepositoryTest {
    private val server = MockWebServer()
    private val tokens = FakeTokens()
    private lateinit var drive: DriveRestRepository

    @Before
    fun setUp() {
        server.start()
        drive = DriveRestRepository(OkHttpClient(), tokens, server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() = server.close()

    private fun respond(code: Int, body: String = "", vararg headers: Pair<String, String>) {
        val builder = MockResponse.Builder().code(code).body(body)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        server.enqueue(builder.build())
    }

    private fun request(size: Long) = DriveUploadRequest(
        name = "call's.m4a",
        mimeType = "audio/mp4",
        size = size,
        modifiedAt = 1_700_000_000_000,
        parentFolderId = "folder1",
        identityKey = "identity1",
    )

    private fun startSession() = respond(200, "", "Location" to server.url("/session").toString())

    @Test
    fun `reuses an existing folder`() = runTest {
        respond(200, """{"files":[{"id":"existing"}]}""")
        assertEquals("existing", drive.findOrCreateFolder("Call Recordings"))
        val query = server.takeRequest().url.queryParameter("q").orEmpty()
        assertTrue(query, "name='Call Recordings'" in query)
        assertTrue(query, "trashed=false" in query)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `creates the folder when there is none`() = runTest {
        respond(200, """{"files":[]}""")
        respond(200, """{"id":"new"}""")
        assertEquals("new", drive.findOrCreateFolder("Call Recordings"))
        server.takeRequest()
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        val metadata = Json.parseToJsonElement(create.body!!.utf8()).jsonObject
        assertEquals("Call Recordings", metadata["name"]!!.jsonPrimitive.content)
        assertEquals("application/vnd.google-apps.folder", metadata["mimeType"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a deleted or trashed folder does not count as existing`() = runTest {
        respond(404, """{"error":{"code":404,"message":"File not found"}}""")
        assertFalse(drive.folderExists("gone"))
        respond(200, """{"id":"f","trashed":true}""")
        assertFalse(drive.folderExists("trashed"))
        respond(200, """{"id":"f","trashed":false}""")
        assertTrue(drive.folderExists("ok"))
    }

    @Test
    fun `small file uploads in one chunk with the right metadata`() = runTest {
        startSession()
        respond(200, """{"id":"file1"}""")
        val content = ByteArray(1000) { it.toByte() }

        val id = drive.upload(request(1000), ByteArrayInputStream(content)) { }

        assertEquals("file1", id)
        val start = server.takeRequest()
        assertEquals("resumable", start.url.queryParameter("uploadType"))
        assertEquals("Bearer token-1", start.headers["Authorization"])
        assertEquals("audio/mp4", start.headers["X-Upload-Content-Type"])
        assertEquals("1000", start.headers["X-Upload-Content-Length"])
        val metadata = Json.parseToJsonElement(start.body!!.utf8()).jsonObject
        assertEquals("call's.m4a", metadata["name"]!!.jsonPrimitive.content)
        assertEquals("folder1", metadata["parents"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("2023-11-14T22:13:20Z", metadata["modifiedTime"]!!.jsonPrimitive.content)
        assertEquals(
            "identity1",
            metadata["appProperties"]!!.jsonObject["funActivityIdentity"]!!.jsonPrimitive.content,
        )
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("bytes 0-999/1000", put.headers["Content-Range"])
        assertEquals(content.toList(), put.body!!.toByteArray().toList())
    }

    @Test
    fun `large file is sent in chunks and progress follows confirmed bytes`() = runTest {
        val size = 3L * MIB
        startSession()
        respond(308, "", "Range" to "bytes=0-${CHUNK - 1}")
        respond(200, """{"id":"file2"}""")
        val progress = mutableListOf<Long>()

        val id = drive.upload(request(size), ByteArrayInputStream(ByteArray(size.toInt()))) { progress += it }

        assertEquals("file2", id)
        server.takeRequest()
        assertEquals("bytes 0-${CHUNK - 1}/$size", server.takeRequest().headers["Content-Range"])
        assertEquals("bytes $CHUNK-${size - 1}/$size", server.takeRequest().headers["Content-Range"])
        assertEquals(size, progress.last())
    }

    @Test
    fun `a partly stored chunk is resent from the confirmed offset`() = runTest {
        val size = CHUNK.toLong()
        val content = ByteArray(CHUNK) { (it % 251).toByte() }
        startSession()
        respond(308, "", "Range" to "bytes=0-${MIB - 1}")
        respond(200, """{"id":"file3"}""")

        drive.upload(request(size), ByteArrayInputStream(content)) { }

        server.takeRequest()
        server.takeRequest()
        val resend = server.takeRequest()
        assertEquals("bytes $MIB-${size - 1}/$size", resend.headers["Content-Range"])
        assertEquals(
            content.copyOfRange(MIB, CHUNK).toList(),
            resend.body!!.toByteArray().toList(),
        )
    }

    @Test
    fun `a server error asks for progress and then resumes`() = runTest {
        startSession()
        respond(503, "")
        respond(308, "")
        respond(200, """{"id":"file4"}""")

        assertEquals("file4", drive.upload(request(1000), ByteArrayInputStream(ByteArray(1000))) { })

        server.takeRequest()
        assertEquals("bytes 0-999/1000", server.takeRequest().headers["Content-Range"])
        assertEquals("bytes */1000", server.takeRequest().headers["Content-Range"])
        assertEquals("bytes 0-999/1000", server.takeRequest().headers["Content-Range"])
    }

    @Test
    fun `finishes without resending when the status check shows the upload completed`() = runTest {
        startSession()
        respond(503, "")
        respond(200, """{"id":"file5"}""")

        assertEquals("file5", drive.upload(request(1000), ByteArrayInputStream(ByteArray(1000))) { })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `an expired token is refreshed once and the request repeated`() = runTest {
        respond(401, "")
        respond(200, """{"files":[]}""")

        assertEquals(null, drive.findUploaded("folder1", "identity1"))

        assertEquals("Bearer token-1", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer token-2", server.takeRequest().headers["Authorization"])
        assertEquals(listOf("token-1"), tokens.invalidated)
    }

    @Test
    fun `identity search looks in the destination folder`() = runTest {
        respond(200, """{"files":[{"id":"already"}]}""")
        assertEquals("already", drive.findUploaded("folder1", "identity1"))
        val query = server.takeRequest().url.queryParameter("q").orEmpty()
        assertTrue(query, "key='funActivityIdentity' and value='identity1'" in query)
        assertTrue(query, "'folder1' in parents" in query)
    }

    @Test
    fun `a full Drive surfaces as a quota error`() = runTest {
        respond(403, QUOTA_BODY)
        val error = assertSuspendThrows<DriveApiException> {
            drive.upload(request(10), ByteArrayInputStream(ByteArray(10))) { }
        }
        assertEquals(DriveErrorKind.QUOTA_EXCEEDED, error.kind)
    }

    @Test
    fun `a file shorter than reported fails instead of uploading short`() = runTest {
        startSession()
        assertSuspendThrows<IOException> {
            drive.upload(request(1000), ByteArrayInputStream(ByteArray(10))) { }
        }
    }

    @Test
    fun `error classification follows the reason, not only the status`() {
        fun kindOf(code: Int, reason: String? = null): DriveErrorKind {
            val body = reason?.let { """{"error":{"message":"m","errors":[{"reason":"$it"}]}}""" }
            return parseDriveError(code, body).kind
        }
        assertEquals(DriveErrorKind.AUTH, kindOf(401))
        assertEquals(DriveErrorKind.AUTH, kindOf(403, "insufficientPermissions"))
        assertEquals(DriveErrorKind.QUOTA_EXCEEDED, kindOf(403, "storageQuotaExceeded"))
        assertEquals(DriveErrorKind.TRANSIENT, kindOf(403, "rateLimitExceeded"))
        assertEquals(DriveErrorKind.TRANSIENT, kindOf(403, "userRateLimitExceeded"))
        assertEquals(DriveErrorKind.TRANSIENT, kindOf(429))
        assertEquals(DriveErrorKind.TRANSIENT, kindOf(500))
        assertEquals(DriveErrorKind.NOT_FOUND, kindOf(404))
        assertEquals(DriveErrorKind.PERMANENT, kindOf(403, "accessNotConfigured"))
        assertEquals(DriveErrorKind.PERMANENT, kindOf(400))
    }

    @Test
    fun `an unreadable error body is still classified`() {
        assertEquals(DriveErrorKind.TRANSIENT, parseDriveError(502, "<html>Bad Gateway</html>").kind)
    }

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(block: () -> Unit): T {
        val thrown = try {
            block()
            null
        } catch (e: Throwable) {
            e
        }
        return assertThrows(T::class.java) { thrown?.let { throw it } }
    }

    private class FakeTokens : AccessTokenProvider {
        private var generation = 1
        val invalidated = mutableListOf<String>()
        override suspend fun accessToken() = "token-$generation"
        override suspend fun invalidate(token: String) {
            invalidated += token
            generation++
        }
    }

    private companion object {
        const val QUOTA_BODY =
            """{"error":{"code":403,"message":"The user's Drive storage quota has been exceeded.",""" +
                """"errors":[{"reason":"storageQuotaExceeded"}]}}"""
    }
}
