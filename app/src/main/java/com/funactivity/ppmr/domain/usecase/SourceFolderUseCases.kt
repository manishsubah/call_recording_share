package com.funactivity.ppmr.domain.usecase

import com.funactivity.ppmr.domain.model.SourceFolder
import com.funactivity.ppmr.domain.repository.RecordingFolderRepository
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.domain.repository.SyncScheduler
import javax.inject.Inject

class AddSourceFolderUseCase @Inject constructor(
    private val folders: RecordingFolderRepository,
    private val settings: SettingsRepository,
    private val scheduler: SyncScheduler,
) {
    /** Returns false when Android refused a lasting grant; the folder list then stays the same. */
    suspend operator fun invoke(treeUri: String): Boolean {
        if (!folders.takePermission(treeUri)) return false
        settings.addSourceFolder(SourceFolder(treeUri, folders.displayNameOf(treeUri)))
        settings.setFolderLostNotified(false)
        scheduler.requestScan(manual = false)
        return true
    }
}

class RemoveSourceFolderUseCase @Inject constructor(
    private val folders: RecordingFolderRepository,
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(treeUri: String) {
        settings.removeSourceFolder(treeUri)
        // Queued files from this folder must not upload. The scan queues the other folders again.
        sync.clearUnfinished()
        folders.releasePermission(treeUri)
        settings.setFolderLostNotified(false)
        scheduler.requestScan(manual = false)
    }
}
