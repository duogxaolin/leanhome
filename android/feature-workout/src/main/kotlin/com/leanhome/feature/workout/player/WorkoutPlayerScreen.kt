package com.leanhome.feature.workout.player

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.leanhome.feature.workout.WorkoutPlayerViewModel
import com.leanhome.feature.workout.mvi.PlayerPhase
import com.leanhome.feature.workout.mvi.WorkoutPlayerState
import com.leanhome.feature.workout.mvi.WorkoutPlayerIntent

/**
 * WorkoutPlayerScreen — drives the EXERCISE_ACTIVE / REST_INTERVAL / SESSION_COMPLETE
 * state machine for a workout.
 *
 * ExoPlayer loops the current exercise video. If offline/un-cached, falls back to
 * text instructions + static thumbnail.
 */
@Composable
fun WorkoutPlayerScreen(
    viewModel: WorkoutPlayerViewModel,
    onCheckin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state.phase) {
            PlayerPhase.EXERCISE_ACTIVE -> ExerciseActiveView(state, viewModel, context)
            PlayerPhase.REST_INTERVAL -> RestIntervalView(state, viewModel)
            PlayerPhase.SESSION_COMPLETE -> SummaryView(state, onCheckin)
        }
    }
}

@Composable
private fun ExerciseActiveView(
    state: WorkoutPlayerState,
    viewModel: WorkoutPlayerViewModel,
    context: android.content.Context,
) {
    val spec = state.exercises.getOrNull(state.currentExerciseIndex) ?: return
    val exercise = spec.exercise

    Text(
        "Set ${state.currentSet} of ${state.totalSets}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(4.dp),
    )

    Text(exercise.nameEn, style = MaterialTheme.typography.headlineSmall)

    // ExoPlayer video loop OR fallback SVG/text if offline
    if (state.isVideoAvailable && !state.isOffline) {
        val exoPlayer = remember { ExoPlayer.Builder(context).build() }
        LaunchedEffect(exercise.id) {
            exoPlayer.stop()
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(exercise.videoUrl)))
            exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ONE
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
        )
    } else {
        // Offline fallback: text instructions + thumbnail note
        Card(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Video available online", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Primary: ${exercise.primaryMuscles.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Synergists: ${exercise.secondaryMuscles.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    Text("Reps: ${spec.plannedReps}", style = MaterialTheme.typography.titleMedium)

    // Targeted muscle groups + secondary synergists (per spec)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Primary muscles",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(exercise.primaryMuscles.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall)
            Text("Secondary synergists",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(exercise.secondaryMuscles.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall)
        }
    }

    // Form cues + at least 2 common mistakes (per spec)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Common mistakes",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            exercise.commonMistakes.forEach {
                Text("⚠ $it", style = MaterialTheme.typography.bodySmall)
            }
            Text("Form cues",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            exercise.correctFormCues.forEach {
                Text("✓ $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Button(
        onClick = { viewModel.handleIntent(WorkoutPlayerIntent.DoneSet) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Done Set")
    }

    OutlinedButton(
        onClick = { viewModel.handleIntent(WorkoutPlayerIntent.EndSession) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("End Workout")
    }
}

@Composable
private fun RestIntervalView(
    state: WorkoutPlayerState,
    viewModel: WorkoutPlayerViewModel,
) {
    LaunchedEffect(state.phase) {
        if (state.phase == PlayerPhase.REST_INTERVAL) viewModel.startRestCountdown()
    }

    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Rest", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${state.restSecondsRemaining}s",
                style = MaterialTheme.typography.displaySmall,
                color = if (state.restSecondsRemaining in 1..3) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
            )
            if (state.restSecondsRemaining in 1..3) {
                Text("Haptic! Short buzz next.", style = MaterialTheme.typography.bodySmall)
            }

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.handleIntent(WorkoutPlayerIntent.SubtractFifteenSeconds) },
                ) {
                    Text("-15s")
                }
                Button(
                    onClick = { viewModel.handleIntent(WorkoutPlayerIntent.AddThirtySeconds) },
                ) {
                    Text("+30s")
                }
            }
            OutlinedButton(
                onClick = { viewModel.handleIntent(WorkoutPlayerIntent.SkipRest) },
            ) {
                Text("Skip rest")
            }
        }
    }
}

@Composable
private fun SummaryView(
    state: WorkoutPlayerState,
    onCheckin: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Session Complete!", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Completed all ${state.exercises.size} exercises.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.sessionElapsedSeconds > 0) {
            val minutes = state.sessionElapsedSeconds / 60
            val seconds = state.sessionElapsedSeconds % 60
            Text("Total time: $minutes:$seconds", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Total volume lifted: ${state.totalVolumeKg}kg",
            style = MaterialTheme.typography.bodyMedium)

        Button(onClick = onCheckin, modifier = Modifier.fillMaxWidth()) {
            Text("Rate This Workout")
        }
    }
}
