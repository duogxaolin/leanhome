package com.leanhome.feature.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.engine.EnergyBalanceEngine
import com.leanhome.core.domain.model.FitnessGoal
import com.leanhome.core.ui.theme.SafetyDanger
import com.leanhome.core.ui.theme.SafetyWarning
import com.leanhome.feature.onboarding.mvi.OnboardingIntent
import com.leanhome.feature.onboarding.mvi.OnboardingState
import kotlin.math.roundToInt

/**
 * Step 7: Schedule — days per week and daily time.
 */
@Composable
fun ScheduleStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your weekly schedule", style = MaterialTheme.typography.headlineSmall)

        Text("Days per week: ${state.daysPerWeek}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = state.daysPerWeek.toFloat(),
            onValueChange = { onIntent(OnboardingIntent.DaysPerWeekChanged(it.roundToInt())) },
            valueRange = 2f..6f,
            steps = 3,
        )

        val splitName = when (state.daysPerWeek) {
            in 2..3 -> "Full Body A/B alternating"
            4 -> "Upper / Lower split"
            5, 6 -> "Push / Pull / Legs"
            else -> ""
        }
        Text("Recommended split: $splitName", style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(8.dp))
        Text("Daily time: ${state.dailyTimeMinutes} minutes", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = state.dailyTimeMinutes.toFloat(),
            onValueChange = { onIntent(OnboardingIntent.DailyTimeChanged(it.roundToInt())) },
            valueRange = 15f..120f,
            steps = 20,
        )
    }
}

/**
 * Step 8 (skip for HEALTH_MAINTENANCE): Timeline Slider with safety warnings.
 *
 * The deficit calculation runs against the user's TDEE. If the chosen
 * timeline is aggressive (deficit > 25% of TDEE or > 1000 kcal), the UI
 * renders a mandatory clinical disclaimer with red/orange styling and
 * requires acknowledgement.
 */
@Composable
fun TimelineSliderStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    accumulator: EnergyBalanceEngine,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How fast do you want results?", style = MaterialTheme.typography.headlineSmall)

        Text("Timeline: ${state.timelineDays} days", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = state.timelineDays.toFloat(),
            onValueChange = { onIntent(OnboardingIntent.TimelineDaysChanged(it.roundToInt())) },
            valueRange = 7f..180f,
            steps = 50,
        )

        // Show deficit info
        state.deficitResult?.let { result ->
            when (result.warningLevel) {
                EnergyBalanceEngine.DeficitResult.WarningLevel.AT_CEILING -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SafetyWarning.copy(alpha = 0.18f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("At safe ceiling", style = MaterialTheme.typography.titleMedium)
                            Text("Deficit: %.0f kcal/day (25%% TDEE)".format(result.dailyDeficit),
                                style = MaterialTheme.typography.bodyMedium)
                            Text("Acceptable but at the upper limit of safe.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                EnergyBalanceEngine.DeficitResult.WarningLevel.EXTREME -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SafetyDanger.copy(alpha = 0.18f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("⚠️ HIGH RISK / EXTREME DEFICIT",
                                style = MaterialTheme.typography.titleMedium, color = SafetyDanger)
                            Text("Required deficit: %.0f kcal/day".format(result.dailyDeficit),
                                style = MaterialTheme.typography.bodyMedium)
                            Text("Safe maximum: %.0f kcal/day".format(result.clampedDeficit ?: 0.0),
                                style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "⚠ Clinical disclaimer: This timeline exceeds safe metabolic limits. " +
                                    "It will be clamped to %.0f kcal/day. " +
                                    "True fat loss at this rate REQUIRE dietary intervention (caloric restriction) " +
                                    "beyond what workout alone can safely achieve. " +
                                    "Risk of muscle loss and metabolic compensation. " +
                                    "Consider extending the timeline.".format(result.clampedDeficit ?: 0.0),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                EnergyBalanceEngine.DeficitResult.WarningLevel.NONE -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Safe deficit", style = MaterialTheme.typography.titleMedium)
                            Text("Deficit: %.0f kcal/day".format(result.dailyDeficit),
                                style = MaterialTheme.typography.bodyMedium)
                            Text("Within physiological safety limits.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Step 9: Review and submit.
 */
@Composable
fun ReviewSummaryStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Review your profile", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Name: ${state.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Age: ${state.age}", style = MaterialTheme.typography.bodyMedium)
                Text("Gender: ${state.gender.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Height: ${state.heightCm} cm", style = MaterialTheme.typography.bodyMedium)
                Text("Weight: ${state.weightKg} kg", style = MaterialTheme.typography.bodyMedium)
                Text("Country: ${state.countryCode}", style = MaterialTheme.typography.bodyMedium)
                Text("Archetype: ${state.bodyArchetype.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Goal: ${state.fitnessGoal.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Equipment: ${state.availableEquipment.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Injuries: ${state.injuryFlags.joinToString { it.name }.ifEmpty { "None" }}",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Days per week: ${state.daysPerWeek}", style = MaterialTheme.typography.bodyMedium)
                Text("Daily time: ${state.dailyTimeMinutes} min", style = MaterialTheme.typography.bodyMedium)
                state.bmi?.let { Text("BMI: %.1f".format(it), style = MaterialTheme.typography.bodyMedium) }
                state.bmiClassification?.let { Text("Category: ${it.label}", style = MaterialTheme.typography.bodyMedium) }
                if (state.fitnessGoal == FitnessGoal.FAT_LOSS || state.fitnessGoal == FitnessGoal.WEIGHT_CUT) {
                    Text("Timeline: ${state.timelineDays} days", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text("Tap Finish to generate your personalized workout plan.",
            style = MaterialTheme.typography.bodySmall)
    }
}
