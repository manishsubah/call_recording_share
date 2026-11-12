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
    /**
     * [nextReadyAt] is when a file that was too new can be checked again, if there is one.
     * [someFoldersLost] means the folders still readable were scanned but others were not.
     */
    data class Completed(
        val enqueued: Int,
        val nextReadyAt: Long?,
        val someFoldersLost: Boolean = false,
    ) : ScanResult
    data object NothingToScan : ScanResult
    data object FolderAccessLost : ScanResult
    data class Failed(val reason: String) : ScanResult
}

/** Compares the chosen folders with the upload records and queues whatever is new. */
class EnqueueNewRecordingsUseCase @Inject constructor(
    private val folders: RecordingFolderRepository,
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
    private val hasher: ContentHasher,
    private val messages: SyncMessages,
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): ScanResult {
        val state = settings.appState.first()
        if (state.sourceFolders.isEmpty() || state.accountEmail == null) return ScanResult.NothingToScan

        val config = settings.settings.first()
        val listings = state.sourceFolders.map { folders.listRecordings(it.treeUri, config.includeSubfolders) }
        val files = listings.filterIsInstance<FolderListing.Success>().flatMap { it.files }
        val lost = listings.count { it is FolderListing.PermissionLost }
        // One unreadable folder must not hold up the others.
        if (listings.none { it is FolderListing.Success }) {
            if (lost > 0) return ScanResult.FolderAccessLost
            return ScanResult.Failed(listings.filterIsInstance<FolderListing.Failed>().first().reason)
        }

        // Tracks this scan too, so a file reached through two overlapping folders queues once.
        val known = sync.allIdentityKeys().toMutableSet()
        var enqueued = 0
        var nextReadyAt: Long? = null
        for (file in files) {
            when (val decision = CandidateRules.evaluate(file, now, config.minFileAgeSeconds)) {
                CandidateDecision.Ignored -> Unit
                is CandidateDecision.TooNew ->
                    nextReadyAt = minOf(nextReadyAt ?: decision.readyAt, decision.readyAt)
                CandidateDecision.Eligible -> {
                    val identity = FileIdentity.keyOf(file.displayName, file.size, file.lastModified)
                    if (known.add(identity) && consider(file, identity)) enqueued++
                }
            }
        }
        return ScanResult.Completed(enqueued, nextReadyAt, someFoldersLost = lost > 0)
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
