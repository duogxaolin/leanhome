package com.leanhome.feature.workout.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.model.SplitName
import com.leanhome.core.domain.model.WorkoutSession
import com.leanhome.core.domain.repository.WorkoutSessionRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * WorkoutListScreen — displays history and upcoming sessions.
 */
@Composable
fun WorkoutListScreen(
    userId: String,
    repository: WorkoutSessionRepository,
    onSelect: (WorkoutSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessions by repository.getSessionsByUser(userId).collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your Workouts", style = MaterialTheme.typography.headlineMedium)

        if (sessions.isEmpty()) {
            Text("No workouts yet — finish onboarding to generate your personalized plan.",
                style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sessions, key = { it.id }) { session ->
                Card(
                    onClick = { onSelect(session) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(splitTitle(session.splitName),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Date: ${session.scheduledDate}",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Status: ${session.status.name}",
                            style = MaterialTheme.typography.bodySmall)
                        session.rpeScore?.let {
                            Text("RPE: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun splitTitle(splitName: SplitName): String = when (splitName) {
    SplitName.FULL_BODY_A -> "Full Body A"
    SplitName.FULL_BODY_B -> "Full Body B"
    SplitName.UPPER_A -> "Upper A"
    SplitName.UPPER_B -> "Upper B"
    SplitName.LOWER_A -> "Lower A"
    SplitName.LOWER_B -> "Lower B"
    SplitName.PUSH -> "Push Day"
    SplitName.PULL -> "Pull Day"
    SplitName.LEGS -> "Legs Day"
    SplitName.FOCUS -> "Focus Day"
}
