package com.leanhome.feature.onboarding.mvi

import com.leanhome.core.domain.model.ActivityLevel
import com.leanhome.core.domain.model.BodyArchetype
import com.leanhome.core.domain.model.Equipment
import com.leanhome.core.domain.model.FitnessGoal
import com.leanhome.core.domain.model.Gender
import com.leanhome.core.domain.model.Joint
import com.leanhome.core.domain.model.Exercise

/**
 * MVI Intent — user actions that trigger state transitions.
 * Sealed class so the reducer covers all possible events.
 */
sealed class OnboardingIntent {
    data class NameChanged(val value: String) : OnboardingIntent()
    data class AgeChanged(val value: Int) : OnboardingIntent()
    data class GenderChanged(val value: Gender) : OnboardingIntent()
    data class HeightChanged(val value: Double) : OnboardingIntent()
    data class WeightChanged(val value: Double) : OnboardingIntent()
    data class CountryChanged(val value: String) : OnboardingIntent()
    data class ArchetypeSelected(val value: BodyArchetype) : OnboardingIntent()
    data class GoalSelected(val value: FitnessGoal) : OnboardingIntent()
    data class TargetMuscleSelected(val value: TargetMuscleGroup?) : OnboardingIntent()
    data class EquipmentToggled(val value: Equipment) : OnboardingIntent()
    data class InjuryFlagToggled(val value: Joint) : OnboardingIntent()
    data class ActivityLevelChanged(val value: ActivityLevel) : OnboardingIntent()
    data class DaysPerWeekChanged(val value: Int) : OnboardingIntent()
    data class DailyTimeChanged(val value: Int) : OnboardingIntent()
    data class TargetWeightChanged(val value: Double?) : OnboardingIntent()
    data class ExcludedExerciseToggled(val exerciseId: String) : OnboardingIntent()
    data class CatalogExercisesLoaded(val exercises: List<Exercise>) : OnboardingIntent()
    object GoToNextStep : OnboardingIntent()
    object GoToPrevStep : OnboardingIntent()
    object CompleteOnboarding : OnboardingIntent()
}

/**
 * One-time effects (navigation, etc.) emitted outside of state.
 */
sealed class OnboardingEffect {
    /** Onboarding finished and the profile + weekly plan were persisted. */
    data class OnboardingComplete(val userId: String) : OnboardingEffect()
}
