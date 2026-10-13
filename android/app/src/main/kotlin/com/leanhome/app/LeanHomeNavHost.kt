package com.leanhome.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.leanhome.feature.onboarding.OnboardingScreen
import com.leanhome.feature.onboarding.mvi.OnboardingViewModel

/**
 * LeanHome main navigation host.
 *
 * Routes:
 *   onboarding  — the multi-step onboarding flow
 *   home        — landing screen after onboarding (the main hub)
 */
@Composable
fun LeanHomeNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "onboarding",
    ) {
        composable("onboarding") {
            val viewModel: OnboardingViewModel = viewModel()
            OnboardingScreen(
                viewModel = viewModel,
                onComplete = { navController.navigate("home") },
            )
        }
        composable("home") {
            HomePlaceholderScreen()
        }
    }
}

@Composable
private fun HomePlaceholderScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Welcome to LeanHome!\nOnboarding complete.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
