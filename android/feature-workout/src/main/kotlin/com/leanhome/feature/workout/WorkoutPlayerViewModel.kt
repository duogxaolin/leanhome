package com.leanhome.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leanhome.core.domain.model.Exercise
import com.leanhome.core.domain.model.SessionExercise
import com.leanhome.core.domain.model.WorkoutSession
import com.leanhome.core.domain.repository.WorkoutSessionRepository
import com.leanhome.feature.workout.mvi.PlayerExerciseSpec
import com.leanhome.feature.workout.mvi.PlayerPhase
import com.leanhome.feature.workout.mvi.WorkoutPlayerIntent
import com.leanhome.feature.workout.mvi.WorkoutPlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * WorkoutPlayerViewModel — orchestrates the player state machine
 * (EXERCISE_ACTIVE → REST_INTERVAL → SESSION_COMPLETE) and persists
 * session progress through [WorkoutSessionRepository].
 */
class WorkoutPlayerViewModel(
    private val sessionRepository: WorkoutSessionRepository? = null,
    initialExercises: List<PlayerExerciseSpec> = emptyList(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        WorkoutPlayerState(
            exercises = initialExercises,
            totalSets = initialExercises.firstOrNull()?.plannedSets ?: 3,
        )
    )
    val state: StateFlow<WorkoutPlayerState> = _state.asStateFlow()

    fun handleIntent(intent: WorkoutPlayerIntent) {
        when (intent) {
            WorkoutPlayerIntent.DoneSet -> doneSet()
            WorkoutPlayerIntent.SkipRest -> skipRest()
            WorkoutPlayerIntent.AddThirtySeconds -> addRestSeconds(30)
            WorkoutPlayerIntent.SubtractFifteenSeconds -> addRestSeconds(-15)
            WorkoutPlayerIntent.EndSession -> endSession()
            WorkoutPlayerIntent.RestTick -> restTick()
            is WorkoutPlayerIntent.SkipToExercise -> skipToExercise(intent.index)
        }
    }

    private fun doneSet() {
        val s = _state.value
        if (s.phase != PlayerPhase.EXERCISE_ACTIVE) return

        // Record completion of current set
        val remainingTotalSets = s.currentSet < s.totalSets
        _state.update {
            it.copy(
                phase = PlayerPhase.REST_INTERVAL,
                restSecondsRemaining = it.exercises.getOrNull(s.currentExerciseIndex)?.restSeconds ?: 60,
                restTotal = it.exercises.getOrNull(s.currentExerciseIndex)?.restSeconds ?: 60,
            )
        }
    }

    private fun skipRest() {
        _state.update { it.copy(restSecondsRemaining = 0) }
        maybeAdvanceFromRest()
    }

    private fun addRestSeconds(delta: Int) {
        _state.update {
            val newRemaining = (it.restSecondsRemaining + delta).coerceAtLeast(0)
            it.copy(restSecondsRemaining = newRemaining)
        }
    }

    private fun restTick() {
        val s = _state.value
        if (s.phase != PlayerPhase.REST_INTERVAL) return
        val newRemaining = (s.restSecondsRemaining - 1).coerceAtLeast(0)
        _state.update { it.copy(restSecondsRemaining = newRemaining) }
        if (newRemaining == 0) maybeAdvanceFromRest()
    }

    private fun maybeAdvanceFromRest() {
        val s = _state.value
        val spec = s.exercises.getOrNull(s.currentExerciseIndex)
        if (s.currentSet < (spec?.plannedSets ?: 3)) {
            // More sets to do on the same exercise
            _state.update {
                it.copy(
                    phase = PlayerPhase.EXERCISE_ACTIVE,
                    currentSet = it.currentSet + 1,
                )
            }
        } else if (s.currentExerciseIndex < s.exercises.lastIndex) {
            // Move to next exercise
            _state.update {
                it.copy(
                    phase = PlayerPhase.EXERCISE_ACTIVE,
                    currentExerciseIndex = it.currentExerciseIndex + 1,
                    currentSet = 1,
                    totalSets = it.exercises.getOrNull(it.currentExerciseIndex + 1)?.plannedSets ?: 3,
                )
            }
        } else {
            // Session complete
            endSession()
        }
    }

    private fun skipToExercise(index: Int) {
        _state.update {
            it.copy(
                phase = PlayerPhase.EXERCISE_ACTIVE,
                currentExerciseIndex = index.coerceIn(0, it.exercises.lastIndex),
                currentSet = 1,
                totalSets = it.exercises.getOrNull(index)?.plannedSets ?: 3,
            )
        }
    }

    private fun endSession() {
        _state.update { it.copy(phase = PlayerPhase.SESSION_COMPLETE) }
        // Persist the completed session (caller wires repository)
    }

    fun startRestCountdown() {
        viewModelScope.launch {
            while (_state.value.phase == PlayerPhase.REST_INTERVAL && _state.value.restSecondsRemaining > 0) {
                delay(1000)
                restTick()
            }
        }
    }

    fun completeModule(savedSession: WorkoutSession? = null) {
        savedSession?.let { sessionRepository?.let { repo -> viewModelScope.launch { repo.update(it) } } }
        _state.update { it.copy(phase = PlayerPhase.SESSION_COMPLETE) }
    }
}
