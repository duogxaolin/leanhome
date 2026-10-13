package com.leanhome.core.domain.usecase

import com.leanhome.core.domain.engine.EnergyBalanceEngine

/**
 * Use case: evaluates rate of perceived exertion across rolling sessions
 * and applies auto-regulation (volume deload/overload).
 */
data class FatigueRecommendation(
    val recommendationLevel: RecommendationLevel,
    val volumeAdjustmentPct: Int,  // negative for deload, positive for overload, 0 for sustain
    val message: String,
)

enum class RecommendationLevel { DELoad, PROGRESS, MAINTAIN }

class FatigueRegulationUseCase(private val engine: EnergyBalanceEngine = EnergyBalanceEngine()) {

    /**
     * Evaluate fatigue from rolling 3 sessions of RPE.
     * - Avg RPE >= 9.0 → deload 10% volume (avoid overtraining).
     * - Avg RPE <= 5.0 → progressive overload (+1 rep or +1 set).
     * - Otherwise → maintain.
     */
    fun evaluate(rpeScores: List<Int>): FatigueRecommendation {
        if (rpeScores.size < 3) {
            return FatigueRecommendation(
                recommendationLevel = RecommendationLevel.MAINTAIN,
                volumeAdjustmentPct = 0,
                message = "Not enough data for auto-regulation yet — keep going!"
            )
        }
        val recent3 = rpeScores.takeLast(3)
        val avg = recent3.sumOf { it.toDouble() } / 3.0

        return when {
            avg >= 9.0 -> FatigueRecommendation(
                recommendationLevel = RecommendationLevel.DELoad,
                volumeAdjustmentPct = -10,
                message = "High fatigue detected over the last 3 sessions. Auto-deload 10% volume set removal recommended to prevent overtraining."
            )
            avg <= 5.0 -> FatigueRecommendation(
                recommendationLevel = RecommendationLevel.PROGRESS,
                volumeAdjustmentPct = 20,
                message = "Low exertion over last 3 sessions — progressive overload (+1 rep or +1 set) suggested."
            )
            else -> FatigueRecommendation(
                recommendationLevel = RecommendationLevel.MAINTAIN,
                volumeAdjustmentPct = 0,
                message = "Training intensity is well-balanced — maintain current volume."
            )
        }
    }

    /**
     * Detect plateau: if weight stalls for 2 consecutive weeks while compliance >= 85%.
     */
    fun detectPlateau(weeklyWeightsKg: List<Double>, compliancePct: Double): Boolean {
        if (weeklyWeightsKg.size < 3) return false
        if (compliancePct < 85) return false
        // Plateau = no weight loss for 2 consecutive weeks
        val w0 = weeklyWeightsKg[weeklyWeightsKg.size - 3]
        val w1 = weeklyWeightsKg[weeklyWeightsKg.size - 2]
        val w2 = weeklyWeightsKg.last()
        val loss1 = w0 - w1
        val loss2 = w1 - w2
        return loss1 <= 0 && loss2 <= 0 || (loss1 < 0.2 && loss2 < 0.2)
    }
}
