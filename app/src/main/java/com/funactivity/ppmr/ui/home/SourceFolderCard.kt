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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.funactivity.ppmr.R
import com.funactivity.ppmr.domain.model.SourceFolder

sealed interface FolderStatus {
    data object NotSelected : FolderStatus
    data object Checking : FolderStatus
    data class Ready(val recordingCount: Int) : FolderStatus
    data class PermissionLost(val allFolders: Boolean) : FolderStatus
    data object ReadFailed : FolderStatus
}

@Composable
fun SourceFolderCard(
    folders: List<SourceFolder>,
    status: FolderStatus,
    onPick: () -> Unit,
    onRemove: (treeUri: String) -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.folder_card_title), style = MaterialTheme.typography.labelLarge)
            folders.forEach { folder ->
                val removeDescription = stringResource(R.string.action_remove_folder_description, folder.name)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(folder.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { onRemove(folder.treeUri) },
                        modifier = Modifier.semantics { contentDescription = removeDescription },
                    ) {
                        Text(stringResource(R.string.action_remove_folder))
                    }
                }
            }
            val detail = when (status) {
                FolderStatus.NotSelected -> stringResource(R.string.folder_none_selected)
                FolderStatus.Checking -> null
                is FolderStatus.PermissionLost -> stringResource(R.string.folder_permission_lost)
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
                if (folders.isEmpty()) {
                    Button(onClick = onPick) { Text(stringResource(R.string.action_pick_folder)) }
                } else {
                    OutlinedButton(onClick = onPick) {
                        Text(stringResource(R.string.action_add_folder))
                    }
                }
                TextButton(onClick = onHelp) { Text(stringResource(R.string.action_show_folder_help)) }
            }
        }
    }
}
