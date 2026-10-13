package com.leanhome.feature.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.model.FitnessGoal
import com.leanhome.core.domain.model.TargetMuscleGroup
import com.leanhome.feature.onboarding.mvi.OnboardingIntent
import com.leanhome.feature.onboarding.mvi.OnboardingState

/**
 * Step 4: Goal Selection.
 * HEALTH_MAINTENANCE skips the timeline slider step.
 * TARGET_ZONE_TONING enables the muscle group selection.
 */
@Composable
fun GoalSelectionStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("What's your goal?", style = MaterialTheme.typography.headlineSmall)

        FitnessGoal.entries.forEach { goal ->
            val selected = state.fitnessGoal == goal
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = { onIntent(OnboardingIntent.GoalSelected(goal)) },
                    )
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = { onIntent(OnboardingIntent.GoalSelected(goal)) })
                    Spacer(modifier = Modifier.height(0.dp).fillMaxWidth(0).padding(8.dp))
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(goalTitle(goal), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(goalDescription(goal), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (state.fitnessGoal == FitnessGoal.TARGET_ZONE_TONING) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Pick your target muscle focus:", style = MaterialTheme.typography.titleMedium)
            TargetMuscleGroup.entries.forEach { tg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.targetMuscleGroup == tg,
                            onClick = { onIntent(OnboardingIntent.TargetMuscleSelected(tg)) },
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.targetMuscleGroup == tg,
                        onClick = { onIntent(OnboardingIntent.TargetMuscleSelected(tg)) })
                    Text(tg.name, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private fun goalTitle(g: FitnessGoal): String = when (g) {
    FitnessGoal.HEALTH_MAINTENANCE -> "Stay Healthy"
    FitnessGoal.FAT_LOSS -> "Fat Loss"
    FitnessGoal.WEIGHT_CUT -> "Aggressive Weight Cut"
    FitnessGoal.TARGET_ZONE_TONING -> "Tone a Specific Zone"
}

private fun goalDescription(g: FitnessGoal): String = when (g) {
    FitnessGoal.HEALTH_MAINTENANCE -> "Build/maintain general conditioning. Zero calorie deficit. Cardiovascular focus."
    FitnessGoal.FAT_LOSS -> "Gradual, safe fat loss over 60+ days. Balanced deficit."
    FitnessGoal.WEIGHT_CUT -> "Short-term (30-day) targeted cut. Risk-factor warnings shown."
    FitnessGoal.TARGET_ZONE_TONING -> "Build and shape a specific muscle group while keeping balance."
}
