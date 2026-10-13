package com.leanhome.core.domain.engine

import com.leanhome.core.domain.model.ActivityLevel
import com.leanhome.core.domain.model.Gender
import kotlin.math.round

/**
 * Energy Balance Engine — calculates BMR (Mifflin-St Jeor), TDEE (PAL multiplier),
 * dynamic deficit timelines, and enforces physiological safety ceilings.
 *
 * Both the Kotlin (Android) and Swift (iOS) implementations MUST produce
 * identical results for every canonical test vector.
 */
class EnergyBalanceEngine {

    companion object {
        /** kcal per kg of body fat. */
        const val KCAL_PER_KG_FAT = 7700.0

        /** Maximum fraction of TDEE that can safely be cut as daily deficit. */
        const val MAX_DEFICIT_TDEE_FRACTION = 0.25

        /** Absolute hard cap on daily caloric deficit (kcal/day). */
        const val MAX_DEFICIT_HARD_CAP_KCAL = 1000.0

        /** Maximum safe weekly weight-loss rate as fraction of body weight. */
        const val MAX_WEEKLY_LOSS_FRACTION = 0.01

        /** Minimum daily caloric intake floor for females (kcal/day). */
        const val MIN_INTAKE_FEMALE_KCAL = 1200.0

        /** Minimum daily caloric intake floor for males (kcal/day). */
        const val MIN_INTAKE_MALE_KCAL = 1500.0

        val ASIAN_WHO_COUNTRIES = setOf("VN", "JP", "KR", "SG", "TH")
    }

    // ── BMR & TDEE ───────────────────────────────────────────────────────

