package com.funactivity.ppmr.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.funactivity.ppmr.MainActivity
import com.funactivity.ppmr.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_UPLOADS,
                    context.getString(R.string.channel_uploads_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.channel_uploads_description) },
                NotificationChannel(
                    CHANNEL_RESULTS,
                    context.getString(R.string.channel_results_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.channel_results_description) },
            ),
        )
    }

    fun scanningForeground(): ForegroundInfo =
        foregroundInfo(progressNotification(context.getString(R.string.notification_preparing), null))

    fun uploadingForeground(progress: SyncProgress.Uploading): ForegroundInfo =
        foregroundInfo(progressNotification(progress.fileName, progress))

    private fun foregroundInfo(notification: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ID_PROGRESS, notification)
        }

    private fun progressNotification(text: String, progress: SyncProgress.Uploading?): Notification =
        NotificationCompat.Builder(context, CHANNEL_UPLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_uploading_title))
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (progress == null) {
                    setProgress(0, 0, true)
                } else {
                    setProgress(PROGRESS_MAX, (progress.fraction * PROGRESS_MAX).toInt(), false)
                }
            }
            .build()

    fun uploadSucceeded(fileName: String) {
        notifyResult(
            id = fileName.hashCode(),
            title = context.getString(R.string.notification_upload_success_title),
            text = fileName,
        )
    }

    fun uploadFailed(fileName: String, reason: String) {
        notifyResult(
            id = fileName.hashCode(),
            title = context.getString(R.string.notification_upload_failed_title),
            text = "$fileName: $reason",
        )
    }

    fun authRequired() {
        notifyResult(
            id = ID_AUTH,
            title = context.getString(R.string.notification_auth_title),
            text = context.getString(R.string.notification_auth_text),
        )
    }

    fun folderAccessLost() {
        notifyResult(
            id = ID_FOLDER,
            title = context.getString(R.string.notification_folder_title),
            text = context.getString(R.string.notification_folder_text),
        )
    }

    @SuppressLint("MissingPermission") // Checked by canNotify().
    private fun notifyResult(id: Int, title: String, text: String) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL_UPLOADS = "uploads"
        const val CHANNEL_RESULTS = "results"
        const val ID_PROGRESS = 1001
        const val ID_AUTH = 1002
        const val ID_FOLDER = 1003
        const val PROGRESS_MAX = 100
    }
}
