package com.funactivity.ppmr.domain.usecase

import com.funactivity.ppmr.domain.repository.ContentHasher
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.repository.SharedRecordingStore
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.domain.repository.SyncScheduler
import com.funactivity.ppmr.domain.sync.FileIdentity
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** A file another app shared, with the MIME type it claimed. */
data class SharedItem(val uri: String, val mimeType: String?)

sealed interface ShareResult {
    data class Done(val queued: Int, val alreadyInDrive: Int, val unreadable: Int) : ShareResult
    data object NotSignedIn : ShareResult
}

/**
 * Queues recordings shared from another app, such as a phone app that keeps its recordings
 * private. Uses the same dedup as folder scans, so sharing a file twice uploads it once.
 */
class ImportSharedRecordingsUseCase @Inject constructor(
    private val store: SharedRecordingStore,
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
    private val hasher: ContentHasher,
    private val messages: SyncMessages,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(items: List<SharedItem>): ShareResult {
        if (settings.appState.first().accountEmail == null) return ShareResult.NotSignedIn
        var queued = 0
        var already = 0
        var unreadable = 0
        store.locked {
            val known = sync.allIdentityKeys().toMutableSet()
            for (item in items) {
                val copy = store.importCopy(item.uri, item.mimeType)
                if (copy == null || copy.size <= 0) {
                    copy?.let { store.delete(it.documentUri) }
                    unreadable++
                    continue
                }
                val identity = FileIdentity.keyOf(copy.displayName, copy.size, copy.lastModified)
                if (!known.add(identity)) {
                    store.delete(copy.documentUri)
                    already++
                    continue
                }
                val hash = hasher.hashOf(copy)
                val existing = hash?.let { sync.driveFileIdForContent(it) }
                when {
                    existing != null -> {
                        sync.recordAsDuplicate(copy, identity, hash, existing, messages.duplicateContent())
                        store.delete(copy.documentUri)
                        already++
                    }
                    hash != null && sync.hasPendingContent(hash) -> {
                        store.delete(copy.documentUri)
                        already++
                    }
                    else -> {
                        sync.enqueue(copy, identity, hash)
                        queued++
                    }
                }
            }
        }
        if (queued > 0) scheduler.startUploads(settings.settings.first().wifiOnly, expedited = true)
        return ShareResult.Done(queued, already, unreadable)
    }
}

/** Deletes shared copies that are uploaded or no longer queued. */
class CleanUpSharedCopiesUseCase @Inject constructor(
    private val store: SharedRecordingStore,
    private val sync: SyncRepository,
) {
    suspend operator fun invoke() {
        store.locked { store.deleteAllExcept(sync.unfinishedDocumentUris()) }
    }
}
