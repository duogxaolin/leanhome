package com.leanhome.feature.workout.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leanhome.core.domain.model.WeightLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ProgressDashboardScreen — the weekly progress hub.
 *
 * Renders:
 *  - weight trend line chart from baseline to current (Canvas-drawn)
 *  - projected goal achievement date computed from the current rate of loss
 *  - adherence rate (completed / planned sessions over the last 7 days)
 *  - an adherence badge when adherence >= 85%
 *  - a weekly weigh-in prompt card surfacing every 7 days
 *
 * The screen is purely a presentation of [ProgressDashboardState]; all
 * computation lives in the ViewModel and the dashboard use case.
 */
@Composable
fun ProgressDashboardScreen(
    viewModel: ProgressDashboardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Progress", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        if (state.isLoading && state.snapshot == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.message?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(msg, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        val snapshot = state.snapshot
        if (snapshot == null) {
            Text("No profile yet — finish onboarding to see your progress.")
            return@Column
        }

        // Weekly weigh-in prompt card.
        if (state.weighInPromptVisible) {
            WeighInCard(viewModel, state)
        }

        // Weight trend chart.
        WeightTrendCard(snapshot.weightLogs, snapshot.baselineWeightKg, snapshot.latestWeightKg)

        // Adherence + badge.
        AdherenceCard(snapshot.adherencePercent, snapshot.adherenceBadgeEarned)

        // Projected goal date.
        snapshot.projectedGoalDate?.let { projection ->
            GoalProjectionCard(projection)
        } ?: run {
            if (snapshot.profile?.fitnessGoal == com.leanhome.core.domain.model.FitnessGoal.FAT_LOSS ||
                snapshot.profile?.fitnessGoal == com.leanhome.core.domain.model.FitnessGoal.WEIGHT_CUT
            ) {
                Text(
                    "Record a weigh-in to project your goal date.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(
            onClick = { viewModel.handleIntent(ProgressDashboardIntent.Refresh) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Refresh") }
    }
}

@Composable
private fun WeighInCard(viewModel: ProgressDashboardViewModel, state: ProgressDashboardState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weekly weigh-in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "It's time to log your morning fasting weight.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.weighInInput,
                onValueChange = { viewModel.handleIntent(ProgressDashboardIntent.WeighInInputChanged(it)) },
                label = { Text("Weight (kg)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.handleIntent(ProgressDashboardIntent.SubmitWeighIn) }) {
                    Text("Save")
                }
                OutlinedButton(onClick = { viewModel.handleIntent(ProgressDashboardIntent.HideWeighInPrompt) }) {
                    Text("Later")
                }
            }
        }
    }
}

@Composable
private fun WeightTrendCard(
    weightLogs: List<WeightLog>,
    baselineWeightKg: Double?,
    latestWeightKg: Double?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weight trend", style = MaterialTheme.typography.titleMedium)
            if (weightLogs.size < 2) {
                Text(
                    baselineWeightKg?.let { "Baseline: ${"%.1f".format(it)} kg" } ?: "No weigh-ins yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Log at least two weigh-ins to render the trend line.",
                    style = MaterialTheme.typography.bodySmall,
                )
                return@Column
            }
            Text(
                "Baseline ${"%.1f".format(baselineWeightKg ?: weightLogs.first().weightKg)} kg → " +
                    "current ${"%.1f".format(latestWeightKg ?: weightLogs.last().weightKg)} kg",
                style = MaterialTheme.typography.bodyMedium,
            )
            WeightTrendChart(weightLogs)
        }
    }
}

@Composable
private fun WeightTrendChart(weightLogs: List<WeightLog>) {
    val pointColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        if (weightLogs.isEmpty()) return@Canvas
        val weights = weightLogs.map { it.weightKg }
        val minW = weights.min()
        val maxW = weights.max()
        val range = (maxW - minW).let { if (it < 0.5) 0.5 else it }
        val pad = 0.1f
        val w = size.width
        val h = size.height

        val points = weightLogs.mapIndexed { i, log ->
            val x = if (weightLogs.size == 1) w / 2 else (i.toFloat() / (weightLogs.size - 1)) * w
            val normalized = ((log.weightKg - minW) / range).toFloat()
            val y = h - (normalized * (h * (1 - 2 * pad)) + h * pad)
            Offset(x, y)
        }

        // Trend line path.
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = 6f))
        }

        // Data points.
        points.forEach { p ->
            drawCircle(color = pointColor, radius = 6f, center = p)
        }
    }
}

@Composable
private fun AdherenceCard(adherencePercent: Int, badgeEarned: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Adherence (last 7 days)", style = MaterialTheme.typography.titleMedium)
            Text(
                "$adherencePercent%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { (adherencePercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (badgeEarned) {
                AssistChip(
                    onClick = {},
                    label = { Text("★ 85%+ adherence badge") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                )
            } else {
                Text(
                    "Reach 85% to earn the adherence badge.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun GoalProjectionCard(projection: com.leanhome.core.domain.engine.EnergyBalanceEngine.GoalProjection) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Projected goal date", style = MaterialTheme.typography.titleMedium)
            if (projection.estimatedDays <= 0) {
                Text("Goal reached — well done!", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    dateFormat.format(Date(projection.projectedDate)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "At the current rate (${projection.estimatedDays} days at " +
                        "${"%.0f".format(projection.effectiveDailyDeficit)} kcal/day deficit).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (projection.isClamped) {
                    Text(
                        "Deficit clamped to the safe ceiling.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
