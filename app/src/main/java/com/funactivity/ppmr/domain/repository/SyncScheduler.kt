package com.funactivity.ppmr.domain.repository

/** Starts and stops background sync work. */
interface SyncScheduler {
    /** Turns the periodic scan and the new-file trigger on or off. */
    fun setAutoSyncEnabled(enabled: Boolean)

    /** Scans as soon as possible. [manual] marks a user request, which also runs if auto-sync is off. */
    fun requestScan(manual: Boolean)

    /** Scans after [delayMillis], replacing any scan already waiting. */
    fun scheduleScan(delayMillis: Long, manual: Boolean)

    /** Starts the upload queue, waiting for a permitted network if needed. */
    suspend fun startUploads(wifiOnly: Boolean, expedited: Boolean)

    fun cancelAll()
}
