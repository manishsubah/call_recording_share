package com.funactivity.ppmr.domain.model

/** A folder the user chose to upload from, with the path shown for it. */
data class SourceFolder(val treeUri: String, val name: String)

/** Non-preference app state: who is signed in and which folders are connected. */
data class AppState(
    val onboardingDone: Boolean = false,
    val accountEmail: String? = null,
    val driveFolderId: String? = null,
    val sourceFolders: List<SourceFolder> = emptyList(),
    val lastSuccessfulSyncAt: Long? = null,
    val authRequired: Boolean = false,
    val folderLostNotified: Boolean = false,
)
