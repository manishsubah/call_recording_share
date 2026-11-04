package com.funactivity.ppmr.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.funactivity.ppmr.R
import com.funactivity.ppmr.domain.model.SyncItem
import com.funactivity.ppmr.domain.model.SyncStatus

@Composable
fun ActivityRow(item: SyncItem, onRetry: (String) -> Unit, modifier: Modifier = Modifier) {
    val failed = item.status == SyncStatus.FAILED
    val rowModifier = if (failed) {
        modifier.clickable(
            onClickLabel = stringResource(R.string.activity_retry_action),
            onClick = { onRetry(item.identityKey) },
        )
    } else {
        modifier
    }
    Row(
        rowModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(item.status)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                item.fileName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                "${stringResource(item.status.labelRes())} · ${relativeTime(item.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.message != null) {
                Text(
                    item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (failed) {
                Text(
                    stringResource(R.string.activity_tap_to_retry),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(status: SyncStatus) {
    val modifier = Modifier.size(24.dp)
    // The status is also written out as text in the row, so the icons are decorative.
    when (status) {
        SyncStatus.QUEUED -> Icon(
            painterResource(R.drawable.ic_schedule),
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SyncStatus.UPLOADING -> CircularProgressIndicator(modifier.padding(2.dp), strokeWidth = 2.dp)
        SyncStatus.UPLOADED -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary,
        )
        SyncStatus.FAILED -> Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private fun SyncStatus.labelRes(): Int = when (this) {
    SyncStatus.QUEUED -> R.string.status_queued
    SyncStatus.UPLOADING -> R.string.status_uploading
    SyncStatus.UPLOADED -> R.string.status_uploaded
    SyncStatus.FAILED -> R.string.status_failed
}

/** Relative time that says "just now" instead of Android's "0 minutes ago". */
@Composable
internal fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    return if (now - timestamp < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.time_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}
