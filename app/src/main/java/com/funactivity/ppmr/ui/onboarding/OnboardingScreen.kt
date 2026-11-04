package com.funactivity.ppmr.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.funactivity.ppmr.R
import com.funactivity.ppmr.ui.auth.rememberGoogleSignIn
import com.funactivity.ppmr.ui.common.NotificationPermissionPrompt
import com.funactivity.ppmr.ui.common.rememberFolderPicker
import com.funactivity.ppmr.ui.home.AuthError
import com.funactivity.ppmr.ui.home.FolderHelpDialog

private const val STEP_COUNT = 3

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsStateWithLifecycle()
    val folderGrantFailed by viewModel.folderGrantFailed.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(1) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_step, step, STEP_COUNT),
            style = MaterialTheme.typography.labelLarge,
        )
        LinearProgressIndicator(
            progress = { step.toFloat() / STEP_COUNT },
            modifier = Modifier.fillMaxWidth(),
        )
        when (step) {
            1 -> SignInStep(appState.accountEmail, onNext = { step = 2 })
            2 -> FolderStep(
                folderName = appState.sourceFolderName,
                grantFailed = folderGrantFailed,
                onPicked = viewModel::onFolderPicked,
                onNext = { step = 3 },
            )
            else -> DoneStep(onFinish = { viewModel.finish(onFinished) })
        }
    }
}

@Composable
private fun ColumnScope.SignInStep(email: String?, onNext: () -> Unit) {
    val auth = rememberGoogleSignIn()
    StepText(R.string.onboarding_sign_in_title, R.string.onboarding_sign_in_body)
    if (email != null) {
        Text(stringResource(R.string.onboarding_signed_in_as, email))
    }
    AuthError(auth)
    when {
        auth.state.inProgress -> CircularProgressIndicator()
        email == null -> Button(onClick = auth.signIn) { Text(stringResource(R.string.action_sign_in)) }
    }
    StepButtons(ready = email != null, onNext = onNext)
}

@Composable
private fun ColumnScope.FolderStep(
    folderName: String?,
    grantFailed: Boolean,
    onPicked: (android.net.Uri) -> Unit,
    onNext: () -> Unit,
) {
    val pickFolder = rememberFolderPicker(onPicked)
    var showHelp by rememberSaveable { mutableStateOf(false) }
    if (showHelp) FolderHelpDialog(onDismiss = { showHelp = false })

    StepText(R.string.onboarding_folder_title, R.string.onboarding_folder_body)
    if (folderName != null) {
        Text(stringResource(R.string.onboarding_folder_selected, folderName))
    }
    if (grantFailed) {
        Text(
            stringResource(R.string.folder_grant_failed),
            color = MaterialTheme.colorScheme.error,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (folderName == null) {
            Button(onClick = pickFolder) { Text(stringResource(R.string.action_pick_folder)) }
        } else {
            OutlinedButton(onClick = pickFolder) { Text(stringResource(R.string.action_change_folder)) }
        }
    }
    TextButton(onClick = { showHelp = true }) { Text(stringResource(R.string.action_show_folder_help)) }
    StepButtons(ready = folderName != null, onNext = onNext)
}

@Composable
private fun ColumnScope.DoneStep(onFinish: () -> Unit) {
    StepText(R.string.onboarding_done_title, R.string.onboarding_done_body)
    NotificationPermissionPrompt()
    Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.privacy_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.weight(1f, fill = false))
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_finish))
    }
}

@Composable
private fun StepText(titleRes: Int, bodyRes: Int) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun StepButtons(ready: Boolean, onNext: () -> Unit) {
    if (ready) {
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_next)) }
    } else {
        TextButton(onClick = onNext) { Text(stringResource(R.string.action_skip)) }
    }
}
