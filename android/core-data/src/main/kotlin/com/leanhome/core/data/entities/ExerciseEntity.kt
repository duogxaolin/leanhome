package com.leanhome.core.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the exercises catalog table.
 * Column names and types MUST match the SQLite schema in exercises_seed.db
 * for Room's createFromAsset to work.
 *
 * JSON array columns are stored as TEXT with JSON string values.
 */
@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["required_equipment"], name = "idx_exercises_equipment"),
        Index(value = ["difficulty_level"], name = "idx_exercises_difficulty"),
    ]
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name_en: String,
    val name_vi: String,
    val primary_muscles: String, // JSON array
    val secondary_muscles: String, // JSON array
    val required_equipment: String, // JSON array
    val contraindicated_joints: String, // JSON array
    val difficulty_level: String,
    val video_url: String,
    val fallback_thumbnail_url: String,
    val common_mistakes: String, // JSON array
    val correct_form_cues: String, // JSON array
)
