package com.leanhome.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.leanhome.core.data.LeanHomeDataContainer
import com.leanhome.core.domain.usecase.GenerateWeeklyPlanUseCase
import com.leanhome.core.domain.usecase.GetProgressDashboardUseCase
import com.leanhome.feature.onboarding.OnboardingScreen
import com.leanhome.feature.onboarding.mvi.OnboardingViewModel
import com.leanhome.feature.workout.WorkoutPlayerViewModel
import com.leanhome.feature.workout.checkin.SessionCheckinScreen
import com.leanhome.feature.workout.dashboard.ProgressDashboardScreen
import com.leanhome.feature.workout.dashboard.ProgressDashboardViewModel
import com.leanhome.feature.workout.detail.WorkoutDetailScreen
import com.leanhome.feature.workout.list.WorkoutListScreen
import com.leanhome.feature.workout.mvi.PlayerExerciseSpec
import com.leanhome.feature.workout.player.WorkoutPlayerScreen

/**
 * LeanHome main navigation host.
 *
 * Routes:
 *   onboarding                  — multi-step onboarding flow (start destination)
 *   workoutList/{userId}        — upcoming + history sessions
 *   workoutDetail/{sessionId}   — exercises in a planned session
 *   workoutPlayer/{sessionId}   — ExoPlayer-driven session playback
 *   sessionCheckin/{sessionId}  — RPE + feeling tags check-in
 *   progressDashboard/{userId}  — weight trend, adherence, projected goal date
 *
 * The host pulls repositories and use cases from [LeanHomeApp]'s data
 * container, so every screen has the same single source of truth.
 */
@Composable
fun LeanHomeNavHost() {
    val navController = rememberNavController()
    val container = LeanHomeApp.instance.dataContainer

    val generateWeeklyPlan = remember(container) {
        GenerateWeeklyPlanUseCase(
            exerciseRepository = container.exerciseRepository,
            sessionRepository = container.workoutSessionRepository,
        )
    }
    val getDashboard = remember(container) {
        GetProgressDashboardUseCase(
            userProfileRepository = container.userProfileRepository,
            weightLogRepository = container.weightLogRepository,
            sessionRepository = container.workoutSessionRepository,
        )
    }

    NavHost(
        navController = navController,
        startDestination = "onboarding",
    ) {
        composable("onboarding") {
            val viewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModelFactory(container, generateWeeklyPlan),
            )
            OnboardingScreen(
                viewModel = viewModel,
                onComplete = { userId ->
                    navController.navigate("workoutList/$userId") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
            )
        }
        composable(
            "workoutList/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId").orEmpty()
            WorkoutListScreen(
                userId = userId,
                repository = container.workoutSessionRepository,
                onSelect = { session ->
                    navController.navigate("workoutDetail/${session.id}")
                },
                onOpenDashboard = { navController.navigate("progressDashboard/$userId") },
            )
        }
        composable(
            "workoutDetail/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            WorkoutDetailScreen(
                sessionId = sessionId,
                sessionRepository = container.workoutSessionRepository,
                exerciseRepository = container.exerciseRepository,
                onStartWorkout = { navController.navigate("workoutPlayer/$sessionId") },
            )
        }
        composable(
            "workoutPlayer/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            val playerViewModel: WorkoutPlayerViewModel = viewModel(
                factory = WorkoutPlayerViewModelFactory(sessionId, container),
            )
            WorkoutPlayerScreen(
                viewModel = playerViewModel,
                onCheckin = { navController.navigate("sessionCheckin/$sessionId") },
            )
        }
        composable(
            "sessionCheckin/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            val plannedExercises = remember(sessionId) {
                plannedSpecsForSession(sessionId, container)
            }
            SessionCheckinScreen(
                plannedExercises = plannedExercises,
                sessionId = sessionId,
                repository = container.workoutSessionRepository,
                onSubmit = {
                    // After check-in, return to the workout list (dashboard
                    // accessible from there). Sessions recompose on the Flow.
                    navController.popBackStack("workoutList/${currentUserIdForSession(sessionId, container)}", inclusive = false)
                },
            )
        }
        composable(
            "progressDashboard/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId").orEmpty()
            val dashboardViewModel: ProgressDashboardViewModel = viewModel(
                factory = ProgressDashboardViewModelFactory(userId, container, getDashboard),
            )
            ProgressDashboardScreen(
                viewModel = dashboardViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Blocking helper to resolve the current session's userId for the post-checkin
 * back-navigation target.
 */
private fun currentUserIdForSession(
    sessionId: String,
    container: LeanHomeDataContainer,
): String {
    return kotlinx.coroutines.runBlocking {
        container.workoutSessionRepository.getById(sessionId)?.userId.orEmpty()
    }
}

private fun plannedSpecsForSession(
    sessionId: String,
    container: LeanHomeDataContainer,
): List<PlayerExerciseSpec> = kotlinx.coroutines.runBlocking {
    val session = container.workoutSessionRepository.getById(sessionId)
        ?: return@runBlocking emptyList()
    val exercises = container.exerciseRepository.getAllOnce().associateBy { it.id }
    session.exercises.mapNotNull { plan ->
        val exercise = exercises[plan.exerciseId] ?: return@mapNotNull null
        PlayerExerciseSpec(
            exercise = exercise,
            plannedSets = plan.plannedSets,
            plannedReps = plan.plannedReps,
            restSeconds = plan.plannedRestSeconds,
        )
    }
}
