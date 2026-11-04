package com.funactivity.ppmr.domain

import com.funactivity.ppmr.domain.model.AppSettings
import com.funactivity.ppmr.domain.model.AppState
import com.funactivity.ppmr.domain.model.FolderListing
import com.funactivity.ppmr.domain.model.RecordingFile
import com.funactivity.ppmr.domain.model.SyncItem
import com.funactivity.ppmr.domain.model.TrackedFile
import com.funactivity.ppmr.domain.repository.ContentHasher
import com.funactivity.ppmr.domain.repository.RecordingFolderRepository
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.domain.usecase.EnqueueNewRecordingsUseCase
import com.funactivity.ppmr.domain.usecase.ScanResult
import com.funactivity.ppmr.domain.usecase.SyncMessages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val NOW = 1_000_000L

class EnqueueNewRecordingsTest {
    private val folder = FakeFolder()
    private val sync = FakeSync()
    private val hashes = mutableMapOf<String, String?>()
    private var state = AppState(accountEmail = "me@example.com", sourceTreeUri = "content://tree")

    private fun useCase() = EnqueueNewRecordingsUseCase(
        folders = folder,
        settings = FakeSettings(state),
        sync = sync,
        hasher = object : ContentHasher {
            override suspend fun hashOf(file: RecordingFile) = hashes[file.displayName]
        },
        messages = FakeMessages,
    )

    private fun recording(name: String, size: Long = 100, modified: Long = 1_000) =
        RecordingFile("content://doc/$name", name, size, modified, "audio/mp4")

    @Test
    fun `queues a new file once across repeated scans`() = runTest {
        folder.files = listOf(recording("a.m4a"))
        assertEquals(ScanResult.Completed(1, null), useCase()(NOW))
        assertEquals(ScanResult.Completed(0, null), useCase()(NOW))
        assertEquals(1, sync.queued.size)
    }

    @Test
    fun `an uploaded file is not queued again`() = runTest {
        folder.files = listOf(recording("a.m4a"))
        useCase()(NOW)
        sync.markAllUploaded()
        assertEquals(ScanResult.Completed(0, null), useCase()(NOW))
    }

    @Test
    fun `a renamed copy of uploaded content is linked instead of uploaded`() = runTest {
        hashes["a.m4a"] = "same"
        hashes["renamed.m4a"] = "same"
        folder.files = listOf(recording("a.m4a"))
        useCase()(NOW)
        sync.markAllUploaded()

        folder.files = listOf(recording("a.m4a"), recording("renamed.m4a"))
        assertEquals(ScanResult.Completed(0, null), useCase()(NOW))
        assertEquals(listOf("renamed.m4a"), sync.duplicates)
    }

    @Test
    fun `two identical new files upload once`() = runTest {
        hashes["a.m4a"] = "same"
        hashes["b.m4a"] = "same"
        folder.files = listOf(recording("a.m4a"), recording("b.m4a"))
        assertEquals(ScanResult.Completed(1, null), useCase()(NOW))
    }

    @Test
    fun `a file that is too new is left for later and reports when it is ready`() = runTest {
        folder.files = listOf(recording("a.m4a", modified = NOW - 5_000))
        val expectedReady = NOW - 5_000 + AppSettings().minFileAgeSeconds * 1_000L
        assertEquals(ScanResult.Completed(0, expectedReady), useCase()(NOW))
        assertEquals(0, sync.queued.size)
    }

    @Test
    fun `a file that grew counts as a new identity`() = runTest {
        folder.files = listOf(recording("a.m4a", size = 100))
        useCase()(NOW)
        folder.files = listOf(recording("a.m4a", size = 200))
        assertEquals(ScanResult.Completed(1, null), useCase()(NOW))
    }

    @Test
    fun `lost folder access and read failures are reported, not thrown`() = runTest {
        folder.listing = FolderListing.PermissionLost
        assertEquals(ScanResult.FolderAccessLost, useCase()(NOW))
        folder.listing = FolderListing.Failed("card removed")
        assertEquals(ScanResult.Failed("card removed"), useCase()(NOW))
    }

    @Test
    fun `nothing is scanned without an account or a folder`() = runTest {
        state = AppState(sourceTreeUri = "content://tree")
        assertEquals(ScanResult.NothingToScan, useCase()(NOW))
        state = AppState(accountEmail = "me@example.com")
        assertEquals(ScanResult.NothingToScan, useCase()(NOW))
    }
}

