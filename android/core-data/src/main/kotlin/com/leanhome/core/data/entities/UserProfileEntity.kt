package com.leanhome.core.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the user_profiles table.
 * Column names and types MUST match the SQLite schema in exercises_seed.db.
 */
@Entity(
    tableName = "user_profiles"
)
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val height_cm: Double,
    val weight_kg: Double,
    val country_code: String,
    val body_shape: String,
    val fitness_goal: String,
    val target_muscle_group: String?,
    val available_equipment: String, // JSON array
    val excluded_exercise_ids: String = "[]", // JSON array of exercise IDs
    val activity_level: String = "SEDENTARY",
    val days_per_week: Int,
    val daily_time_minutes: Int,
    val target_weight_kg: Double?,
    val target_days: Int?,
    val bmr: Double,
    val tdee: Double,
    val created_at: Long,
)
