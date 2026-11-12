package com.funactivity.ppmr.domain

import com.funactivity.ppmr.domain.model.AppState
import com.funactivity.ppmr.domain.model.RecordingFile
import com.funactivity.ppmr.domain.repository.ContentHasher
import com.funactivity.ppmr.domain.repository.SharedRecordingStore
import com.funactivity.ppmr.domain.repository.SyncScheduler
import com.funactivity.ppmr.domain.usecase.CleanUpSharedCopiesUseCase
import com.funactivity.ppmr.domain.usecase.ImportSharedRecordingsUseCase
import com.funactivity.ppmr.domain.usecase.ShareResult
import com.funactivity.ppmr.domain.usecase.SharedItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSharedRecordingsTest {
    private val store = FakeStore()
    private val sync = FakeSync()
    private val scheduler = FakeScheduler()
    private val hashes = mutableMapOf<String, String?>()
    private var state = AppState(accountEmail = "me@example.com")

    private fun useCase() = ImportSharedRecordingsUseCase(
        store = store,
        settings = FakeSettings(state),
        sync = sync,
        hasher = object : ContentHasher {
            override suspend fun hashOf(file: RecordingFile) = hashes[file.displayName]
        },
        messages = FakeMessages,
        scheduler = scheduler,
    )

    private fun share(vararg names: String) = names.map { SharedItem("content://phone/$it", "audio/mp4") }

    @Test
    fun `a shared recording is copied, queued and uploads start`() = runTest {
        assertEquals(ShareResult.Done(1, 0, 0), useCase()(share("call.m4a")))
        assertEquals(1, sync.queued.size)
        assertEquals(setOf("file:///copies/call.m4a"), store.files)
        assertEquals(1, scheduler.uploadStarts)
    }

    @Test
    fun `sharing the same recording twice queues it once and drops the second copy`() = runTest {
        useCase()(share("call.m4a"))
        store.nextCopySuffix = "-2"
        assertEquals(ShareResult.Done(0, 1, 0), useCase()(share("call.m4a")))
        assertEquals(1, sync.queued.size)
        assertEquals(setOf("file:///copies/call.m4a"), store.files)
    }

    @Test
    fun `content already uploaded is linked, not uploaded again`() = runTest {
        hashes["call.m4a"] = "h"
        hashes["renamed.m4a"] = "h"
        useCase()(share("call.m4a"))
        sync.markAllUploaded()
        assertEquals(ShareResult.Done(0, 1, 0), useCase()(share("renamed.m4a")))
        assertEquals(listOf("renamed.m4a"), sync.duplicates)
        assertTrue("file:///copies/renamed.m4a" !in store.files)
    }

    @Test
    fun `an unreadable or empty file is reported and leaves nothing behind`() = runTest {
        store.unreadable += "bad.m4a"
        store.emptyFiles += "empty.m4a"
        assertEquals(ShareResult.Done(0, 0, 2), useCase()(share("bad.m4a", "empty.m4a")))
        assertEquals(emptySet<String>(), store.files)
        assertEquals(0, scheduler.uploadStarts)
    }

    @Test
    fun `nothing is copied before sign-in`() = runTest {
        state = AppState()
        assertEquals(ShareResult.NotSignedIn, useCase()(share("call.m4a")))
        assertEquals(emptySet<String>(), store.files)
    }

    @Test
    fun `clean-up keeps queued copies and deletes uploaded ones`() = runTest {
        useCase()(share("a.m4a"))
        sync.markAllUploaded()
        useCase()(share("b.m4a"))
        CleanUpSharedCopiesUseCase(store, sync)()
        assertEquals(setOf("file:///copies/b.m4a"), store.files)
    }
}

private class FakeStore : SharedRecordingStore {
    val files = mutableSetOf<String>()
    val unreadable = mutableSetOf<String>()
    val emptyFiles = mutableSetOf<String>()
    var nextCopySuffix = ""

    override suspend fun importCopy(sourceUri: String, mimeType: String?): RecordingFile? {
        val name = sourceUri.substringAfterLast('/')
        if (name in unreadable) return null
        val uri = "file:///copies/$name$nextCopySuffix"
        files += uri
        return RecordingFile(uri, name, if (name in emptyFiles) 0 else 100, 1_000, "audio/mp4")
    }

    override suspend fun delete(documentUri: String) {
        files -= documentUri
    }

    override suspend fun deleteAllExcept(keep: Set<String>) {
        files.retainAll(keep)
    }

    override suspend fun <T> locked(block: suspend () -> T): T = block()
}

private class FakeScheduler : SyncScheduler {
    var uploadStarts = 0
    override fun setAutoSyncEnabled(enabled: Boolean) = Unit
    override fun requestScan(manual: Boolean) = Unit
    override fun scheduleScan(delayMillis: Long, manual: Boolean) = Unit
    override suspend fun startUploads(wifiOnly: Boolean, expedited: Boolean) {
        uploadStarts++
    }
    override fun cancelAll() = Unit
}
