package com.leanhome.feature.workout.checkin

import com.leanhome.core.domain.model.FeelingTag
import com.leanhome.core.domain.model.SessionExercise
import com.leanhome.core.domain.model.SessionStatus
import com.leanhome.core.domain.repository.WorkoutSessionRepository
import com.leanhome.feature.workout.mvi.PlayerExerciseSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseActual(
    val sets: Int,
    val reps: Int,
)

data class SessionCheckinState(
    val rpe: Int = 5,
    val feelingTags: Set<FeelingTag> = emptySet(),
    val actuals: List<ExerciseActual> = emptyList(),
    val notes: String = "",
)

class SessionCheckinViewModel(
    private val plannedExercises: List<PlayerExerciseSpec>,
    private val sessionId: String,
    private val repository: WorkoutSessionRepository?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(
        SessionCheckinState(
            actuals = plannedExercises.map { ExerciseActual(it.plannedSets, it.plannedReps) },
        )
    )
    val state: StateFlow<SessionCheckinState> = _state.asStateFlow()

    fun onRpeChanged(rpe: Int) {
        _state.update { it.copy(rpe = rpe.coerceIn(1, 10)) }
    }

    fun onToggleFeeling(tag: FeelingTag) {
        _state.update { current ->
            val updated = if (tag in current.feelingTags) current.feelingTags - tag
            else current.feelingTags + tag
            current.copy(feelingTags = updated)
        }
    }

    fun onActualSetsChanged(index: Int, sets: Int) {
        _state.update { current ->
            val mutable = current.actuals.toMutableList()
            if (index in mutable.indices) {
                mutable[index] = mutable[index].copy(sets = sets.coerceAtLeast(0))
            }
            current.copy(actuals = mutable)
        }
    }

    fun onActualRepsChanged(index: Int, reps: Int) {
        _state.update { current ->
            val mutable = current.actuals.toMutableList()
            if (index in mutable.indices) {
                mutable[index] = mutable[index].copy(reps = reps.coerceAtLeast(0))
            }
            current.copy(actuals = mutable)
        }
    }

    fun onNotesChanged(text: String) {
        _state.update { it.copy(notes = text.take(500)) }
    }

    fun submit(onSubmit: () -> Unit) {
        val s = _state.value
        val sessionExercises = plannedExercises.mapIndexed { idx, spec ->
            val actual = s.actuals.getOrNull(idx) ?: ExerciseActual(spec.plannedSets, spec.plannedReps)
            SessionExercise(
                exerciseId = spec.exercise.id,
                plannedSets = spec.plannedSets,
                plannedReps = spec.plannedReps,
                plannedRestSeconds = spec.restSeconds,
                actualSetsCompleted = actual.sets,
                actualRepsCompleted = List(actual.sets) { actual.reps },
            )
        }
        // Persist: update is performed by caller/SessionActivity which holds the existing session
        if (repository != null) {
            scope.launch {
                repository.updateRpeAndStatus(
                    sessionId = sessionId,
                    rpeScore = s.rpe,
                    status = SessionStatus.COMPLETED,
                    completedDurationSeconds = null,
                    feedbackTags = s.feelingTags.toList(),
                    feedbackNotes = s.notes.takeIf { it.isNotBlank() },
                )
            }
        } else {
            onSubmit()
            return
        }
        // onSubmit is also called via onComplete callback by repository-success path.
        onSubmit()
    }
}
