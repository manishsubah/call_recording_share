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
import com.funactivity.ppmr.domain.usecase.SyncMessages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal class FakeFolder : RecordingFolderRepository {
    var files: List<RecordingFile> = emptyList()
    var listing: FolderListing? = null
    val listingsByTree = mutableMapOf<String, FolderListing>()

    override suspend fun listRecordings(treeUri: String, includeSubfolders: Boolean) =
        listingsByTree[treeUri] ?: listing ?: FolderListing.Success(files)

    override fun takePermission(treeUri: String) = true
    override fun releasePermission(treeUri: String) = Unit
    override fun hasPermission(treeUri: String) = true
    override fun displayNameOf(treeUri: String) = treeUri
    override suspend fun stat(documentUri: String) = error("not used")
    override fun open(documentUri: String) = error("not used")
}

internal class FakeSync : SyncRepository {
    private class Row(val name: String, val uri: String, val hash: String?, var uploaded: Boolean)

    private val rows = mutableMapOf<String, Row>()
    val duplicates = mutableListOf<String>()
    val queued: List<Any> get() = rows.values.filter { !it.uploaded }

    fun markAllUploaded() = rows.values.forEach { it.uploaded = true }

    override suspend fun allIdentityKeys() = rows.keys.toSet()
    override suspend fun unfinishedDocumentUris() = rows.values.filter { !it.uploaded }.map { it.uri }.toSet()
    override suspend fun driveFileIdForContent(contentHash: String) =
        rows.values.firstOrNull { it.hash == contentHash && it.uploaded }?.let { "drive-id" }
    override suspend fun hasPendingContent(contentHash: String) =
        rows.values.any { it.hash == contentHash && !it.uploaded }
    override suspend fun enqueue(file: RecordingFile, identityKey: String, contentHash: String?) {
        rows[identityKey] = Row(file.displayName, file.documentUri, contentHash, uploaded = false)
    }
    override suspend fun recordAsDuplicate(
        file: RecordingFile,
        identityKey: String,
        contentHash: String?,
        driveFileId: String,
        message: String,
    ) {
        rows[identityKey] = Row(file.displayName, file.documentUri, contentHash, uploaded = true)
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

internal class FakeSettings(state: AppState) : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings())
    override val appState = MutableStateFlow(state)
    override suspend fun setWifiOnly(enabled: Boolean) = Unit
    override suspend fun setAutoSync(enabled: Boolean) = Unit
    override suspend fun setIncludeSubfolders(enabled: Boolean) = Unit
    override suspend fun setSuccessNotifications(enabled: Boolean) = Unit
    override suspend fun setMinFileAgeSeconds(seconds: Int) = Unit
    override suspend fun setOnboardingDone() = Unit
    override suspend fun addSourceFolder(folder: SourceFolder) = Unit
    override suspend fun removeSourceFolder(treeUri: String) = Unit
    override suspend fun setDriveFolderId(id: String?) = Unit
    override suspend fun setLastSuccessfulSync(timestamp: Long) = Unit
    override suspend fun setAuthRequired(required: Boolean) = Unit
    override suspend fun setFolderLostNotified(notified: Boolean) = Unit
    override suspend fun setAccount(email: String): String? = null
    override suspend fun clearAccount() = Unit
}

internal object FakeMessages : SyncMessages {
    override fun duplicateContent() = "duplicate"
    override fun alreadyInDrive() = "already"
    override fun fileMissing() = "missing"
    override fun folderAccessLost() = "lost"
    override fun quotaExceeded() = "quota"
    override fun retrying(attempt: Int, maxAttempts: Int, reason: String) = "retry"
    override fun gaveUp(reason: String) = "gave up"
    override fun networkProblem() = "network"
}
