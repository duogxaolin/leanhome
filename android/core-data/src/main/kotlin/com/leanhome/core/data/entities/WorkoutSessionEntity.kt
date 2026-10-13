package com.leanhome.core.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the workout_sessions table.
 * Column names and types MUST match the SQLite schema in exercises_seed.db.
 */
@Entity(
    tableName = "workout_sessions",
    indices = [
        Index(value = ["user_id"], name = "idx_sessions_user"),
        Index(value = ["status"], name = "idx_sessions_status"),
    ]
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val user_id: String,
    val scheduled_date: String, // ISO 8601 date YYYY-MM-DD
    val split_name: String,
    val status: String,
    val exercises: String = "[]", // JSON array of session_exercise objects
    val completed_duration_seconds: Int?,
    val rpe_score: Int?,
    val feedback_tags: String?, // JSON array
    val feedback_notes: String?,
    val created_at: Long,
)
