package com.funactivity.ppmr.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.funactivity.ppmr.ui.home.HomeScreen
import com.funactivity.ppmr.ui.onboarding.OnboardingScreen
import com.funactivity.ppmr.ui.settings.SettingsScreen

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(viewModel: RootViewModel = hiltViewModel()) {
    val startState by viewModel.onboardingDone.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Wait for the stored value so returning users never see onboarding flash by.
        val onboardingDone = startState ?: run {
            Box(Modifier.fillMaxSize())
            return@Surface
        }
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = if (onboardingDone) Routes.HOME else Routes.ONBOARDING,
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
