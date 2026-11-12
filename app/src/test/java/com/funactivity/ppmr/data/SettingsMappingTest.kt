package com.funactivity.ppmr.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.funactivity.ppmr.data.files.formatTreeDocumentId
import com.funactivity.ppmr.data.local.PrefKeys
import com.funactivity.ppmr.data.local.encodeSourceFolders
import com.funactivity.ppmr.data.local.toAppSettings
import com.funactivity.ppmr.data.local.toAppState
import com.funactivity.ppmr.domain.model.AppSettings
import com.funactivity.ppmr.domain.model.AppState
import com.funactivity.ppmr.domain.model.SourceFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMappingTest {
    @Test
    fun `empty preferences give the documented defaults`() {
        val settings = emptyPreferences().toAppSettings()
        assertEquals(true, settings.wifiOnly)
        assertEquals(true, settings.autoSync)
        assertEquals(false, settings.includeSubfolders)
        assertEquals(false, settings.successNotifications)
        assertEquals(30, settings.minFileAgeSeconds)
        assertEquals(AppState(), emptyPreferences().toAppState())
    }

    @Test
    fun `stored values are mapped`() {
        val settings = preferencesOf(
            PrefKeys.WIFI_ONLY to false,
            PrefKeys.AUTO_SYNC to false,
            PrefKeys.INCLUDE_SUBFOLDERS to true,
            PrefKeys.SUCCESS_NOTIFICATIONS to true,
            PrefKeys.MIN_FILE_AGE_SECONDS to 10,
        ).toAppSettings()
        assertEquals(AppSettings(false, false, true, true, 10), settings)
    }

    @Test
    fun `out of range minimum age falls back to the default`() {
        val settings = preferencesOf(PrefKeys.MIN_FILE_AGE_SECONDS to 9999).toAppSettings()
        assertEquals(AppSettings.DEFAULT_MIN_AGE_SECONDS, settings.minFileAgeSeconds)
    }

    @Test
    fun `a folder saved by the single folder version is still used`() {
        val state = preferencesOf(
            PrefKeys.SOURCE_TREE_URI to "content://old",
            PrefKeys.SOURCE_FOLDER_NAME to "Old",
        ).toAppState()
        assertEquals(listOf(SourceFolder("content://old", "Old")), state.sourceFolders)
    }

    @Test
    fun `the saved folder list keeps every folder in order`() {
        val folders = listOf(SourceFolder("content://a", "A"), SourceFolder("content://b", "B"))
        val state = preferencesOf(
            PrefKeys.SOURCE_FOLDERS to encodeSourceFolders(folders),
            PrefKeys.SOURCE_TREE_URI to "content://old",
        ).toAppState()
        assertEquals(folders, state.sourceFolders)
    }

    @Test
    fun `tree document IDs become readable paths`() {
        fun format(id: String) = formatTreeDocumentId(id, "Internal storage", "SD card")
        assertEquals("Internal storage/Recordings/Call", format("primary:Recordings/Call"))
        assertEquals("Internal storage", format("primary:"))
        assertEquals("SD card (1234-ABCD)/Music", format("1234-ABCD:Music"))
        assertEquals("msf-provider-id", format("msf-provider-id"))
    }
}
