package com.leanhome.feature.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.model.Exercise
import com.leanhome.feature.onboarding.mvi.OnboardingIntent
import com.leanhome.feature.onboarding.mvi.OnboardingState

/**
 * Step 7: Exercise Selection — browse the catalog filtered by the user's
 * equipment and injury constraints, and toggle any exercise to exclude it
 * from generated workouts.
 *
 * Every exercise that survives the equipment + injury filter is shown as a
 * card. Tapping a card marks it as excluded (the chip reads "Excluded"); the
 * default state is "Included" so the WorkoutGenerator uses every eligible
 * exercise unless the user opts out, matching the spec's "full mode".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseSelectionStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val userEquipment = state.availableEquipment.toSet()
    val injuryFlags = state.injuryFlags.toSet()

    val candidates: List<Exercise> = state.catalogExercises.filter { ex ->
        ex.isAvailableWith(userEquipment) && !ex.isContraindicatedFor(injuryFlags)
    }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Curate your exercises", style = MaterialTheme.typography.headlineSmall)
        Text(
            "These ${candidates.size} exercises match your equipment and injury profile. " +
                "Tap any you dislike or cannot perform to exclude them from your plan.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (candidates.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Loading the exercise catalog…",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(candidates, key = { it.id }) { exercise ->
                val excluded = exercise.id in state.excludedExerciseIds
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (excluded)
                            MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    onClick = { onIntent(OnboardingIntent.ExcludedExerciseToggled(exercise.id)) },
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            exercise.nameEn,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            exercise.primaryMuscles.joinToString { it.name.replace('_', ' ') },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Equipment: ${exercise.requiredEquipment.joinToString { it.name.replace('_', ' ') }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !excluded,
                                onClick = { onIntent(OnboardingIntent.ExcludedExerciseToggled(exercise.id)) },
                                label = { Text(if (excluded) "Excluded" else "Included") },
                            )
                        }
                    }
                }
            }
        }
    }
}
