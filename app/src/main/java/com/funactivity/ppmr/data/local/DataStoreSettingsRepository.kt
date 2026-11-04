package com.funactivity.ppmr.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.funactivity.ppmr.domain.model.AppSettings
import com.funactivity.ppmr.domain.model.AppState
import com.funactivity.ppmr.domain.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) {
            Timber.w(error, "Could not read preferences; using defaults")
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

    override val settings: Flow<AppSettings> =
        preferences.map { it.toAppSettings() }.distinctUntilChanged()

    override val appState: Flow<AppState> =
        preferences.map { it.toAppState() }.distinctUntilChanged()

    override suspend fun setWifiOnly(enabled: Boolean) = put { it[PrefKeys.WIFI_ONLY] = enabled }

    override suspend fun setAutoSync(enabled: Boolean) = put { it[PrefKeys.AUTO_SYNC] = enabled }

    override suspend fun setIncludeSubfolders(enabled: Boolean) =
        put { it[PrefKeys.INCLUDE_SUBFOLDERS] = enabled }

    override suspend fun setSuccessNotifications(enabled: Boolean) =
        put { it[PrefKeys.SUCCESS_NOTIFICATIONS] = enabled }

    override suspend fun setMinFileAgeSeconds(seconds: Int) =
        put { it[PrefKeys.MIN_FILE_AGE_SECONDS] = AppSettings.normalizeMinAge(seconds) }

    override suspend fun setOnboardingDone() = put { it[PrefKeys.ONBOARDING_DONE] = true }

    override suspend fun setSourceFolder(treeUri: String, displayName: String) = put {
        it[PrefKeys.SOURCE_TREE_URI] = treeUri
        it[PrefKeys.SOURCE_FOLDER_NAME] = displayName
    }

    override suspend fun setDriveFolderId(id: String?) = put {
        if (id == null) it.remove(PrefKeys.DRIVE_FOLDER_ID) else it[PrefKeys.DRIVE_FOLDER_ID] = id
    }

    override suspend fun setLastSuccessfulSync(timestamp: Long) =
        put { it[PrefKeys.LAST_SUCCESSFUL_SYNC] = timestamp }

    override suspend fun setAuthRequired(required: Boolean) =
        put { it[PrefKeys.AUTH_REQUIRED] = required }

    override suspend fun setFolderLostNotified(notified: Boolean) =
        put { it[PrefKeys.FOLDER_LOST_NOTIFIED] = notified }

    override suspend fun setAccount(email: String): String? {
        var previous: String? = null
        dataStore.edit {
            previous = it[PrefKeys.LAST_ACCOUNT_EMAIL]
            it[PrefKeys.ACCOUNT_EMAIL] = email
            it[PrefKeys.LAST_ACCOUNT_EMAIL] = email
            it[PrefKeys.AUTH_REQUIRED] = false
        }
        return previous
    }

    override suspend fun clearAccount() = put {
        // LAST_ACCOUNT_EMAIL stays so a later sign-in can tell whether the account changed.
        it.remove(PrefKeys.ACCOUNT_EMAIL)
        it.remove(PrefKeys.DRIVE_FOLDER_ID)
        it.remove(PrefKeys.AUTH_REQUIRED)
    }

    private suspend inline fun put(crossinline block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }
}
