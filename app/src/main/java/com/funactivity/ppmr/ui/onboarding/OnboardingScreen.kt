package com.funactivity.ppmr.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Placeholder until the sign-in and folder steps exist. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { onFinished() }
}
