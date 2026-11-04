package com.funactivity.ppmr.data.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.funactivity.ppmr.BuildConfig
import com.funactivity.ppmr.domain.model.AuthRequiredException
import com.funactivity.ppmr.domain.model.SignInOutcome
import com.funactivity.ppmr.domain.repository.AccessTokenProvider
import com.funactivity.ppmr.domain.repository.AuthRepository
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Sign-in uses Credential Manager (identity) and then Google Identity's AuthorizationClient
 * (Drive access). Google Play services stores and refreshes the tokens; this app keeps none.
 */
@Singleton
class GoogleAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : AuthRepository, AccessTokenProvider {

    private val authorizationClient get() = Identity.getAuthorizationClient(context)

    /** The account being signed in while Google's consent screen is showing. */
    private var pendingEmail: String? = null

    override suspend fun signIn(activity: Activity): SignInOutcome {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank()) return SignInOutcome.NotConfigured

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val email = try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                // Only the email is used. The ID token is never stored or sent anywhere.
                GoogleIdTokenCredential.createFrom(credential.data).id
            } else {
                return SignInOutcome.Failed("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            return SignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            Timber.w(e, "No Google credential available")
            return SignInOutcome.NoAccount
        } catch (e: GetCredentialException) {
            Timber.w(e, "Credential Manager sign-in failed")
            return SignInOutcome.Failed(e.message ?: e.type)
        }
        return authorize(email)
    }

    override suspend fun authorize(email: String): SignInOutcome = try {
        val result = authorizationClient.authorize(authorizationRequest(email)).await()
        val intentSender = result.pendingIntent?.intentSender
        when {
            result.hasResolution() && intentSender != null -> {
                pendingEmail = email
                SignInOutcome.NeedsConsent(intentSender)
            }
            result.accessToken != null -> SignInOutcome.Success(email)
            else -> SignInOutcome.Failed("Google returned no access token")
        }
    } catch (e: ApiException) {
        Timber.w(e, "Drive authorization failed")
        SignInOutcome.Failed(e.message ?: "Error ${e.statusCode}")
    }

    override suspend fun completeConsent(resultOk: Boolean, data: Intent?): SignInOutcome {
        val email = pendingEmail
        pendingEmail = null
        if (!resultOk || data == null || email == null) return SignInOutcome.ConsentDeclined
        return try {
            val result = authorizationClient.getAuthorizationResultFromIntent(data)
            if (result.accessToken != null) SignInOutcome.Success(email) else SignInOutcome.ConsentDeclined
        } catch (e: ApiException) {
            Timber.w(e, "Drive consent failed")
            if (e.statusCode == CommonStatusCodes.CANCELED) {
                SignInOutcome.ConsentDeclined
            } else {
                SignInOutcome.Failed(e.message ?: "Error ${e.statusCode}")
            }
        }
    }

    override suspend fun signOut() {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            Timber.w(e, "Could not clear credential state")
        }
    }

    override suspend fun disconnect(email: String) {
        try {
            val request = RevokeAccessRequest.builder()
                .setAccount(Account(email, ACCOUNT_TYPE))
                .setScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            authorizationClient.revokeAccess(request).await()
        } catch (e: ApiException) {
            // Local sign-out still goes ahead. The user can also revoke at myaccount.google.com.
            Timber.w(e, "Could not revoke Drive access")
        }
        signOut()
    }

    override suspend fun accessToken(): String {
        val email = settings.appState.first().accountEmail
            ?: throw AuthRequiredException("Not signed in")
        val result = try {
            authorizationClient.authorize(authorizationRequest(email)).await()
        } catch (e: ApiException) {
            // Only known temporary failures are retried. Anything else, such as an account removed
            // from the phone (status 16), will not fix itself and needs the user.
            if (e.statusCode in TEMPORARY_STATUS_CODES) {
                throw IOException("Could not get a Google token (${e.statusCode})", e)
            }
            throw AuthRequiredException(e.message ?: "Google authorization failed")
        }
        if (result.hasResolution()) throw AuthRequiredException()
        return result.accessToken ?: throw AuthRequiredException("Google returned no access token")
    }

    override suspend fun invalidate(token: String) {
        try {
            authorizationClient.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
        } catch (e: ApiException) {
            Timber.w(e, "Could not clear cached token")
        }
    }

    private fun authorizationRequest(email: String): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .setAccount(Account(email, ACCOUNT_TYPE))
            .build()

    private companion object {
        /** Grants access only to files and folders this app creates, not the rest of Drive. */
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val ACCOUNT_TYPE = "com.google"
        val TEMPORARY_STATUS_CODES = setOf(
            CommonStatusCodes.NETWORK_ERROR,
            CommonStatusCodes.INTERNAL_ERROR,
            CommonStatusCodes.INTERRUPTED,
            CommonStatusCodes.TIMEOUT,
            CommonStatusCodes.API_NOT_CONNECTED,
        )
    }
}
