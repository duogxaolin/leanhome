package com.leanhome.feature.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.model.BodyArchetype
import com.leanhome.core.domain.model.Gender
import com.leanhome.feature.onboarding.mvi.OnboardingIntent
import com.leanhome.feature.onboarding.mvi.OnboardingState

/**
 * Step 3: 2D body shape archetype picker.
 *
 * Male options: Inverted Triangle, Rectangle, Oval, Triangle
 * Female options: Hourglass, Pear, Inverted Triangle, Rectangle, Apple
 *
 * (Uses text labels in V1 — illustrations would render SVG assets.)
 */
@Composable
fun ArchetypePickerStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
    onStartCameraScan: () -> Unit = {},
) {
    val archetypes = BodyArchetype.forGender(state.gender)

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose your body shape", style = MaterialTheme.typography.headlineSmall)
        Text("This only affects workout emphasis. Optionally scan with your camera for a suggested shape — processed entirely on your device.",
            style = MaterialTheme.typography.bodySmall)

        OutlinedButton(
            onClick = onStartCameraScan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Scan My Body")
        }
        archetypes.forEach { arch ->
            val selected = state.bodyArchetype == arch
            Card(
                onClick = { onIntent(OnboardingIntent.ArchetypeSelected(arch)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Placeholder for 2D illustration
                    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Text("⊥", style = MaterialTheme.typography.displaySmall)
                    }
                    Column {
                        Text(arch.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(arch.subtitle(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun BodyArchetype.displayName(): String = when (this) {
    BodyArchetype.INVERTED_TRIANGLE -> "Inverted Triangle (Athletic/V-Taper)"
    BodyArchetype.RECTANGLE -> "Rectangle (Straight/Ectomorph)"
    BodyArchetype.OVAL -> "Oval/Round (Endomorph)"
    BodyArchetype.TRIANGLE -> "Triangle (Pear / Lower body heavy)"
    BodyArchetype.HOURGLASS -> "Hourglass"
    BodyArchetype.PEAR -> "Pear"
    BodyArchetype.APPLE -> "Apple"
}

private fun BodyArchetype.subtitle(): String = when (this) {
    BodyArchetype.INVERTED_TRIANGLE -> "V-shape, broad shoulders, narrow waist — mesomorph baseline"
    BodyArchetype.RECTANGLE -> "Straight frame, even proportions — ectomorph baseline"
    BodyArchetype.OVAL -> "Round, central adiposity — endomorph baseline"
    BodyArchetype.TRIANGLE -> "Lower body dominant with shoulders narrower than hips"
    BodyArchetype.HOURGLASS -> "Balanced upper/lower with defined waist — mesomorph"
    BodyArchetype.PEAR -> "Lower body dominant — endomorph"
    BodyArchetype.APPLE -> "Apple shape, midsection dominant — endomorph"
}
