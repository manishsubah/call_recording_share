package com.funactivity.ppmr.domain

import com.funactivity.ppmr.domain.model.AppSettings
import com.funactivity.ppmr.domain.model.AppState
import com.funactivity.ppmr.domain.model.FolderListing
import com.funactivity.ppmr.domain.model.RecordingFile
import com.funactivity.ppmr.domain.model.SourceFolder
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
    private var state = AppState(accountEmail = "me@example.com", sourceFolders = listOf(SourceFolder(TREE, "Tree")))

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
    fun `every chosen folder is scanned`() = runTest {
        useTwoFolders()
        folder.listingsByTree["content://a"] = FolderListing.Success(listOf(recording("a.m4a")))
        folder.listingsByTree["content://b"] = FolderListing.Success(listOf(recording("b.opus")))
        assertEquals(ScanResult.Completed(2, null), useCase()(NOW))
    }

    @Test
    fun `a lost folder does not hold up the others but is reported`() = runTest {
        useTwoFolders()
        folder.listingsByTree["content://a"] = FolderListing.PermissionLost
        folder.listingsByTree["content://b"] = FolderListing.Success(listOf(recording("b.opus")))
        assertEquals(ScanResult.Completed(1, null, someFoldersLost = true), useCase()(NOW))
    }

    @Test
    fun `losing every folder is reported as lost access`() = runTest {
        useTwoFolders()
        folder.listingsByTree["content://a"] = FolderListing.PermissionLost
        folder.listingsByTree["content://b"] = FolderListing.PermissionLost
        assertEquals(ScanResult.FolderAccessLost, useCase()(NOW))
    }

    @Test
    fun `a file reached through overlapping folders queues once`() = runTest {
        useTwoFolders()
        // No content hash, as for a large file, so only the identity can catch the overlap.
        folder.listingsByTree["content://a"] = FolderListing.Success(listOf(recording("a.m4a")))
        folder.listingsByTree["content://b"] = FolderListing.Success(listOf(recording("a.m4a")))
        assertEquals(ScanResult.Completed(1, null), useCase()(NOW))
    }

    private fun useTwoFolders() {
        state = state.copy(
            sourceFolders = listOf(SourceFolder("content://a", "A"), SourceFolder("content://b", "B")),
        )
    }

    @Test
    fun `nothing is scanned without an account or a folder`() = runTest {
        state = AppState(sourceFolders = listOf(SourceFolder(TREE, "Tree")))
        assertEquals(ScanResult.NothingToScan, useCase()(NOW))
        state = AppState(accountEmail = "me@example.com")
        assertEquals(ScanResult.NothingToScan, useCase()(NOW))
    }
}

private const val TREE = "content://tree"
