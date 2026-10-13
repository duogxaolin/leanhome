package com.leanhome.feature.workout.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.model.WorkoutSession
import com.leanhome.core.domain.repository.ExerciseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WorkoutDetailScreen — shows the list of exercises in a session,
 * with planned sets/reps and video preview.
 */
@Composable
fun WorkoutDetailScreen(
    session: WorkoutSession,
    exerciseRepository: ExerciseRepository,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Workout: ${session.splitName.name.replace('_', ' ')}",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Scheduled: ${session.scheduledDate}",
            style = MaterialTheme.typography.bodyMedium)
        Text("Status: ${session.status.name}",
            style = MaterialTheme.typography.bodyMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Exercises", style = MaterialTheme.typography.titleMedium)
                session.exercises.forEachIndexed { idx, plan ->
                    Text("${idx + 1}. ${plan.exerciseId} — ${plan.plannedSets}×${plan.plannedReps}",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth()) {
            Text("Start Workout")
        }
    }
}
