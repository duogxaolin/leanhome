package com.leanhome.core.domain.usecase

import com.leanhome.core.domain.engine.EnergyBalanceEngine
import com.leanhome.core.domain.model.SessionStatus
import com.leanhome.core.domain.model.UserProfile
import com.leanhome.core.domain.model.WeightLog
import com.leanhome.core.domain.repository.UserProfileRepository
import com.leanhome.core.domain.repository.WeightLogRepository
import com.leanhome.core.domain.repository.WorkoutSessionRepository

/**
 * Snapshot of everything the progress dashboard needs to render in one pass.
 */
data class DashboardSnapshot(
    val profile: UserProfile?,
    val weightLogs: List<WeightLog>,
    val latestWeightKg: Double?,
    val baselineWeightKg: Double?,
    /**
     * (completedSessions / plannedSessions) over the trailing 7-day window.
     * Zero planned sessions ⇒ 0% to avoid a misleading 100%.
     */
    val adherencePercent: Int,
    val adherenceBadgeEarned: Boolean,
    /**
     * Engine projection of the date by which the user reaches their target
     * weight at the current rate of loss. Null when there is no fat-loss goal
     * or no weigh-in history yet.
     */
    val projectedGoalDate: EnergyBalanceEngine.GoalProjection?,
    val plateauAlert: Boolean,
)

/**
 * Use case: assemble a [DashboardSnapshot] for the progress dashboard.
 *
 * Adherence is computed over the trailing 7-day window as
 * (completed / planned) sessions. The plateau detector reuses
 * [FatigueRegulationUseCase.detectPlateau] when the user has at least
 * three weekly weigh-ins and adherence >= 85%.
 */
class GetProgressDashboardUseCase(
    private val userProfileRepository: UserProfileRepository,
    private val weightLogRepository: WeightLogRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val engine: EnergyBalanceEngine = EnergyBalanceEngine(),
    private val fatigueRegulation: FatigueRegulationUseCase = FatigueRegulationUseCase(),
) {

    suspend operator fun invoke(now: Long = System.currentTimeMillis()): DashboardSnapshot {
        val profile = userProfileRepository.getCurrentProfileOnce()
            ?: return DashboardSnapshot(
                profile = null,
                weightLogs = emptyList(),
                latestWeightKg = null,
                baselineWeightKg = null,
                adherencePercent = 0,
                adherenceBadgeEarned = false,
                projectedGoalDate = null,
                plateauAlert = false,
            )

        val profileId = profile.id
        val weightLogs = weightLogRepository.getLogsByUserOnce(profileId).sortedBy { it.recordedAt }
        val latestWeight = weightLogs.lastOrNull()?.weightKg ?: profile.weightKg
        val baselineWeight = weightLogs.firstOrNull()?.weightKg ?: profile.weightKg

        val window = sevenDayWindow(now)
        val sessionsInWindow = sessionRepository.getSessionsByDateRange(
            userId = profileId,
            startDate = window.first,
            endDate = window.second,
        )
        val plannedCount = sessionsInWindow.size
        val completedCount = sessionsInWindow.count { it.status == SessionStatus.COMPLETED }
        val adherencePercent = if (plannedCount > 0) {
            ((completedCount.toDouble() / plannedCount) * 100).toInt().coerceIn(0, 100)
        } else 0
        val adherenceBadgeEarned = adherencePercent >= 85

        val isFatLoss = profile.fitnessGoal == com.leanhome.core.domain.model.FitnessGoal.FAT_LOSS ||
            profile.fitnessGoal == com.leanhome.core.domain.model.FitnessGoal.WEIGHT_CUT
        val projection = if (isFatLoss && profile.targetWeightKg != null) {
            val deficit = computeRateDeficit(profile, weightLogs, latestWeight)
            if (deficit > 0.0) {
                engine.projectGoalDate(
                    currentWeightKg = latestWeight,
                    targetWeightKg = profile.targetWeightKg,
                    dailyDeficitKcal = deficit,
                    tdee = profile.tdee,
                    now = now,
                )
            } else null
        } else null

        val plateau = if (isFatLoss && weightLogs.size >= 3) {
            fatigueRegulation.detectPlateau(
                weeklyWeightsKg = weightLogs.map { it.weightKg },
                compliancePct = adherencePercent.toDouble(),
            )
        } else false

        return DashboardSnapshot(
            profile = profile,
            weightLogs = weightLogs,
            latestWeightKg = latestWeight,
            baselineWeightKg = baselineWeight,
            adherencePercent = adherencePercent,
            adherenceBadgeEarned = adherenceBadgeEarned,
            projectedGoalDate = projection,
            plateauAlert = plateau,
        )
    }

    /**
     * Derive the daily deficit used by the projection.
     *
     * When weigh-in history has at least 2 points enough time apart, compute
     * the empirical rate of loss (kg / day) converted to kcal/day via
     * [EnergyBalanceEngine.KCAL_PER_KG_FAT]. Otherwise fall back to the
     * planned deficit implied by the profile's target weight and target
     * days.
     */
    private fun computeRateDeficit(
        profile: UserProfile,
        weightLogs: List<WeightLog>,
        latestWeight: Double,
    ): Double {
        if (weightLogs.size >= 2) {
            val first = weightLogs.first()
            val last = weightLogs.last()
            val days = ((last.recordedAt - first.recordedAt) / MILLIS_PER_DAY.toDouble())
            if (days > 0) {
                val kgPerDay = (first.weightKg - last.weightKg) / days
                if (kgPerDay > 0) {
                    return kgPerDay * EnergyBalanceEngine.KCAL_PER_KG_FAT
                }
            }
        }
        val targetWeight = profile.targetWeightKg
        val targetDays = profile.targetDays
        if (targetWeight != null && targetDays != null && targetDays > 0 && targetWeight < latestWeight) {
            return engine.calculateDailyDeficit(
                targetWeightLossKg = latestWeight - targetWeight,
                targetDays = targetDays,
            )
        }
        return 0.0
    }

    private fun sevenDayWindow(now: Long): Pair<String, String> {
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = now
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -6)
        val start = formatIsoDate(calendar.timeInMillis)
        val end = formatIsoDate(now)
        return start to end
    }

    private fun formatIsoDate(epochMillis: Long): String {
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = epochMillis
        val y = calendar.get(java.util.Calendar.YEAR)
        val m = calendar.get(java.util.Calendar.MONTH) + 1
        val d = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
