package com.leanhome.feature.workout.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.model.WorkoutSession
import com.leanhome.core.domain.repository.ExerciseRepository
import com.leanhome.core.domain.repository.WorkoutSessionRepository

/**
 * WorkoutDetailScreen — shows the list of exercises in a session,
 * with planned sets/reps and resolved exercise names.
 *
 * Loads the session by [sessionId] from [sessionRepository] and resolves each
 * planned exercise's display name via [exerciseRepository] so the user sees
 * "Push-up" rather than the raw ID.
 */
@Composable
fun WorkoutDetailScreen(
    sessionId: String,
    sessionRepository: WorkoutSessionRepository,
    exerciseRepository: ExerciseRepository,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var session by remember { mutableStateOf<WorkoutSession?>(null) }

    LaunchedEffect(sessionId) {
        session = sessionRepository.getById(sessionId)
    }

    val current = session
    if (current == null) {
        Column(modifier = modifier.padding(16.dp)) {
            Text("Loading workout…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    // Resolve exercise names once (local DB, fast).
    val exerciseNames by remember {
        mutableStateOf(
            kotlinx.coroutines.runBlocking {
                exerciseRepository.getAllOnce().associate { it.id to it.nameEn }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Workout: ${current.splitName.name.replace('_', ' ')}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text("Scheduled: ${current.scheduledDate}", style = MaterialTheme.typography.bodyMedium)
        val splitNameLabel = when (current.splitName) {
            com.leanhome.core.domain.model.SplitName.FULL_BODY_A -> "Full Body A"
            com.leanhome.core.domain.model.SplitName.FULL_BODY_B -> "Full Body B"
            else -> current.splitName.name.replace('_', ' ')
        }
        Text(splitNameLabel, style = MaterialTheme.typography.bodyMedium)
        Text("Status: ${current.status.name}", style = MaterialTheme.typography.bodyMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Exercises", style = MaterialTheme.typography.titleMedium)
                current.exercises.forEachIndexed { idx, plan ->
                    val name = exerciseNames[plan.exerciseId] ?: plan.exerciseId
                    Text(
                        "${idx + 1}. $name — ${plan.plannedSets}×${plan.plannedReps}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (current.exercises.isEmpty()) {
                    Text("No exercises assigned yet.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onStartWorkout,
            modifier = Modifier.fillMaxWidth(),
            enabled = current.exercises.isNotEmpty(),
        ) {
            Text("Start Workout")
        }
    }
}
