package com.leanhome.feature.workout.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.model.FeelingTag
import com.leanhome.core.domain.repository.WorkoutSessionRepository
import com.leanhome.feature.workout.mvi.PlayerExerciseSpec
import kotlin.math.roundToInt

/**
 * SessionCheckinScreen — captures RPE 1-10, feeling tags, and actual sets/reps.
 * On submit, persists to Room via WorkoutSessionRepository and notifies caller.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionCheckinScreen(
    plannedExercises: List<PlayerExerciseSpec>,
    sessionId: String,
    repository: WorkoutSessionRepository? = null,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember {
        SessionCheckinViewModel(plannedExercises, sessionId, repository)
    }
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("How was your workout?", style = MaterialTheme.typography.headlineMedium)

        // RPE 1-10 slider
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rate of Perceived Exertion: ${state.rpe}", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = state.rpe.toFloat(),
                    onValueChange = { viewModel.onRpeChanged(it.roundToInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                )
                Text(rpeLabel(state.rpe), style = MaterialTheme.typography.bodySmall)
                Text(rpeScaleHint(state.rpe), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Feeling tags
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How did you feel?", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeelingTag.entries.forEach { tag ->
                        androidx.compose.material3.FilterChip(
                            selected = tag in state.feelingTags,
                            onClick = { viewModel.onToggleFeeling(tag) },
                            label = { Text(tag.name.replace('_', ' ')) },
                        )
                    }
                }
            }
        }

        // Actual sets/reps — one row per planned exercise
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Actual vs Planned", style = MaterialTheme.typography.titleMedium)
                state.actuals.forEachIndexed { idx, actual ->
                    val spec = plannedExercises.getOrNull(idx) ?: return@forEachIndexed
                    val planLabel = "Plan: ${spec.plannedSets}×${spec.plannedReps}"
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${spec.exercise.nameEn} ($planLabel)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = "${actual.sets}",
                            onValueChange = { v ->
                                viewModel.onActualSetsChanged(idx, v.toIntOrNull() ?: 0)
                            },
                            label = { Text("Sets") },
                            singleLine = true,
                            modifier = Modifier.width(80.dp),
                        )
                        OutlinedTextField(
                            value = "${actual.reps}",
                            onValueChange = { v ->
                                viewModel.onActualRepsChanged(idx, v.toIntOrNull() ?: 0)
                            },
                            label = { Text("Reps") },
                            singleLine = true,
                            modifier = Modifier.width(80.dp),
                        )
                    }
                }
            }
        }

        // Notes
        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotesChanged,
            label = { Text("Notes (optional, up to 500 chars)") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            singleLine = false,
        )

        Button(
            onClick = { viewModel.submit(onSubmit) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save & Continue")
        }
    }
}

private fun rpeLabel(rpe: Int): String = when (rpe) {
    in 1..2 -> "Very light"
    in 3..4 -> "Light"
    in 5..6 -> "Moderate"
    7 -> "Challenging but controlled"
    in 8..9 -> "Hard"
    10 -> "Maximum effort / failure"
    else -> ""
}

private fun rpeScaleHint(rpe: Int): String = when (rpe) {
    7 -> "(matches spec example)"
    else -> ""
}
