package com.funactivity.ppmr.sync

import android.content.Context
import com.funactivity.ppmr.R
import com.funactivity.ppmr.domain.usecase.SyncMessages
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ResourceSyncMessages @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncMessages {
    override fun duplicateContent() = context.getString(R.string.sync_duplicate_content)
    override fun alreadyInDrive() = context.getString(R.string.sync_already_in_drive)
    override fun fileMissing() = context.getString(R.string.sync_file_missing)
    override fun folderAccessLost() = context.getString(R.string.sync_folder_lost)
    override fun quotaExceeded() = context.getString(R.string.sync_quota_exceeded)
    override fun retrying(attempt: Int, maxAttempts: Int, reason: String) =
        context.getString(R.string.sync_retrying, attempt, maxAttempts, reason)
    override fun gaveUp(reason: String) = context.getString(R.string.sync_gave_up, reason)
    override fun networkProblem() = context.getString(R.string.sync_network_problem)
}
