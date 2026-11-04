package com.funactivity.ppmr.ui.auth

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funactivity.ppmr.R
import com.funactivity.ppmr.domain.model.SignInOutcome
import com.funactivity.ppmr.domain.repository.AuthRepository
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.usecase.CompleteSignInUseCase
import com.funactivity.ppmr.domain.usecase.EnsureDriveDestinationFolderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class AuthUiState(
    val inProgress: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val errorDetail: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val completeSignIn: CompleteSignInUseCase,
    private val ensureDriveFolder: EnsureDriveDestinationFolderUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val consentChannel = Channel<IntentSender>(Channel.BUFFERED)
    val consentRequests: Flow<IntentSender> = consentChannel.receiveAsFlow()

    /** Full sign-in with the account picker. */
    fun signIn(activity: Activity) = run { auth.signIn(activity) }

    /** Re-authorizes the known account, falling back to the picker if there is none. */
    fun signInAgain(activity: Activity) = run {
        val email = settings.appState.first().accountEmail
        if (email != null) auth.authorize(email) else auth.signIn(activity)
    }

    fun onConsentResult(resultOk: Boolean, data: Intent?) = run { auth.completeConsent(resultOk, data) }

    fun dismissError() {
        _state.value = _state.value.copy(errorRes = null, errorDetail = null)
    }

    private fun run(attempt: suspend () -> SignInOutcome) {
        viewModelScope.launch {
            _state.value = AuthUiState(inProgress = true)
            handle(attempt())
        }
    }

    private suspend fun handle(outcome: SignInOutcome) {
        _state.value = when (outcome) {
            is SignInOutcome.Success -> {
                completeSignIn(outcome.email)
                prepareDriveFolder()
                AuthUiState()
            }
            is SignInOutcome.NeedsConsent -> {
                consentChannel.send(outcome.intentSender)
                AuthUiState(inProgress = true)
            }
            SignInOutcome.Cancelled -> AuthUiState(errorRes = R.string.auth_error_cancelled)
            SignInOutcome.ConsentDeclined -> AuthUiState(errorRes = R.string.auth_error_consent_cancelled)
            SignInOutcome.NoAccount -> AuthUiState(errorRes = R.string.auth_error_no_account)
            SignInOutcome.NotConfigured -> AuthUiState(errorRes = R.string.auth_error_not_configured)
            is SignInOutcome.Failed -> AuthUiState(
                errorRes = R.string.auth_error_failed,
                errorDetail = outcome.reason,
            )
        }
    }

    /** Best effort: if offline, the upload worker creates the folder later. */
    private suspend fun prepareDriveFolder() {
        try {
            ensureDriveFolder()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.i(e, "Drive folder not created yet")
        } catch (e: Exception) {
            Timber.w(e, "Drive folder not created yet")
        }
    }
}
