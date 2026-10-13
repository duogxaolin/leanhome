package com.leanhome.core.domain.usecase

import com.leanhome.core.domain.engine.WorkoutGenerator
import com.leanhome.core.domain.model.Exercise
import com.leanhome.core.domain.model.SessionExercise
import com.leanhome.core.domain.model.SessionStatus
import com.leanhome.core.domain.model.UserProfile
import com.leanhome.core.domain.model.WorkoutSession
import com.leanhome.core.domain.repository.ExerciseRepository
import com.leanhome.core.domain.repository.WorkoutSessionRepository
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

/**
 * Use case: generate (or refresh) the upcoming week of workout sessions for a
 * given profile.
 *
 * Pipeline:
 *   1. Load the catalogue of candidate exercises.
 *   2. Pull the trailing RPE scores of the most recent completed sessions and
 *      ask the [FatigueRegulationUseCase] for a volume adjustment.
 *   3. Ask the [WorkoutGenerator] to build one session per split day, passing
 *      the user's exclusions and the fatigue adjustment.
 *   4. Persist each generated session as a PENDING [WorkoutSession] so the
 *      workout list and dashboard can render them.
 *
 * Returning the persisted sessions lets callers (onboarding, weekly refresh)
 * decide where to navigate next without re-querying the data layer.
 */
class GenerateWeeklyPlanUseCase(
    private val exerciseRepository: ExerciseRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val workoutGenerator: WorkoutGenerator = WorkoutGenerator(),
    private val fatigueRegulation: FatigueRegulationUseCase = FatigueRegulationUseCase(),
) {

    /**
     * Generate [profile.daysPerWeek] sessions starting [startDateMillis].
     * Defaults to today so callers can invoke it right after onboarding.
     *
     * @param startDateMillis epoch millis for the first calendar day the plan
     *   should cover. A null value pins the plan to today, matching the
     *   post-onboarding default.
     */
    suspend operator fun invoke(
        profile: UserProfile,
        startDateMillis: Long? = null,
    ): List<WorkoutSession> {
        val candidates: List<Exercise> = exerciseRepository.getAllOnce()
        val split = workoutGenerator.selectSplit(profile.daysPerWeek)

        // Fatigue auto-regulation: pull trailing RPE scores and ask the
        // regulation use case for a volume adjustment. Empty history yields
        // a 0% sustain, so brand-new onboarding plans are unaffected.
        val recentRpe = sessionRepository
            .getRecentCompletedSessions(profile.id, limit = 3)
            .mapNotNull { it.rpeScore }
        val recommendation = fatigueRegulation.evaluate(recentRpe)
        val adjustmentPct = recommendation.volumeAdjustmentPct

        val now = System.currentTimeMillis()
        val startDay = startDateMillis ?: startOfTodayMillis()
        val saved = mutableListOf<WorkoutSession>()

        split.sessions.forEachIndexed { index, splitName ->
            val sessionPlan = workoutGenerator.generateSession(
                splitName = splitName,
                candidateExercises = candidates,
                userEquipment = profile.availableEquipment.toSet(),
                injuryFlags = profile.injuryFlags.toSet(),
                profile = profile,
                excludedExerciseIds = profile.excludedExerciseIds.toSet(),
                adjustmentPct = adjustmentPct,
            )
            val sessionExercises = sessionPlan.exercises.map { spec ->
                SessionExercise(
                    exerciseId = spec.exercise.id,
                    plannedSets = spec.sets,
                    plannedReps = spec.reps,
                    plannedRestSeconds = spec.restSeconds,
                )
            }
            val dayMillis = startDay + index.toLong() * MILLIS_PER_DAY
            val session = WorkoutSession(
                id = UUID.randomUUID().toString(),
                userId = profile.id,
                scheduledDate = formatIsoDate(dayMillis),
                splitName = splitName,
                status = SessionStatus.PENDING,
                exercises = sessionExercises,
                completedDurationSeconds = null,
                rpeScore = null,
                feedbackTags = emptyList(),
                feedbackNotes = null,
                createdAt = now,
            )
            sessionRepository.insert(session)
            saved.add(session)
        }

        return saved
    }

    private fun startOfTodayMillis(): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun formatIsoDate(epochMillis: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = epochMillis
        val y = calendar.get(Calendar.YEAR)
        val m = calendar.get(Calendar.MONTH) + 1
        val d = calendar.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
