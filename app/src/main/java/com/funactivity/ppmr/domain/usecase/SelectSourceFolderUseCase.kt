package com.funactivity.ppmr.domain.usecase

import com.funactivity.ppmr.domain.repository.RecordingFolderRepository
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.domain.repository.SyncScheduler
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SelectSourceFolderUseCase @Inject constructor(
    private val folders: RecordingFolderRepository,
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
) {
    /** Returns false when Android refused a lasting grant; the previous folder then stays. */
    suspend operator fun invoke(treeUri: String): Boolean {
        if (!folders.takePermission(treeUri)) return false
        val previous = settings.appState.first().sourceTreeUri
        settings.setSourceFolder(treeUri, folders.displayNameOf(treeUri))
        settings.setFolderLostNotified(false)
        if (previous != null && previous != treeUri) {
            // Queued files from the old folder must not upload once it is deselected.
            sync.clearUnfinished()
            folders.releasePermission(previous)
        }
        scheduler.requestScan(manual = false)
        return true
    }
}
