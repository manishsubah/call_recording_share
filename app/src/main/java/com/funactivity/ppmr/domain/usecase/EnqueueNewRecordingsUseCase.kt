package com.funactivity.ppmr.domain.usecase

import com.funactivity.ppmr.domain.model.FolderListing
import com.funactivity.ppmr.domain.model.RecordingFile
import com.funactivity.ppmr.domain.repository.ContentHasher
import com.funactivity.ppmr.domain.repository.RecordingFolderRepository
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.domain.sync.CandidateDecision
import com.funactivity.ppmr.domain.sync.CandidateRules
import com.funactivity.ppmr.domain.sync.FileIdentity
import javax.inject.Inject
import kotlinx.coroutines.flow.first

sealed interface ScanResult {
    /** [nextReadyAt] is when a file that was too new can be checked again, if there is one. */
    data class Completed(val enqueued: Int, val nextReadyAt: Long?) : ScanResult
    data object NothingToScan : ScanResult
    data object FolderAccessLost : ScanResult
    data class Failed(val reason: String) : ScanResult
}

/** Compares the folder with the upload records and queues whatever is new. */
class EnqueueNewRecordingsUseCase @Inject constructor(
    private val folders: RecordingFolderRepository,
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
    private val hasher: ContentHasher,
    private val messages: SyncMessages,
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): ScanResult {
        val state = settings.appState.first()
        val treeUri = state.sourceTreeUri
        if (treeUri == null || state.accountEmail == null) return ScanResult.NothingToScan

        val config = settings.settings.first()
        val listing = folders.listRecordings(treeUri, config.includeSubfolders)
        val files = when (listing) {
            is FolderListing.Success -> listing.files
            FolderListing.PermissionLost -> return ScanResult.FolderAccessLost
            is FolderListing.Failed -> return ScanResult.Failed(listing.reason)
        }

        val known = sync.allIdentityKeys()
        var enqueued = 0
        var nextReadyAt: Long? = null
        for (file in files) {
            when (val decision = CandidateRules.evaluate(file, now, config.minFileAgeSeconds)) {
                CandidateDecision.Ignored -> Unit
                is CandidateDecision.TooNew ->
                    nextReadyAt = minOf(nextReadyAt ?: decision.readyAt, decision.readyAt)
                CandidateDecision.Eligible -> {
                    val identity = FileIdentity.keyOf(file.displayName, file.size, file.lastModified)
                    if (identity !in known && consider(file, identity)) enqueued++
                }
            }
        }
        return ScanResult.Completed(enqueued, nextReadyAt)
    }

    /** Returns true if the file was queued. A renamed copy of known content is not re-uploaded. */
    private suspend fun consider(file: RecordingFile, identity: String): Boolean {
        val hash = hasher.hashOf(file)
        if (hash != null) {
            val existing = sync.driveFileIdForContent(hash)
            if (existing != null) {
                sync.recordAsDuplicate(file, identity, hash, existing, messages.duplicateContent())
                return false
            }
            // A twin is on its way up; the next scan will find it uploaded and link this file.
            if (sync.hasPendingContent(hash)) return false
        }
        sync.enqueue(file, identity, hash)
        return true
    }
}

/** User-visible text that the sync engine stores with history rows. */
interface SyncMessages {
    fun duplicateContent(): String
    fun alreadyInDrive(): String
    fun fileMissing(): String
    fun folderAccessLost(): String
    fun quotaExceeded(): String
    fun retrying(attempt: Int, maxAttempts: Int, reason: String): String
    fun gaveUp(reason: String): String
    fun networkProblem(): String
}
