package com.funactivity.ppmr.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.funactivity.ppmr.R
import com.funactivity.ppmr.sync.SyncProgress
import com.funactivity.ppmr.ui.auth.GoogleSignInActions

@Composable
fun AccountCard(
    email: String?,
    authRequired: Boolean,
    auth: GoogleSignInActions,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.account_card_title), style = MaterialTheme.typography.labelLarge)
            if (email != null) Text(email, style = MaterialTheme.typography.bodyLarge)
            when {
                email == null -> SecondaryText(stringResource(R.string.account_signed_out))
                authRequired -> Text(
                    stringResource(R.string.account_reauth_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            AuthError(auth)
            when {
                auth.state.inProgress -> CircularProgressIndicator()
                email == null -> Button(onClick = auth.signIn) { Text(stringResource(R.string.action_sign_in)) }
                authRequired -> Button(onClick = auth.signInAgain) {
                    Text(stringResource(R.string.action_sign_in_again))
                }
                else -> TextButton(onClick = onSignOut) { Text(stringResource(R.string.action_sign_out)) }
            }
        }
    }
}

/** The error from the last sign-in attempt, if any. */
@Composable
fun AuthError(auth: GoogleSignInActions) {
    val errorRes = auth.state.errorRes ?: return
    val detail = auth.state.errorDetail
    Text(
        if (detail != null) stringResource(errorRes, detail) else stringResource(errorRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
fun DestinationCard(folderId: String?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.destination_card_title), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.destination_path), style = MaterialTheme.typography.bodyLarge)
            if (folderId != null) {
                OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.action_open_drive)) }
            } else {
                SecondaryText(stringResource(R.string.destination_not_ready))
            }
        }
    }
}

@Composable
fun SyncProgressStatus(progress: SyncProgress, modifier: Modifier = Modifier) {
    when (progress) {
        SyncProgress.Idle -> Unit
        SyncProgress.Scanning -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SecondaryText(stringResource(R.string.sync_state_scanning))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is SyncProgress.Uploading -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SecondaryText(stringResource(R.string.sync_state_uploading, progress.fileName))
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun SecondaryText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
