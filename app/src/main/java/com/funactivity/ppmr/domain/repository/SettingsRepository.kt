package com.funactivity.ppmr.domain.repository

import com.funactivity.ppmr.domain.model.AppSettings
import com.funactivity.ppmr.domain.model.AppState
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    val appState: Flow<AppState>

    suspend fun setWifiOnly(enabled: Boolean)
    suspend fun setAutoSync(enabled: Boolean)
    suspend fun setIncludeSubfolders(enabled: Boolean)
    suspend fun setSuccessNotifications(enabled: Boolean)
    suspend fun setMinFileAgeSeconds(seconds: Int)

    suspend fun setOnboardingDone()
    suspend fun setSourceFolder(treeUri: String, displayName: String)
    suspend fun setDriveFolderId(id: String?)
    suspend fun setLastSuccessfulSync(timestamp: Long)
    suspend fun setAuthRequired(required: Boolean)
    suspend fun setFolderLostNotified(notified: Boolean)

    /** Records the signed-in account and returns the previously used account, if any. */
    suspend fun setAccount(email: String): String?
    suspend fun clearAccount()
}
