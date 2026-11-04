package com.funactivity.ppmr.data.local

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.funactivity.ppmr.domain.model.AppSettings
import com.funactivity.ppmr.domain.model.AppState

internal object PrefKeys {
    val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    val AUTO_SYNC = booleanPreferencesKey("auto_sync")
    val INCLUDE_SUBFOLDERS = booleanPreferencesKey("include_subfolders")
    val SUCCESS_NOTIFICATIONS = booleanPreferencesKey("success_notifications")
    val MIN_FILE_AGE_SECONDS = intPreferencesKey("min_file_age_seconds")

    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
    val LAST_ACCOUNT_EMAIL = stringPreferencesKey("last_account_email")
    val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
    val SOURCE_TREE_URI = stringPreferencesKey("source_tree_uri")
    val SOURCE_FOLDER_NAME = stringPreferencesKey("source_folder_name")
    val LAST_SUCCESSFUL_SYNC = longPreferencesKey("last_successful_sync")
    val AUTH_REQUIRED = booleanPreferencesKey("auth_required")
    val FOLDER_LOST_NOTIFIED = booleanPreferencesKey("folder_lost_notified")
}

internal fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        wifiOnly = this[PrefKeys.WIFI_ONLY] ?: defaults.wifiOnly,
        autoSync = this[PrefKeys.AUTO_SYNC] ?: defaults.autoSync,
        includeSubfolders = this[PrefKeys.INCLUDE_SUBFOLDERS] ?: defaults.includeSubfolders,
        successNotifications = this[PrefKeys.SUCCESS_NOTIFICATIONS] ?: defaults.successNotifications,
        minFileAgeSeconds = AppSettings.normalizeMinAge(this[PrefKeys.MIN_FILE_AGE_SECONDS]),
    )
}

internal fun Preferences.toAppState(): AppState = AppState(
    onboardingDone = this[PrefKeys.ONBOARDING_DONE] ?: false,
    accountEmail = this[PrefKeys.ACCOUNT_EMAIL],
    driveFolderId = this[PrefKeys.DRIVE_FOLDER_ID],
    sourceTreeUri = this[PrefKeys.SOURCE_TREE_URI],
    sourceFolderName = this[PrefKeys.SOURCE_FOLDER_NAME],
    lastSuccessfulSyncAt = this[PrefKeys.LAST_SUCCESSFUL_SYNC],
    authRequired = this[PrefKeys.AUTH_REQUIRED] ?: false,
    folderLostNotified = this[PrefKeys.FOLDER_LOST_NOTIFIED] ?: false,
)
