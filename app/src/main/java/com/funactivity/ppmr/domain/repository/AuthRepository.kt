package com.funactivity.ppmr.domain.repository

import android.app.Activity
import android.content.Intent
import com.funactivity.ppmr.domain.model.SignInOutcome

interface AuthRepository {
    /** Shows the Google account picker, then requests Drive access for the chosen account. */
    suspend fun signIn(activity: Activity): SignInOutcome

    /** Requests Drive access again for an already known account, without the account picker. */
    suspend fun authorize(email: String): SignInOutcome

    /** Finishes a sign-in after the consent screen from [SignInOutcome.NeedsConsent] returns. */
    suspend fun completeConsent(resultOk: Boolean, data: Intent?): SignInOutcome

    suspend fun signOut()

    /** Signs out and also revokes this app's Drive access for [email]. */
    suspend fun disconnect(email: String)
}

interface AccessTokenProvider {
    /**
     * Returns a valid token without any UI.
     * @throws com.funactivity.ppmr.domain.model.AuthRequiredException if the user must act.
     * @throws java.io.IOException if Google could not be reached.
     */
    suspend fun accessToken(): String

    suspend fun invalidate(token: String)
}
