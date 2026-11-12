package com.funactivity.ppmr.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.funactivity.ppmr.domain.model.AuthRequiredException
import com.funactivity.ppmr.domain.model.DriveApiException
import com.funactivity.ppmr.domain.model.DriveErrorKind
import com.funactivity.ppmr.domain.model.TrackedFile
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.domain.repository.SyncScheduler
import com.funactivity.ppmr.domain.sync.RetryPolicy
import com.funactivity.ppmr.domain.usecase.CleanUpSharedCopiesUseCase
import com.funactivity.ppmr.domain.usecase.EnsureDriveDestinationFolderUseCase
import com.funactivity.ppmr.domain.usecase.SyncMessages
import com.funactivity.ppmr.domain.usecase.UploadOutcome
import com.funactivity.ppmr.domain.usecase.UploadRecordingUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Works through the queue one file at a time. Retries are tracked per file in Room, so one bad
 * file cannot hold up the others and a stop or reboot loses nothing. The result is always
 * success: the queue, not WorkManager's own retry, decides what happens next.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncRepository,
    private val settings: SettingsRepository,
    private val ensureDriveFolder: EnsureDriveDestinationFolderUseCase,
    private val uploadRecording: UploadRecordingUseCase,
    private val cleanUpSharedCopies: CleanUpSharedCopiesUseCase,
    private val scheduler: SyncScheduler,
    private val connectivity: ConnectivityMonitor,
    private val tracker: SyncProgressTracker,
    private val notifier: SyncNotifier,
    private val messages: SyncMessages,
) : CoroutineWorker(context, params) {

    private var folderId: String? = null

    override suspend fun getForegroundInfo(): ForegroundInfo = notifier.scanningForeground()

    override suspend fun doWork(): Result {
        sync.resetInterrupted()
        val state = settings.appState.first()
        if (state.accountEmail == null || state.authRequired) return Result.success()
        try {
            coroutineScope {
                val foreground = launch { publishForegroundProgress() }
                processQueue()
                foreground.cancel()
            }
            cleanUpSharedCopies()
            scheduleFollowUp()
        } finally {
            // Also runs when WorkManager stops this worker, e.g. because the network went away.
            // Interrupted files go back in the queue without using up an attempt.
            withContext(NonCancellable) {
                sync.resetInterrupted()
                tracker.update(SyncProgress.Idle)
            }
        }
        return Result.success()
    }

    private suspend fun processQueue() {
        while (true) {
            val file = sync.nextDue(System.currentTimeMillis()) ?: return
            val wifiOnly = settings.settings.first().wifiOnly
            if (!connectivity.current().allowsUpload(wifiOnly)) return

            sync.markUploading(file.identityKey)
            tracker.update(SyncProgress.Uploading(file.displayName, 0, file.size))
            val keepGoing = try {
                upload(file)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthRequiredException) {
                Timber.i(e, "Google authorization needed")
                requestReauthorization()
                false
            } catch (e: DriveApiException) {
                handleDriveError(file, e)
            } catch (e: IOException) {
                handleIoError(file, e)
            } catch (e: Exception) {
                // A malformed response, for example. Never let one file crash the whole queue.
                Timber.w(e, "Unexpected upload failure")
                retryOrFail(file, e.message ?: e.javaClass.simpleName)
                true
            }
            if (!keepGoing) return
        }
    }

    private suspend fun upload(file: TrackedFile) {
        val destination = folderId ?: ensureDriveFolder().also { folderId = it }
        val outcome = uploadRecording(file, destination) { uploaded ->
            tracker.update(SyncProgress.Uploading(file.displayName, uploaded, file.size))
        }
        when (outcome) {
            is UploadOutcome.Uploaded -> {
                val note = if (outcome.alreadyInDrive) messages.alreadyInDrive() else null
                sync.markUploaded(file.identityKey, outcome.driveFileId, note)
                settings.setLastSuccessfulSync(System.currentTimeMillis())
                if (settings.settings.first().successNotifications) {
                    notifier.uploadSucceeded(file.displayName)
                }
            }
            UploadOutcome.FileChanged -> {
                sync.forget(file.identityKey)
                scheduler.requestScan(manual = false)
            }
            UploadOutcome.FileMissing -> fail(file, messages.fileMissing())
            UploadOutcome.FolderAccessLost -> fail(file, messages.folderAccessLost())
        }
    }

    /** Returns whether to continue with the next file. */
    private suspend fun handleDriveError(file: TrackedFile, error: DriveApiException): Boolean {
        Timber.w("Drive error %d (%s)", error.httpCode, error.kind)
        when (error.kind) {
            DriveErrorKind.AUTH -> {
                requestReauthorization()
                return false
            }
            DriveErrorKind.QUOTA_EXCEEDED -> fail(file, messages.quotaExceeded())
            DriveErrorKind.PERMANENT -> fail(file, error.message.orEmpty())
            DriveErrorKind.NOT_FOUND -> {
                // The folder may have been deleted while this run was going. Look it up again.
                folderId = null
                retryOrFail(file, error.message.orEmpty())
            }
            DriveErrorKind.TRANSIENT -> retryOrFail(file, error.message.orEmpty())
        }
        return true
    }

    /** Returns whether to continue with the next file. */
    private suspend fun handleIoError(file: TrackedFile, error: IOException): Boolean {
        Timber.i(error, "Network error during upload")
        val wifiOnly = settings.settings.first().wifiOnly
        if (!connectivity.current().allowsUpload(wifiOnly)) {
            // Going offline is not the file's fault: requeue it with no attempt used.
            // scheduleFollowUp() then waits for a permitted network.
            sync.resetInterrupted()
            return false
        }
        retryOrFail(file, messages.networkProblem())
        return true
    }

    private suspend fun retryOrFail(file: TrackedFile, reason: String) {
        val attempts = file.attempts + 1
        if (RetryPolicy.shouldGiveUp(attempts)) {
            sync.markFailed(file.identityKey, attempts, messages.gaveUp(reason))
            notifier.uploadFailed(file.displayName, reason)
        } else {
            val nextAttemptAt = System.currentTimeMillis() + RetryPolicy.delayAfter(attempts)
            sync.scheduleRetry(
                file.identityKey,
                attempts,
                nextAttemptAt,
                messages.retrying(attempts + 1, RetryPolicy.MAX_ATTEMPTS, reason),
            )
        }
    }

    private suspend fun fail(file: TrackedFile, reason: String) {
        sync.markFailed(file.identityKey, file.attempts + 1, reason)
        notifier.uploadFailed(file.displayName, reason)
    }

    private suspend fun requestReauthorization() {
        sync.resetInterrupted()
        settings.setAuthRequired(true)
        notifier.authRequired()
    }

    /** Whatever is still queued needs another run, after its backoff or when the network returns. */
    private suspend fun scheduleFollowUp() {
        if (settings.appState.first().authRequired) return
        val earliest = sync.earliestPendingAttempt() ?: return
        val delayMillis = (earliest - System.currentTimeMillis()).coerceAtLeast(MIN_FOLLOW_UP_DELAY_MS)
        scheduler.scheduleScan(delayMillis, manual = false)
    }

    /**
     * Keeps the notification current while uploading. Android can refuse foreground promotion when
     * the app is in the background; the upload then runs as normal work and, if WorkManager stops
     * it after its time limit, resumes from the queue.
     */
    private suspend fun publishForegroundProgress() {
        while (true) {
            val progress = tracker.progress.value
            if (progress is SyncProgress.Uploading) {
                try {
                    setForeground(notifier.uploadingForeground(progress))
                } catch (e: IllegalStateException) {
                    Timber.i(e, "Foreground service not allowed; continuing without it")
                    return
                }
            }
            delay(PROGRESS_INTERVAL_MS)
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 1_500L
        const val MIN_FOLLOW_UP_DELAY_MS = 30_000L
    }
}