private class FakeFolder : RecordingFolderRepository {
    var files: List<RecordingFile> = emptyList()
    var listing: FolderListing? = null

    override suspend fun listRecordings(treeUri: String, includeSubfolders: Boolean) =
        listing ?: FolderListing.Success(files)

    override fun takePermission(treeUri: String) = true
    override fun releasePermission(treeUri: String) = Unit
    override fun hasPermission(treeUri: String) = true
    override fun displayNameOf(treeUri: String) = treeUri
    override suspend fun stat(documentUri: String) = error("not used")
    override fun open(documentUri: String) = error("not used")
}

private class FakeSync : SyncRepository {
    private class Row(val name: String, val hash: String?, var uploaded: Boolean)

    private val rows = mutableMapOf<String, Row>()
    val duplicates = mutableListOf<String>()
    val queued: List<Any> get() = rows.values.filter { !it.uploaded }

    fun markAllUploaded() = rows.values.forEach { it.uploaded = true }

    override suspend fun allIdentityKeys() = rows.keys.toSet()
    override suspend fun driveFileIdForContent(contentHash: String) =
        rows.values.firstOrNull { it.hash == contentHash && it.uploaded }?.let { "drive-id" }
    override suspend fun hasPendingContent(contentHash: String) =
        rows.values.any { it.hash == contentHash && !it.uploaded }
    override suspend fun enqueue(file: RecordingFile, identityKey: String, contentHash: String?) {
        rows[identityKey] = Row(file.displayName, contentHash, uploaded = false)
    }
    override suspend fun recordAsDuplicate(
        file: RecordingFile,
        identityKey: String,
        contentHash: String?,
        driveFileId: String,
        message: String,
    ) {
        rows[identityKey] = Row(file.displayName, contentHash, uploaded = true)
        duplicates += file.displayName
    }

    override fun observeRecentActivity(limit: Int): Flow<List<SyncItem>> = flowOf(emptyList())
    override fun observePendingCount(): Flow<Int> = flowOf(0)
    override fun observeFailedCount(): Flow<Int> = flowOf(0)
    override suspend fun nextDue(now: Long): TrackedFile? = null
    override suspend fun earliestPendingAttempt(): Long? = null
    override suspend fun markUploading(identityKey: String) = Unit
    override suspend fun markUploaded(identityKey: String, driveFileId: String, message: String?) = Unit
    override suspend fun scheduleRetry(identityKey: String, attempts: Int, nextAttemptAt: Long, message: String) = Unit
    override suspend fun markFailed(identityKey: String, attempts: Int, message: String) = Unit
    override suspend fun resetInterrupted() = Unit
    override suspend fun retry(identityKey: String) = Unit
    override suspend fun retryAllNow() = Unit
    override suspend fun forget(identityKey: String) = Unit
    override suspend fun clearUnfinished() = Unit
    override suspend fun clearHistory() = Unit
    override suspend fun clearAll() = Unit
}

private class FakeSettings(state: AppState) : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings())
    override val appState = MutableStateFlow(state)
    override suspend fun setWifiOnly(enabled: Boolean) = Unit
    override suspend fun setAutoSync(enabled: Boolean) = Unit
    override suspend fun setIncludeSubfolders(enabled: Boolean) = Unit
    override suspend fun setSuccessNotifications(enabled: Boolean) = Unit
    override suspend fun setMinFileAgeSeconds(seconds: Int) = Unit
    override suspend fun setOnboardingDone() = Unit
    override suspend fun setSourceFolder(treeUri: String, displayName: String) = Unit
    override suspend fun setDriveFolderId(id: String?) = Unit
    override suspend fun setLastSuccessfulSync(timestamp: Long) = Unit
    override suspend fun setAuthRequired(required: Boolean) = Unit
    override suspend fun setFolderLostNotified(notified: Boolean) = Unit
    override suspend fun setAccount(email: String): String? = null
    override suspend fun clearAccount() = Unit
}

private object FakeMessages : SyncMessages {
    override fun duplicateContent() = "duplicate"
    override fun alreadyInDrive() = "already"
    override fun fileMissing() = "missing"
    override fun folderAccessLost() = "lost"
    override fun quotaExceeded() = "quota"
    override fun retrying(attempt: Int, maxAttempts: Int, reason: String) = "retry"
    override fun gaveUp(reason: String) = "gave up"
    override fun networkProblem() = "network"
}
