package com.funactivity.ppmr.domain.model

import android.content.IntentSender

sealed interface SignInOutcome {
    data class Success(val email: String) : SignInOutcome

    /** Google must show its Drive consent screen; launch this, then complete the sign-in. */
    data class NeedsConsent(val intentSender: IntentSender) : SignInOutcome
    data object Cancelled : SignInOutcome
    data object ConsentDeclined : SignInOutcome
    data object NoAccount : SignInOutcome
    data object NotConfigured : SignInOutcome
    data class Failed(val reason: String) : SignInOutcome
}

/** Google requires user interaction before Drive can be used again. */
class AuthRequiredException(message: String = "Google authorization required") : Exception(message)
