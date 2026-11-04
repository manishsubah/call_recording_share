package com.funactivity.ppmr.ui.auth

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

class GoogleSignInActions(
    val state: AuthUiState,
    val signIn: () -> Unit,
    val signInAgain: () -> Unit,
    val dismissError: () -> Unit,
)

/** Wires sign-in to the current activity, including Google's Drive consent screen. */
@Composable
fun rememberGoogleSignIn(viewModel: AuthViewModel = hiltViewModel()): GoogleSignInActions {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK, result.data)
    }
    LaunchedEffect(viewModel) {
        viewModel.consentRequests.collect { sender ->
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    return GoogleSignInActions(
        state = state,
        signIn = { activity?.let(viewModel::signIn) },
        signInAgain = { activity?.let(viewModel::signInAgain) },
        dismissError = viewModel::dismissError,
    )
}
