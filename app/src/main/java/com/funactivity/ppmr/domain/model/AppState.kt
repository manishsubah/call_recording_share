package com.funactivity.ppmr.domain.model

/** Non-preference app state: who is signed in and which folders are connected. */
data class AppState(
    val onboardingDone: Boolean = false,
    val accountEmail: String? = null,
    val driveFolderId: String? = null,
    val sourceTreeUri: String? = null,
    val sourceFolderName: String? = null,
    val lastSuccessfulSyncAt: Long? = null,
    val authRequired: Boolean = false,
)
