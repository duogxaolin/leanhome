package com.leanhome.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionExercise(
    val exerciseId: String,
    val plannedSets: Int,
    val plannedReps: Int,
    val plannedRestSeconds: Int = 60,
    val actualSetsCompleted: Int = 0,
    val actualRepsCompleted: List<Int> = emptyList(),
)

@Serializable
enum class SplitName {
    FULL_BODY_A,
    FULL_BODY_B,
    UPPER_A,
    UPPER_B,
    LOWER_A,
    LOWER_B,
    PUSH,
    PULL,
    LEGS,
    FOCUS;

    companion object {
        fun fromString(value: String): SplitName =
            entries.first { it.name == value.uppercase() }
    }
}

@Serializable
enum class SessionStatus {
    PENDING,
    COMPLETED,
    SKIPPED;

    companion object {
        fun fromString(value: String): SessionStatus =
            entries.first { it.name == value.uppercase() }
    }
}

@Serializable
enum class FeelingTag {
    GREAT,
    TIRED,
    JOINT_DISCOMFORT,
    ENERGIZED,
    BREEZY,
    BRUTAL;

    companion object {
        fun fromString(value: String): FeelingTag =
            entries.first { it.name == value.uppercase() }
    }
}

@Serializable
data class WorkoutSession(
    val id: String,
    val userId: String,
    val scheduledDate: String, // ISO 8601 date YYYY-MM-DD
    val splitName: SplitName,
    val status: SessionStatus,
    val exercises: List<SessionExercise> = emptyList(),
    val completedDurationSeconds: Int? = null,
    val rpeScore: Int? = null,
    val feedbackTags: List<FeelingTag> = emptyList(),
    val feedbackNotes: String? = null,
    val createdAt: Long,
)
