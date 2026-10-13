package com.leanhome.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.leanhome.core.data.LeanHomeDataContainer
import com.leanhome.core.domain.usecase.GenerateWeeklyPlanUseCase
import com.leanhome.core.domain.usecase.GetProgressDashboardUseCase
import com.leanhome.feature.onboarding.mvi.OnboardingViewModel
import com.leanhome.feature.workout.WorkoutPlayerViewModel
import com.leanhome.feature.workout.dashboard.ProgressDashboardViewModel

/**
 * Factory that injects the data container + weekly plan generator into the
 * OnboardingViewModel so onboarding persists the profile and generates the
 * first week of sessions before signaling completion.
 */
class OnboardingViewModelFactory(
    private val container: LeanHomeDataContainer,
    private val generateWeeklyPlan: GenerateWeeklyPlanUseCase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(
        engine = com.leanhome.core.domain.engine.EnergyBalanceEngine(),
        exerciseRepository = container.exerciseRepository,
        userProfileRepository = container.userProfileRepository,
        generateWeeklyPlan = generateWeeklyPlan,
    ) as T
}

/**
 * Factory for [WorkoutPlayerViewModel] — loads a session synchronously enough
 * to seed the player state machine (the session comes from the local Room DB
 * so latency is negligible).
 */
class WorkoutPlayerViewModelFactory(
    private val sessionId: String,
    private val container: LeanHomeDataContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val session = kotlinx.coroutines.runBlocking {
            container.workoutSessionRepository.getById(sessionId)
        }
        val exerciseById = kotlinx.coroutines.runBlocking {
            container.exerciseRepository.getAllOnce().associateBy { it.id }
        }
        val specs = session?.exercises?.mapNotNull { plan ->
            val exercise = exerciseById[plan.exerciseId] ?: return@mapNotNull null
            com.leanhome.feature.workout.mvi.PlayerExerciseSpec(
                exercise = exercise,
                plannedSets = plan.plannedSets,
                plannedReps = plan.plannedReps,
                restSeconds = plan.plannedRestSeconds,
            )
        } ?: emptyList()
        return WorkoutPlayerViewModel(
            sessionRepository = container.workoutSessionRepository,
            initialExercises = specs,
        ) as T
    }
}

/**
 * Factory for [ProgressDashboardViewModel].
 */
class ProgressDashboardViewModelFactory(
    private val userId: String,
    private val container: LeanHomeDataContainer,
    private val getDashboard: GetProgressDashboardUseCase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ProgressDashboardViewModel(
        userId = userId,
        getDashboard = getDashboard,
        weightLogRepository = container.weightLogRepository,
    ) as T
}
