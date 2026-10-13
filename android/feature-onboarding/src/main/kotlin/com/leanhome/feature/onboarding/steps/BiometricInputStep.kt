package com.leanhome.feature.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.model.Gender
import com.leanhome.feature.onboarding.mvi.OnboardingIntent
import com.leanhome.feature.onboarding.mvi.OnboardingState

/**
 * Step 1: Biometric input — name, age, gender, height, weight, country.
 *
 * Validation per spec:
 *   - name: 1-50 chars
 *   - age: 13-99
 *   - height: 100-250 cm
 *   - weight: 30-300 kg
 *   - country: ISO alpha-2 code
 */
@Composable
fun BiometricInputStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tell us about yourself", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = state.name,
            onValueChange = { onIntent(OnboardingIntent.NameChanged(it)) },
            label = { Text("Name") },
            isError = state.errors.containsKey("name"),
            supportingText = state.errors["name"]?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = if (state.age == 0) "" else state.age.toString(),
            onValueChange = { it.toIntOrNull()?.let { v -> onIntent(OnboardingIntent.AgeChanged(v)) } },
            label = { Text("Age (${OnboardingState.MIN_AGE}-${OnboardingState.MAX_AGE})") },
            isError = state.errors.containsKey("age"),
            supportingText = state.errors["age"]?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Gender", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Gender.entries.forEachIndexed { idx, g ->
                SegmentedButton(
                    selected = state.gender == g,
                    onClick = { onIntent(OnboardingIntent.GenderChanged(g)) },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = Gender.entries.size),
                ) {
                    Text(g.name)
                }
            }
        }

        OutlinedTextField(
            value = if (state.heightCm == 0.0) "" else state.heightCm.toString(),
            onValueChange = { it.toDoubleOrNull()?.let { v -> onIntent(OnboardingIntent.HeightChanged(v)) } },
            label = { Text("Height (cm) ${OnboardingState.MIN_HEIGHT.toInt()}-${OnboardingState.MAX_HEIGHT.toInt()}") },
            isError = state.errors.containsKey("height"),
            supportingText = state.errors["height"]?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = if (state.weightKg == 0.0) "" else state.weightKg.toString(),
            onValueChange = { it.toDoubleOrNull()?.let { v -> onIntent(OnboardingIntent.WeightChanged(v)) } },
            label = { Text("Weight (kg) ${OnboardingState.MIN_WEIGHT.toInt()}-${OnboardingState.MAX_WEIGHT.toInt()}") },
            isError = state.errors.containsKey("weight"),
            supportingText = state.errors["weight"]?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.countryCode,
            onValueChange = { onIntent(OnboardingIntent.CountryChanged(it)) },
            label = { Text("Country (e.g. VN, US, GB)") },
            isError = state.errors.containsKey("countryCode"),
            supportingText = state.errors["countryCode"]?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
