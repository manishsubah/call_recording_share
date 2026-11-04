package com.funactivity.ppmr.domain.usecase

import com.funactivity.ppmr.domain.repository.RecordingFolderRepository
import com.funactivity.ppmr.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SelectSourceFolderUseCase @Inject constructor(
    private val folders: RecordingFolderRepository,
    private val settings: SettingsRepository,
) {
    /** Returns false when Android refused a lasting grant; the previous folder then stays. */
    suspend operator fun invoke(treeUri: String): Boolean {
        if (!folders.takePermission(treeUri)) return false
        val previous = settings.appState.first().sourceTreeUri
        settings.setSourceFolder(treeUri, folders.displayNameOf(treeUri))
        if (previous != null && previous != treeUri) folders.releasePermission(previous)
        return true
    }
}
