package com.funactivity.ppmr.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.funactivity.ppmr.R
import com.funactivity.ppmr.sync.SyncProgress
import com.funactivity.ppmr.ui.auth.rememberGoogleSignIn
import com.funactivity.ppmr.ui.common.SettingSwitch
import com.funactivity.ppmr.ui.common.rememberFolderPicker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val auth = rememberGoogleSignIn()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showFolderHelp by rememberSaveable { mutableStateOf(false) }
    val pickFolder = rememberFolderPicker(viewModel::onFolderPicked)
    val driveOpenFailed = stringResource(R.string.drive_open_failed)
    val folderGrantFailed = stringResource(R.string.folder_grant_failed)

    LifecycleResumeEffect(Unit) {
        viewModel.refreshFolder()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.folderGrantFailed.collect { snackbarHost.showSnackbar(folderGrantFailed) }
    }

    if (showFolderHelp) FolderHelpDialog(onDismiss = { showFolderHelp = false })

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
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
            item(key = "setup") {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccountCard(
                        email = state.appState.accountEmail,
                        authRequired = state.appState.authRequired,
                        auth = auth,
                        onSignOut = viewModel::signOut,
                    )
                    SourceFolderCard(
                        folderName = state.appState.sourceFolderName,
                        status = state.folderStatus,
                        onPick = pickFolder,
                        onHelp = { showFolderHelp = true },
                    )
                    DestinationCard(
                        folderId = state.appState.driveFolderId,
                        onOpen = {
                            val url = "https://drive.google.com/drive/folders/${state.appState.driveFolderId}"
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            } catch (e: ActivityNotFoundException) {
                                scope.launch { snackbarHost.showSnackbar(driveOpenFailed) }
                            }
                        },
                    )
                }
            }
            item(key = "auto-sync") {
                SettingSwitch(
                    title = stringResource(R.string.setting_auto_sync),
                    summary = stringResource(R.string.setting_auto_sync_summary),
                    checked = state.settings.autoSync,
                    onChange = viewModel::setAutoSync,
                )
            }
            item(key = "sync") {
                SyncSection(state, onSyncNow = viewModel::syncNow)
            }
            item(key = "activity-header") {
                ActivityHeader(state.pendingCount, state.failedCount)
            }
            if (state.activity.isEmpty()) {
                item(key = "activity-empty") {
                    SecondaryText(
                        stringResource(R.string.activity_empty),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.activity, key = { it.identityKey }) { item ->
                ActivityRow(item, onRetry = viewModel::retry)
            }
        }
    }
}

@Composable
private fun SyncSection(state: HomeUiState, onSyncNow: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onSyncNow,
            enabled = state.canSync && state.progress is SyncProgress.Idle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_sync_now))
        }
        SyncProgressStatus(state.progress)
        if (!state.canSync && !state.appState.authRequired) {
            SecondaryText(stringResource(R.string.sync_needs_setup))
        }
        when (state.notice) {
            SyncNotice.NONE -> Unit
            SyncNotice.NO_NETWORK -> Text(
                stringResource(R.string.notice_no_network),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            SyncNotice.WAITING_FOR_WIFI -> Text(
                stringResource(R.string.notice_wifi_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val lastSync = state.appState.lastSuccessfulSyncAt
        SecondaryText(
            if (lastSync != null) {
                val time = DateUtils.getRelativeTimeSpanString(
                    lastSync,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                )
                stringResource(R.string.last_sync_label, time)
            } else {
                stringResource(R.string.last_sync_never)
            },
        )
    }
}

@Composable
private fun ActivityHeader(pending: Int, failed: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.activity_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (pending > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(stringResource(R.string.badge_pending, pending), Modifier.padding(horizontal = 4.dp))
            }
        }
        if (failed > 0) {
            Badge {
                Text(stringResource(R.string.badge_failed, failed), Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
