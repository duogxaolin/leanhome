package com.leanhome.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.engine.EnergyBalanceEngine
import com.leanhome.feature.onboarding.mvi.OnboardingEffect
import com.leanhome.feature.onboarding.mvi.OnboardingIntent
import com.leanhome.feature.onboarding.mvi.OnboardingStep
import com.leanhome.feature.onboarding.mvi.OnboardingViewModel
import com.leanhome.feature.onboarding.steps.ArchetypePickerStep
import com.leanhome.feature.onboarding.steps.BiometricInputStep
import com.leanhome.feature.onboarding.steps.EquipmentSelectionStep
import com.leanhome.feature.onboarding.steps.GoalSelectionStep
import com.leanhome.feature.onboarding.steps.InjuryFlagsStep
import com.leanhome.feature.onboarding.steps.ScheduleStep
import com.leanhome.feature.onboarding.steps.ReviewSummaryStep
import com.leanhome.feature.onboarding.steps.TimelineSliderStep
import com.leanhome.feature.onboarding.steps.WhoBmiGaugeStep

/**
 * Main Onboarding screen — drives the multi-step paged flow via the
 * [OnboardingViewModel] (MVI). Shows progress, step content, and
 * Back / Next / Finish buttons.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = OnboardingViewModel(),
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val engine = remember { EnergyBalanceEngine() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.OnboardingComplete -> onComplete()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Progress indicator
        val progress = (state.step.ordinal + 1f) / (OnboardingStep.entries.size).toFloat()
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "Step ${state.step.ordinal + 1} of ${OnboardingStep.entries.size}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(8.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            val onIntent: (OnboardingIntent) -> Unit = viewModel::handleIntent
            when (state.step) {
                OnboardingStep.BIOMETRIC_INPUT -> BiometricInputStep(state, onIntent)
                OnboardingStep.WHO_BMI_GAUGE -> WhoBmiGaugeStep(state, engine)
                OnboardingStep.ARCHETYPE_PICKER -> ArchetypePickerStep(state, onIntent)
                OnboardingStep.GOAL_SELECTION -> GoalSelectionStep(state, onIntent)
                OnboardingStep.EQUIPMENT_SELECTION -> EquipmentSelectionStep(state, onIntent)
                OnboardingStep.INJURY_FLAGS -> InjuryFlagsStep(state, onIntent)
                OnboardingStep.SCHEDULE -> ScheduleStep(state, onIntent)
                OnboardingStep.TIMELINE_SLIDER -> TimelineSliderStep(state, onIntent, engine)
                OnboardingStep.REVIEW_SUMMARY -> ReviewSummaryStep(state, onIntent)
            }
        }

        // Navigation bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(
                onClick = { viewModel.handleIntent(OnboardingIntent.GoToPrevStep) },
                enabled = state.step != OnboardingStep.BIOMETRIC_INPUT,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                Text("Back")
            }

            if (state.step == OnboardingStep.REVIEW_SUMMARY) {
                Button(
                    onClick = { viewModel.handleIntent(OnboardingIntent.CompleteOnboarding) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Finish & Generate Plan")
                }
            } else {
                Button(
                    onClick = { viewModel.handleIntent(OnboardingIntent.GoToNextStep) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Next")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                }
            }
        }
    }
