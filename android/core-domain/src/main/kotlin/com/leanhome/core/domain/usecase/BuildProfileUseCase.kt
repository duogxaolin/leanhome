package com.leanhome.core.domain.usecase

import com.leanhome.core.domain.engine.EnergyBalanceEngine
import com.leanhome.core.domain.model.ActivityLevel
import com.leanhome.core.domain.model.BodyArchetype
import com.leanhome.core.domain.model.Equipment
import com.leanhome.core.domain.model.FitnessGoal
import com.leanhome.core.domain.model.Gender
import com.leanhome.core.domain.model.Joint
import com.leanhome.core.domain.model.UserProfile

/**
 * Validates user inputs during onboarding per spec:
 *   name 1-50 chars, age 13-99, height 100-250, weight 30-300.
 */
data class OnboardingInput(
    val name: String = "",
    val age: Int = 0,
    val gender: Gender = Gender.MALE,
    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0,
    val countryCode: String = "",
    val bodyArchetype: BodyArchetype = BodyArchetype.RECTANGLE,
    val fitnessGoal: FitnessGoal = FitnessGoal.HEALTH_MAINTENANCE,
    val targetMuscleGroup: com.leanhome.core.domain.model.TargetMuscleGroup? = null,
    val availableEquipment: List<Equipment> = listOf(Equipment.BODYWEIGHT_ONLY),
    val excludedExerciseIds: List<String> = emptyList(),
    val injuryFlags: List<Joint> = emptyList(),
    val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    val daysPerWeek: Int = 3,
    val dailyTimeMinutes: Int = 30,
    val targetWeightKg: Double? = null,
    val timelineDays: Int? = null,
)

data class ValidationResult(
    val isValid: Boolean,
    val errors: Map<String, String> = emptyMap(),
)

object OnboardingValidator {
    const val MIN_AGE = 13
    const val MAX_AGE = 99
    const val MIN_HEIGHT = 100.0
    const val MAX_HEIGHT = 250.0
    const val MIN_WEIGHT = 30.0
    const val MAX_WEIGHT = 300.0

    fun validate(input: OnboardingInput): ValidationResult {
        val errors = mutableMapOf<String, String>()

        val name = input.name.trim()
        if (name.isEmpty() || name.length > UserProfile.MAX_NAME_LEN) {
            errors["name"] = "Name must be ${UserProfile.MIN_NAME_LEN}-${UserProfile.MAX_NAME_LEN} characters"
        }

        if (input.age < MIN_AGE || input.age > MAX_AGE) {
            errors["age"] = "Age must be $MIN_AGE-$MAX_AGE"
        }

        if (input.heightCm < MIN_HEIGHT || input.heightCm > MAX_HEIGHT) {
            errors["height"] = "Height must be $MIN_HEIGHT-$MAX_HEIGHT cm"
        }

        if (input.weightKg < MIN_WEIGHT || input.weightKg > MAX_WEIGHT) {
            errors["weight"] = "Weight must be $MIN_WEIGHT-$MAX_WEIGHT kg"
        }

        if (!input.countryCode.matches(Regex("^[A-Z]{2}$"))) {
            errors["countryCode"] = "Country must be a 2-letter ISO code"
        }

        if (input.availableEquipment.isEmpty()) {
            errors["equipment"] = "Select at least one equipment type"
        }

        if (input.daysPerWeek < UserProfile.MIN_DAYS_PER_WEEK || input.daysPerWeek > UserProfile.MAX_DAYS_PER_WEEK) {
            errors["daysPerWeek"] = "Days per week must be ${UserProfile.MIN_DAYS_PER_WEEK}-${UserProfile.MAX_DAYS_PER_WEEK}"
        }

        if (input.dailyTimeMinutes < UserProfile.MIN_TIME_MINUTES || input.dailyTimeMinutes > UserProfile.MAX_TIME_MINUTES) {
            errors["dailyTimeMinutes"] = "Daily time must be ${UserProfile.MIN_TIME_MINUTES}-${UserProfile.MAX_TIME_MINUTES} min"
        }

        // Target weight: only required for fat-loss / weight-cut goals. Must
        // be below the current weight (you cannot gain toward a loss target)
        // and above the physiological minimum enforced by the engine.
        if (input.fitnessGoal == FitnessGoal.FAT_LOSS || input.fitnessGoal == FitnessGoal.WEIGHT_CUT) {
            val target = input.targetWeightKg
            if (target == null) {
                errors["targetWeightKg"] = "Enter a target weight"
            } else if (target >= input.weightKg) {
                errors["targetWeightKg"] = "Target must be below your current weight"
            } else if (target < UserProfile.MIN_WEIGHT) {
                errors["targetWeightKg"] = "Target must be at least ${UserProfile.MIN_WEIGHT} kg"
            }
            if (input.timelineDays == null || input.timelineDays <= 0) {
                errors["timelineDays"] = "Choose a timeline (days)"
            }
        }

        return ValidationResult(errors.isEmpty(), errors)
    }
}

/**
 * Use case: builds a complete UserProfile from onboarding input, computing
 * BMR and TDEE for the user.
 */
class BuildProfileUseCase(private val engine: EnergyBalanceEngine = EnergyBalanceEngine()) {

    operator fun invoke(input: OnboardingInput, createdAt: Long): UserProfile {
        val bmr = engine.calculateBmr(input.gender, input.age, input.weightKg, input.heightCm)
        val tdee = engine.calculateTdee(bmr, input.activityLevel)
        return UserProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = input.name.trim(),
            age = input.age,
            gender = input.gender,
            heightCm = input.heightCm,
            weightKg = input.weightKg,
            countryCode = input.countryCode.uppercase(),
            bodyArchetype = input.bodyArchetype,
            fitnessGoal = input.fitnessGoal,
            targetMuscleGroup = input.targetMuscleGroup,
            availableEquipment = input.availableEquipment,
            excludedExerciseIds = input.excludedExerciseIds,
            injuryFlags = input.injuryFlags,
            activityLevel = input.activityLevel,
            daysPerWeek = input.daysPerWeek,
            dailyTimeMinutes = input.dailyTimeMinutes,
            targetWeightKg = if (input.fitnessGoal == FitnessGoal.FAT_LOSS || input.fitnessGoal == FitnessGoal.WEIGHT_CUT) {
                input.targetWeightKg
            } else null,
            targetDays = if (input.fitnessGoal == FitnessGoal.FAT_LOSS || input.fitnessGoal == FitnessGoal.WEIGHT_CUT) {
                input.timelineDays
            } else null,
            bmr = bmr,
            tdee = tdee,
            createdAt = createdAt,
        )
    }
}
