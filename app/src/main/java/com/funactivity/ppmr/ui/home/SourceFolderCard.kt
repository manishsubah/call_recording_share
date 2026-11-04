package com.funactivity.ppmr.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.funactivity.ppmr.R

sealed interface FolderStatus {
    data object NotSelected : FolderStatus
    data object Checking : FolderStatus
    data class Ready(val recordingCount: Int) : FolderStatus
    data object PermissionLost : FolderStatus
    data object ReadFailed : FolderStatus
}

@Composable
fun SourceFolderCard(
    folderName: String?,
    status: FolderStatus,
    onPick: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.folder_card_title), style = MaterialTheme.typography.labelLarge)
            if (folderName != null) {
                Text(folderName, style = MaterialTheme.typography.bodyLarge)
            }
            val detail = when (status) {
                FolderStatus.NotSelected -> stringResource(R.string.folder_none_selected)
                FolderStatus.Checking -> null
                FolderStatus.PermissionLost -> stringResource(R.string.folder_permission_lost)
                FolderStatus.ReadFailed -> stringResource(R.string.folder_read_failed)
                is FolderStatus.Ready ->
                    if (status.recordingCount == 0) {
                        stringResource(R.string.folder_no_recordings)
                    } else {
                        pluralStringResource(
                            R.plurals.folder_recording_count,
                            status.recordingCount,
                            status.recordingCount,
                        )
                    }
            }
            if (detail != null) {
                val isError = status is FolderStatus.PermissionLost || status is FolderStatus.ReadFailed
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status is FolderStatus.NotSelected || status is FolderStatus.PermissionLost) {
                    Button(onClick = onPick) { Text(stringResource(R.string.action_pick_folder)) }
                } else {
                    OutlinedButton(onClick = onPick) {
                        Text(stringResource(R.string.action_change_folder))
                    }
                }
                TextButton(onClick = onHelp) { Text(stringResource(R.string.action_show_folder_help)) }
            }
        }
    }
}
