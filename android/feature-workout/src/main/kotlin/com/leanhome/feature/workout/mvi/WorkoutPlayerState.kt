package com.leanhome.feature.workout.mvi

import com.leanhome.core.domain.model.Exercise
import com.leanhome.core.domain.model.SessionExercise

/**
 * Workout Player state machine.
 *
 * States:
 *   EXERCISE_ACTIVE — video loop, exercise info, form cues
 *   REST_INTERVAL   — countdown + haptic feedback
 *   SESSION_COMPLETE — summary → triggers post-workout check-in
 */
data class WorkoutPlayerState(
    val phase: PlayerPhase = PlayerPhase.EXERCISE_ACTIVE,
    val exercises: List<PlayerExerciseSpec> = emptyList(),
    val currentExerciseIndex: Int = 0,
    val currentSet: Int = 1,
    val totalSets: Int = 3,
    val restSecondsRemaining: Int = 0,
    val restTotal: Int = 60,
    val sessionElapsedSeconds: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val isVideoAvailable: Boolean = true,
    val isOffline: Boolean = false,
)

data class PlayerExerciseSpec(
    val exercise: Exercise,
    val plannedSets: Int,
    val plannedReps: Int,
    val restSeconds: Int,
    val weightPerRepKg: Double = 0.0,
) {
    fun toSessionExercise(): SessionExercise = SessionExercise(
        exerciseId = exercise.id,
        plannedSets = plannedSets,
        plannedReps = plannedReps,
        plannedRestSeconds = restSeconds,
    )
}

enum class PlayerPhase {
    EXERCISE_ACTIVE,
    REST_INTERVAL,
    SESSION_COMPLETE,
}