    /**
     * Mifflin-St Jeor BMR.
     * Male:   10 * weight + 6.25 * height - 5 * age + 5
     * Female: 10 * weight + 6.25 * height - 5 * age - 161
     * Other:  male formula (default).
     */
    fun calculateBmr(gender: Gender, age: Int, weightKg: Double, heightCm: Double): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return when (gender) {
            Gender.FEMALE -> base - 161.0
            Gender.MALE, Gender.OTHER -> base + 5.0
        }
    }

    /**
     * TDEE = BMR * PAL.
     */
    fun calculateTdee(gender: Gender, age: Int, weightKg: Double, heightCm: Double, activityLevel: ActivityLevel): Double {
        val bmr = calculateBmr(gender, age, weightKg, heightCm)
        return bmr * activityLevel.pal
    }

    fun calculateTdee(bmr: Double, activityLevel: ActivityLevel): Double = bmr * activityLevel.pal

    // ── Deficit Calculation ──────────────────────────────────────────────

    /**
     * Daily Deficit = (ΔW * 7700) / D
     *
     * The raw daily deficit required to lose [targetWeightLossKg] over [targetDays] days.
     */
    fun calculateDailyDeficit(targetWeightLossKg: Double, targetDays: Int): Double {
        require(targetDays > 0) { "Target days must be positive" }
        require(targetWeightLossKg >= 0) { "Target weight loss must be non-negative" }
        return (targetWeightLossKg * KCAL_PER_KG_FAT) / targetDays
    }

    data class DeficitResult(
        val dailyDeficit: Double,
        val isSafe: Boolean,
        val warningLevel: WarningLevel,
        val clampedDeficit: Double? = null,
        val requiresDietaryIntervention: Boolean = false,
    )

    enum class WarningLevel { NONE, AT_CEILING, EXTREME }

    /**
     * Full deficit calculation including safety evaluation.
     *
     * Determines the raw daily deficit, checks it against safety ceilings
     * (25% TDEE or 1000 kcal cap, whichever is lower), and flags the warning level.
     *
     * When `tdee` is null, the engine returns the raw daily deficit with
     * `isSafe = true` and `NONE` warning (no safety evaluation is performed).
     *
     * @param tdee current TDEE (kcal/day), null to skip safety evaluation
     * @param weightKg current body weight (kg)
     */
    fun evaluateDeficit(
        targetWeightLossKg: Double,
        targetDays: Int,
        tdee: Double? = null,
        weightKg: Double = 0.0,
    ): DeficitResult {
        val dailyDeficit = calculateDailyDeficit(targetWeightLossKg, targetDays)
        val noTdee = tdee == null
        val maxSafe = if (noTdee) 0.0 else maxSafeDailyDeficit(tdee!!)

        return when {
            noTdee -> {
                // No TDEE provided — just return the raw calculation as safe.
                DeficitResult(
                    dailyDeficit = dailyDeficit,
                    isSafe = true,
                    warningLevel = WarningLevel.NONE,
                    clampedDeficit = null,
                    requiresDietaryIntervention = false,
                )
            }
            dailyDeficit <= maxSafe -> {
                // Safe: deficit below or at the ceiling.
                // AT_CEILING when deficit is within 5% of the ceiling (>= 95% of ceiling).
                val ratio = if (maxSafe > 0) dailyDeficit / maxSafe else 0.0
                val atCeiling = ratio >= 0.95
                DeficitResult(
                    dailyDeficit = dailyDeficit,
                    isSafe = true,
                    warningLevel = if (atCeiling) WarningLevel.AT_CEILING else WarningLevel.NONE,
                    clampedDeficit = null,
                    requiresDietaryIntervention = false,
                )
            }
            else -> {
                // Deficit exceeds ceiling. Two sub-cases:
                // 1. Slightly over ceiling (within 5% tolerance) → AT_CEILING, safe.
                // 2. Well over ceiling → EXTREME, clamp and flag dietary intervention.
                val overageRatio = if (maxSafe > 0) dailyDeficit / maxSafe else 1.0
                if (overageRatio <= 1.05) {
                    DeficitResult(
                        dailyDeficit = dailyDeficit,
                        isSafe = true,
                        warningLevel = WarningLevel.AT_CEILING,
                        clampedDeficit = null,
                        requiresDietaryIntervention = false,
                    )
                } else {
                    DeficitResult(
                        dailyDeficit = dailyDeficit,
                        isSafe = false,
                        warningLevel = WarningLevel.EXTREME,
                        clampedDeficit = maxSafe,
                        requiresDietaryIntervention = true,
                    )
                }
            }
        }
    }

    // ── Safety Thresholds ────────────────────────────────────────────────

    /**
     * Maximum safe weekly weight-loss: 1% of body weight per week.
     */
    fun maxWeeklyWeightLossKg(weightKg: Double): Double = weightKg * MAX_WEEKLY_LOSS_FRACTION

    /**
     * Minimum daily caloric intake floor by gender.
     */
    fun minCaloricIntake(gender: Gender): Double = when (gender) {
        Gender.FEMALE -> MIN_INTAKE_FEMALE_KCAL
        Gender.MALE, Gender.OTHER -> MIN_INTAKE_MALE_KCAL
    }

    /**
     * Maximum safe daily caloric deficit: min(25% TDEE, 1000 kcal).
     */
    fun maxSafeDailyDeficit(tdee: Double): Double =
        minOf(tdee * MAX_DEFICIT_TDEE_FRACTION, MAX_DEFICIT_HARD_CAP_KCAL)

    // ── BMI Classification ───────────────────────────────────────────────

    fun calculateBmi(weightKg: Double, heightCm: Double): Double {
        require(heightCm > 0) { "Height must be positive" }
        val heightM = heightCm / 100.0
        return weightKg / (heightM * heightM)
    }

    data class BmiClassification(
        val classification: BmiCategory,
        val standard: BmiStandard,
        val label: String,
    )

    enum class BmiCategory { UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE }
    enum class BmiStandard { ASIAN_WHO, INTERNATIONAL_WHO }

    /**
     * Classify BMI according to regional WHO standards.
     * Asian countries (VN, JP, KR, SG, TH) use stricter Asian thresholds.
     */
    fun classifyBmi(countryCode: String, bmi: Double): BmiClassification {
        val isAsian = countryCode.uppercase() in ASIAN_WHO_COUNTRIES
        return if (isAsian) {
            classifyAsianBmi(bmi)
        } else {
            classifyInternationalBmi(bmi)
        }
    }

    private fun classifyAsianBmi(bmi: Double): BmiClassification {
        val category = when {
            bmi < 18.5 -> BmiCategory.UNDERWEIGHT
            bmi < 23.0 -> BmiCategory.NORMAL
            bmi < 25.0 -> BmiCategory.OVERWEIGHT
            else -> BmiCategory.OBESE
        }
        val label = when (category) {
            BmiCategory.UNDERWEIGHT -> "Underweight (Asian WHO Standard)"
            BmiCategory.NORMAL -> "Normal (Asian WHO Standard)"
            BmiCategory.OVERWEIGHT -> "Overweight / Pre-obese (Asian WHO Standard)"
            BmiCategory.OBESE -> "Obese (Asian WHO Standard)"
        }
        return BmiClassification(category, BmiStandard.ASIAN_WHO, label)
    }

    private fun classifyInternationalBmi(bmi: Double): BmiClassification {
        val category = when {
            bmi < 18.5 -> BmiCategory.UNDERWEIGHT
            bmi < 25.0 -> BmiCategory.NORMAL
            bmi < 30.0 -> BmiCategory.OVERWEIGHT
            else -> BmiCategory.OBESE
        }
        val label = when (category) {
            BmiCategory.UNDERWEIGHT -> "Underweight (International WHO Standard)"
            BmiCategory.NORMAL -> "Normal weight (International WHO Standard)"
            BmiCategory.OVERWEIGHT -> "Overweight (International WHO Standard)"
            BmiCategory.OBESE -> "Obese (International WHO Standard)"
        }
        return BmiClassification(category, BmiStandard.INTERNATIONAL_WHO, label)
    }

    // ── Maintenance Goal ─────────────────────────────────────────────────

    data class MaintenanceResult(
        val dailyDeficit: Double,
        val caloricTarget: Double,
        val skipTimeline: Boolean,
    )

    /**
     * HEALTH_MAINTENANCE: zero deficit, caloric target = TDEE, skip timeline slider.
     */
    fun calculateMaintenance(tdee: Double): MaintenanceResult = MaintenanceResult(
        dailyDeficit = 0.0,
        caloricTarget = tdee,
        skipTimeline = true,
    )

    // ── Goal Projection ─────────────────────────────────────────────────

    data class GoalProjection(
        val estimatedDays: Int,
        val projectedDate: Long,
        val dailyDeficit: Double,
        val isClamped: Boolean,
        val effectiveDailyDeficit: Double,
    )

    /**
     * Project the date by which the user will reach their target weight,
     * given their intended daily deficit (calculated from the onboarding
     * timeline selection or the most recent weigh-in trend).
     *
     * When [dailyDeficitKcal] exceeds the safe ceiling derived from [tdee],
     * the deficit is clamped to [maxSafeDailyDeficit] and the projected
     * date is recomputed using the clamped deficit so the displayed target
     * stays physiologically achievable.
     *
     * @param currentWeightKg current body weight (kg)
     * @param targetWeightKg target body weight (kg); when >= current, the
     *   goal is treated as already reached (zero-day projection)
     * @param dailyDeficitKcal intended daily caloric deficit (kcal/day)
     * @param tdee current TDEE (kcal/day) used for safety clamping; null
     *   skips the safety clamp and uses the raw deficit
     * @param now epoch millis marking the projection start point
     */
    fun projectGoalDate(
        currentWeightKg: Double,
        targetWeightKg: Double,
        dailyDeficitKcal: Double,
        tdee: Double? = null,
        now: Long,
    ): GoalProjection {
        val weightToLose = (currentWeightKg - targetWeightKg).coerceAtLeast(0.0)
        if (weightToLose <= 0.0 || dailyDeficitKcal <= 0.0) {
            return GoalProjection(
                estimatedDays = 0,
                projectedDate = now,
                dailyDeficit = 0.0,
                isClamped = false,
                effectiveDailyDeficit = 0.0,
            )
        }
        val maxSafe = tdee?.let { maxSafeDailyDeficit(tdee) }
        val isClamped = maxSafe != null && dailyDeficitKcal > maxSafe
        val effective = maxSafe?.let { minOf(dailyDeficitKcal, it) } ?: dailyDeficitKcal

        // days = (weight_to_lose * KCAL_PER_KG_FAT) / effective_daily_deficit
        val days = (weightToLose * KCAL_PER_KG_FAT) / effective
        // Round up so the user sees a non-trivial date; floor at 1 day while
        // weight remains to be lost and the deficit is non-zero.
        val estimatedDays = days.toInt().let { if (it < 1) 1 else it }

        val projectedDate = now + estimatedDays.toLong() * 86_400_000L

        return GoalProjection(
            estimatedDays = estimatedDays,
            projectedDate = projectedDate,
            dailyDeficit = dailyDeficitKcal,
            isClamped = isClamped,
            effectiveDailyDeficit = effective,
        )
    }
}
