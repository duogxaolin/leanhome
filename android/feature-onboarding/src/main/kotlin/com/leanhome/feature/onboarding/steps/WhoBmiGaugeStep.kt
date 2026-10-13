package com.leanhome.feature.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.engine.EnergyBalanceEngine
import com.leanhome.feature.onboarding.mvi.OnboardingState

/**
 * Step 2: WHO BMI gauge — visualizes the computed BMI with the
 * regional WHO standard (Asian vs International thresholds).
 */
@Composable
fun WhoBmiGaugeStep(
    state: OnboardingState,
    accumulator: EnergyBalanceEngine,
    modifier: Modifier = Modifier,
) {
    val bmi = state.bmi
    val classification = state.bmiClassification

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your BMI", style = MaterialTheme.typography.headlineSmall)

        if (bmi == null) {
            Text("Enter biometrics to compute BMI", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Text(
            text = "%.1f".format(bmi),
            style = MaterialTheme.typography.displayLarge,
            color = colorForBmi(bmi, state.countryCode, accumulator),
        )

        if (classification != null) {
            Text(
                "Classified: ${classification.label}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Standard: ${classification.standard.name}",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        // Gauge bar: visualize BMI from 15 to 40
        val normalizedBmi = ((bmi - 15.0) / 25.0).coerceIn(0.0, 1.0).toFloat()
        LinearProgressIndicator(
            progress = { normalizedBmi },
            modifier = Modifier.fillMaxWidth().height(12.dp),
            color = colorForBmi(bmi, state.countryCode, accumulator),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        val ranges = if (state.countryCode.uppercase() in EnergyBalanceEngine.ASIAN_WHO_COUNTRIES) {
            "Underweight <18.5 · Normal 18.5-23 · Overweight 23-25 · Obese ≥25"
        } else {
            "Underweight <18.5 · Normal 18.5-25 · Overweight 25-30 · Obese ≥30"
        }
        Text(ranges, style = MaterialTheme.typography.bodySmall)
    }
}

private fun colorForBmi(
    bmi: Double,
    countryCode: String,
    engine: EnergyBalanceEngine,
): Color {
    val cls = engine.classifyBmi(countryCode, bmi)
    val safetyColors = com.leanhome.core.ui.theme
    return when (cls.classification) {
        EnergyBalanceEngine.BmiCategory.UNDERWEIGHT -> safetyColors.BmiUnderweight
        EnergyBalanceEngine.BmiCategory.NORMAL -> safetyColors.BmiNormal
        EnergyBalanceEngine.BmiCategory.OVERWEIGHT -> safetyColors.BmiOverweight
        EnergyBalanceEngine.BmiCategory.OBESE -> safetyColors.BmiObese
    }
}
