package com.leanhome.feature.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.model.Equipment
import com.leanhome.feature.onboarding.mvi.OnboardingIntent
import com.leanhome.feature.onboarding.mvi.OnboardingState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquipmentSelectionStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("What equipment do you have?", style = MaterialTheme.typography.headlineSmall)
        Text("Select all that apply. Filter ensures only exercises you can do will appear.",
            style = MaterialTheme.typography.bodySmall)

        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Equipment.entries.forEach { eq ->
                FilterChip(
                    selected = eq in state.availableEquipment,
                    onClick = { onIntent(OnboardingIntent.EquipmentToggled(eq)) },
                    label = { Text(eq.name.replace('_', ' ')) },
                )
            }
        }

        if (state.errors.containsKey("equipment")) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(state.errors["equipment"]!!, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InjuryFlagsStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Any injuries or joint pain?", style = MaterialTheme.typography.headlineSmall)
        Text("We'll avoid exercises that strain those areas and suggest safer alternatives.",
            style = MaterialTheme.typography.bodySmall)

        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.leanhome.core.domain.model.Joint.entries.forEach { joint ->
                FilterChip(
                    selected = joint in state.injuryFlags,
                    onClick = { onIntent(OnboardingIntent.InjuryFlagToggled(joint)) },
                    label = { Text(joint.name.replace('_', ' ')) },
                )
            }
        }
    }
}
