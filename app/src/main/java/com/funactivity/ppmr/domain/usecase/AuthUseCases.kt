package com.funactivity.ppmr.domain.usecase

import com.funactivity.ppmr.domain.repository.DriveRepository
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.repository.SyncRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Name of the Drive folder that receives uploads. */
const val DRIVE_FOLDER_NAME = "Call Recordings"

/** Returns the destination folder ID, creating or re-finding the folder when needed. */
class EnsureDriveDestinationFolderUseCase @Inject constructor(
    private val drive: DriveRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(): String {
        val stored = settings.appState.first().driveFolderId
        if (stored != null && drive.folderExists(stored)) return stored
        val folderId = drive.findOrCreateFolder(DRIVE_FOLDER_NAME)
        settings.setDriveFolderId(folderId)
        return folderId
    }
}

/** Saves a completed sign-in. Upload records belong to one account, so a new one starts clean. */
class CompleteSignInUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val sync: SyncRepository,
) {
    suspend operator fun invoke(email: String) {
        val previous = settings.setAccount(email)
        if (previous != null && previous != email) sync.clearAll()
    }
}
