package com.funactivity.ppmr.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.funactivity.ppmr.R
import com.funactivity.ppmr.ui.common.rememberFolderPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsStateWithLifecycle()
    val activity by viewModel.recentActivity.collectAsStateWithLifecycle()
    val folderStatus by viewModel.folderStatus.collectAsStateWithLifecycle()
    var showFolderHelp by rememberSaveable { mutableStateOf(false) }
    val pickFolder = rememberFolderPicker(viewModel::onFolderPicked)

    LifecycleResumeEffect(Unit) {
        viewModel.refreshFolder()
        onPauseOrDispose { }
    }

    if (showFolderHelp) FolderHelpDialog(onDismiss = { showFolderHelp = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                SourceFolderCard(
                    folderName = appState.sourceFolderName,
                    status = folderStatus,
                    onPick = pickFolder,
                    onHelp = { showFolderHelp = true },
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(activity, key = { it.identityKey }) { item ->
                ActivityRow(item, onRetry = viewModel::retry)
            }
        }
    }
}
