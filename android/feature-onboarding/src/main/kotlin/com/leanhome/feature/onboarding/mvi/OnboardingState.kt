package com.leanhome.feature.onboarding.mvi

import com.leanhome.core.domain.model.ActivityLevel
import com.leanhome.core.domain.model.BodyArchetype
import com.leanhome.core.domain.model.Equipment
import com.leanhome.core.domain.model.FitnessGoal
import com.leanhome.core.domain.model.Gender
import com.leanhome.core.domain.model.Joint
import com.leanhome.core.domain.model.TargetMuscleGroup

/**
 * The complete onboarding state for the multi-step flow.
 * Includes all validation-relevant inputs.
 */
data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.BIOMETRIC_INPUT,
    val name: String = "",
    val age: Int = 25,
    val gender: Gender = Gender.MALE,
    val heightCm: Double = 175.0,
    val weightKg: Double = 75.0,
    val countryCode: String = "US",
    val bodyArchetype: BodyArchetype = BodyArchetype.RECTANGLE,
    val fitnessGoal: FitnessGoal = FitnessGoal.HEALTH_MAINTENANCE,
    val targetMuscleGroup: TargetMuscleGroup? = null,
    val availableEquipment: List<Equipment> = listOf(Equipment.BODYWEIGHT_ONLY),
    val injuryFlags: List<Joint> = emptyList(),
    val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    val daysPerWeek: Int = 3,
    val dailyTimeMinutes: Int = 30,
    val timelineDays: Int = 60,
    val isComplete: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val bmi: Double? = null,
    val bmiClassification: com.leanhome.core.domain.engine.EnergyBalanceEngine.BmiClassification? = null,
    val deficitResult: com.leanhome.core.domain.engine.EnergyBalanceEngine.DeficitResult? = null,
    val safetyWarning: String? = null,
) {
    companion object {
        const val MIN_NAME_LEN = 1
        const val MAX_NAME_LEN = 50
        const val MIN_AGE = 13
        const val MAX_AGE = 99
        const val MIN_HEIGHT = 100.0
        const val MAX_HEIGHT = 250.0
        const val MIN_WEIGHT = 30.0
        const val MAX_WEIGHT = 300.0
    }
}

enum class OnboardingStep {
    BIOMETRIC_INPUT,
    WHO_BMI_GAUGE,
    ARCHETYPE_PICKER,
    GOAL_SELECTION,
    EQUIPMENT_SELECTION,
    INJURY_FLAGS,
    SCHEDULE,
    TIMELINE_SLIDER,
    REVIEW_SUMMARY,
}
