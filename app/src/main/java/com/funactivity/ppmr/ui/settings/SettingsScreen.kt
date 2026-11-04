package com.funactivity.ppmr.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.funactivity.ppmr.R
import com.funactivity.ppmr.domain.model.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
internal fun SettingSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The whole row is the toggle target; null avoids a second focus stop for TalkBack.
        Switch(checked = checked, onCheckedChange = null)
    }
}
