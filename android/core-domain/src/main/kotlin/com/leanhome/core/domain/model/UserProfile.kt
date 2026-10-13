package com.leanhome.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val age: Int,
    val gender: Gender,
    val heightCm: Double,
    val weightKg: Double,
    val countryCode: String,
    val bodyArchetype: BodyArchetype,
    val fitnessGoal: FitnessGoal,
    val targetMuscleGroup: TargetMuscleGroup? = null,
    val availableEquipment: List<Equipment>,
    val injuryFlags: List<Joint>,
    val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    val daysPerWeek: Int,
    val dailyTimeMinutes: Int,
    val targetWeightKg: Double? = null,
    val targetDays: Int? = null,
    val bmr: Double,
    val tdee: Double,
    val createdAt: Long,
) {
    companion object {
        const val MIN_AGE = 13
        const val MAX_AGE = 99
        const val MIN_HEIGHT = 100.0
        const val MAX_HEIGHT = 250.0
        const val MIN_WEIGHT = 30.0
        const val MAX_WEIGHT = 300.0
        const val MIN_NAME_LEN = 1
        const val MAX_NAME_LEN = 50
        const val MIN_DAYS_PER_WEEK = 2
        const val MAX_DAYS_PER_WEEK = 6
        const val MIN_TIME_MINUTES = 15
        const val MAX_TIME_MINUTES = 120
    }
}
