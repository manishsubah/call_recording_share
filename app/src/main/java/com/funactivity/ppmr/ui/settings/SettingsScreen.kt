package com.funactivity.ppmr.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.funactivity.ppmr.BuildConfig
import com.funactivity.ppmr.R
import com.funactivity.ppmr.domain.model.AppSettings
import com.funactivity.ppmr.ui.common.NotificationPermissionPrompt
import com.funactivity.ppmr.ui.common.SettingSwitch

private enum class Confirm { CLEAR_HISTORY, DISCONNECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val email by viewModel.accountEmail.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }

    when (confirm) {
        Confirm.CLEAR_HISTORY -> ConfirmDialog(
            text = stringResource(R.string.setting_clear_history_confirm),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = viewModel::clearHistory,
            onDismiss = { confirm = null },
        )
        Confirm.DISCONNECT -> ConfirmDialog(
            text = stringResource(R.string.setting_disconnect_confirm, email.orEmpty()),
            confirmLabel = stringResource(R.string.action_disconnect),
            onConfirm = viewModel::disconnect,
            onDismiss = { confirm = null },
        )
        null -> Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            SettingSwitch(
                title = stringResource(R.string.setting_wifi_only),
                summary = stringResource(R.string.setting_wifi_only_summary),
                checked = settings.wifiOnly,
                onChange = viewModel::setWifiOnly,
            )
            SettingSwitch(
                title = stringResource(R.string.setting_auto_sync),
                summary = stringResource(R.string.setting_auto_sync_summary),
                checked = settings.autoSync,
                onChange = viewModel::setAutoSync,
            )
            SettingSwitch(
                title = stringResource(R.string.setting_subfolders),
                summary = stringResource(R.string.setting_subfolders_summary),
                checked = settings.includeSubfolders,
                onChange = viewModel::setIncludeSubfolders,
            )
            SettingSwitch(
                title = stringResource(R.string.setting_success_notifications),
                summary = stringResource(R.string.setting_success_notifications_summary),
                checked = settings.successNotifications,
                onChange = viewModel::setSuccessNotifications,
            )
            MinAgeSetting(settings.minFileAgeSeconds, viewModel::setMinFileAge)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.setting_section_data))
            ActionRow(
                title = stringResource(R.string.setting_clear_history),
                summary = stringResource(R.string.setting_clear_history_summary),
                onClick = { confirm = Confirm.CLEAR_HISTORY },
            )
            email?.let {
                ActionRow(
                    title = stringResource(R.string.setting_disconnect),
                    summary = stringResource(R.string.setting_disconnect_summary),
                    onClick = { confirm = Confirm.DISCONNECT },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.permissions_title))
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BodyText(stringResource(R.string.permissions_folder))
                BodyText(stringResource(R.string.permissions_network))
                BodyText(stringResource(R.string.permissions_notifications))
                NotificationPermissionPrompt()
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.privacy_title))
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BodyText(stringResource(R.string.privacy_note))
                BodyText(stringResource(R.string.setting_version, BuildConfig.VERSION_NAME))
            }
        }
    }
}

@Composable
private fun MinAgeSetting(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.setting_min_age), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.setting_min_age_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettings.MIN_AGE_OPTIONS.forEach { seconds ->
                FilterChip(
                    selected = seconds == selected,
                    onClick = { onSelect(seconds) },
                    label = { Text(stringResource(R.string.setting_min_age_value, seconds)) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ActionRow(title: String, summary: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        BodyText(summary)
    }
}

@Composable
private fun ConfirmDialog(
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
