//
//  EnergyBalanceEngine.swift
//  CoreDomain
//
//  Energy balance calculations: BMR (Mifflin-St Jeor), TDEE, dynamic
//  timeline deficit with physiological safety ceilings, BMI classification
//  (Asian vs International WHO), maintenance goal handling.
//
//  All numeric behavior is verified against the canonical test-vector suite
//  `math_engine_test_vectors.json` (31 vectors) by `LeanHomeTests`.
//

import Foundation

/// Outcome of an energy-balance assessment for a user profile.
public struct EnergyBalanceResult: Sendable, Hashable {
    /// Basal Metabolic Rate (kcal/day).
    public let bmr: Double
    /// Total Daily Energy Expenditure (kcal/day) = BMR × PAL.
    public let tdee: Double
    /// BMI computed from raw height/weight.
    public let bmi: Double
    /// BMI category under the applicable WHO standard.
    public let bmiClassification: BMIClassification
    /// WHO standard applied (Asian vs International).
    public let bmiStandard: BMIStandard
    /// Human-readable BMI label.
    public let bmiLabel: String
    /// Daily caloric deficit (0 for maintenance).
    public let dailyDeficit: Double
    /// Daily caloric intake target (TDEE − clamped deficit, floored).
    public let caloricTarget: Double
    /// Raw calculated deficit before safety clamping.
    public let rawDeficit: Double
    /// Deficit after enforcing the 25%-TDEE / 1000-kcal ceiling.
    public let clampedDeficit: Double
    /// Whether the chosen timeline is physiologically safe.
    public let isSafe: Bool
    /// Warning level for the deficit.
    public let warningLevel: WarningLevel
    /// Whether the goal skips timeline selection (maintenance).
    public let skipTimeline: Bool
    /// Whether the deficit is so extreme it requires dietary intervention.
    public let requiresDietaryIntervention: Bool
    /// Minimum safe caloric intake floor for the user's gender.
    public let minCaloricIntake: Double
    /// Maximum safe daily caloric deficit.
    public let maxSafeDeficit: Double
    /// Maximum safe weekly weight loss (1% of body weight).
    public let maxWeeklyLossKg: Double

    public init(
        bmr: Double, tdee: Double, bmi: Double,
        bmiClassification: BMIClassification, bmiStandard: BMIStandard,
        bmiLabel: String, dailyDeficit: Double, caloricTarget: Double,
        rawDeficit: Double, clampedDeficit: Double, isSafe: Bool,
        warningLevel: WarningLevel, skipTimeline: Bool,
        requiresDietaryIntervention: Bool, minCaloricIntake: Double,
        maxSafeDeficit: Double, maxWeeklyLossKg: Double
    ) {
        self.bmr = bmr
        self.tdee = tdee
        self.bmi = bmi
        self.bmiClassification = bmiClassification
        self.bmiStandard = bmiStandard
        self.bmiLabel = bmiLabel
        self.dailyDeficit = dailyDeficit
        self.caloricTarget = caloricTarget
        self.rawDeficit = rawDeficit
        self.clampedDeficit = clampedDeficit
        self.isSafe = isSafe
        self.warningLevel = warningLevel
        self.skipTimeline = skipTimeline
        self.requiresDietaryIntervention = requiresDietaryIntervention
        self.minCaloricIntake = minCaloricIntake
        self.maxSafeDeficit = maxSafeDeficit
        self.maxWeeklyLossKg = maxWeeklyLossKg
    }
}

/// Pure, side-effect-free energy-balance calculations.
public enum EnergyBalanceEngine {

    // MARK: - BMR (Mifflin-St Jeor)

    /// Compute the Basal Metabolic Rate via the Mifflin-St Jeor equation.
    ///
    /// - Male:   10·kg + 6.25·cm − 5·age + 5
    /// - Female: 10·kg + 6.25·cm − 5·age − 161
    /// - Other:  uses the male formula as default (test-vector behavior).
    public static func bmr(gender: Gender, age: Int, weightKg: Double, heightCm: Double) -> Double {
        let base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * Double(age)
        switch gender {
        case .female:
            return base - 161.0
        case .male, .other:
            return base + 5.0
        }
    }

    /// Total Daily Energy Expenditure = BMR × PAL.
    public static func tdee(bmr: Double, activity: ActivityLevel) -> Double {
        bmr * activity.pal
    }

    /// Convenience: BMR + TDEE in one pass.
    public static func bmrTDEE(
        gender: Gender, age: Int, weightKg: Double,
        heightCm: Double, activity: ActivityLevel
    ) -> (bmr: Double, tdee: Double) {
        let b = bmr(gender: gender, age: age, weightKg: weightKg, heightCm: heightCm)
        return (b, tdee(bmr: b, activity: activity))
    }

    // MARK: - Deficit

    /// Raw daily caloric deficit for `targetDays` over `targetWeightLossKg`,
    /// assuming 1 kg fat ≈ 7700 kcal.
    ///
    /// `Daily Deficit = ΔW × 7700 / D`
    public static func rawDailyDeficit(targetWeightLossKg: Double, targetDays: Int) -> Double {
        guard targetDays > 0 else { return 0 }
        return (targetWeightLossKg * 7700.0) / Double(targetDays)
    }

    /// Maximum safe daily caloric deficit = `min(25% TDEE, 1000 kcal)`.
    public static func maxSafeDeficit(tdee: Double) -> Double {
        min(tdee * 0.25, 1000.0)
    }

    /// Deficit clamped to the safe ceiling.
    public static func clampedDeficit(raw: Double, maxSafe: Double) -> Double {
        min(raw, maxSafe)
    }

    /// Warning level for a raw-vs-ceiling deficit assessment.
    ///
    /// Uses a 1% relative band around the safe ceiling, accommodating the
    /// floating-point imprecision of "exactly at the 25% TDEE boundary"
    /// (canonical test vector `deficit_boundary_exact_25pct`):
    /// - `EXTREME`:    raw exceeds the ceiling by more than 1%.
    /// - `AT_CEILING`: raw is within ±1% of the ceiling (i.e. the user is
    ///                 consuming essentially the full safe allowance).
    /// - `NONE`:       raw is comfortably below the ceiling.
    public static func warningLevel(rawDeficit: Double, maxSafe: Double) -> WarningLevel {
        let ceiling = max(maxSafe, 1.0)
        if rawDeficit > ceiling * 1.01 {
            return .extreme
        }
        if rawDeficit >= ceiling * 0.99 {
            return .atCeiling
        }
        return .none
    }

    // MARK: - Safety thresholds

    /// Maximum safe weekly weight loss = 1% of body weight (kg/week).
    public static func maxWeeklyLossKg(weightKg: Double) -> Double {
        weightKg * 0.01
    }

    /// Minimum caloric intake floor: 1200 kcal (female), 1500 kcal (male/other).
    public static func minCaloricIntake(gender: Gender) -> Double {
        switch gender {
        case .female: return 1200.0
        case .male, .other: return 1500.0
        }
    }

    // MARK: - BMI

    /// Maximum daily deficit that incorporates the intake floor as an
    /// additional safety bound (TDEE − minCaloricIntake). Used by the full
    /// assessment; not part of the raw ceiling.
    public static func deficitFloorLimited(tdee: Double, gender: Gender) -> Double {
        max(0.0, tdee - minCaloricIntake(gender: gender))
    }

    /// BMI = weightKg / (heightM)².
    public static func bmi(weightKg: Double, heightCm: Double) -> Double {
        guard heightCm > 0 else { return 0 }
        let h = heightCm / 100.0
        return weightKg / (h * h)
    }

    /// WHO standard for a country code. Asian WHO applies for Western
    /// Pacific Region countries: VN, JP, KR, SG, TH.
    public static func bmiStandard(for countryCode: String) -> BMIStandard {
        let asian = ["VN", "JP", "KR", "SG", "TH"]
        return asian.contains(countryCode.uppercased()) ? .asianWHO : .internationalWHO
    }

    /// Classify a BMI value under the applicable WHO standard.
    public static func classifyBMI(bmi: Double, countryCode: String) -> (BMIClassification, BMIStandard, String) {
        let standard = bmiStandard(for: countryCode)
        switch standard {
        case .asianWHO:
            if bmi < 18.5 {
                return (.underweight, .asianWHO, "Underweight (Asian WHO Standard)")
            } else if bmi < 23.0 {
                return (.normal, .asianWHO, "Normal (Asian WHO Standard)")
            } else if bmi < 25.0 {
                return (.overweight, .asianWHO, "Overweight / Pre-obese (Asian WHO Standard)")
            } else {
                return (.obese, .asianWHO, "Obese (Asian WHO Standard)")
            }
        case .internationalWHO:
            if bmi < 18.5 {
                return (.underweight, .internationalWHO, "Underweight (International WHO Standard)")
            } else if bmi < 25.0 {
                return (.normal, .internationalWHO, "Normal weight (International WHO Standard)")
            } else if bmi < 30.0 {
                return (.overweight, .internationalWHO, "Overweight (International WHO Standard)")
            } else {
                return (.obese, .internationalWHO, "Obese (International WHO Standard)")
            }
        }
    }

    // MARK: - Full assessment

    /// Full energy-balance assessment from a user profile plus weight target.
    ///
    /// - For `HEALTH_MAINTENANCE`: deficit is 0, the caloric target equals
    ///   TDEE, the timeline is skipped, and clamped/raw deficits are 0.
    /// - For deficit goals: the daily deficit is derived from
    ///   `(currentWeight − targetWeight) × 7700 / targetDays`, then clamped to
    ///   the safe ceiling.
    public static func assess(
        gender: Gender,
        age: Int,
        weightKg: Double,
        heightCm: Double,
        activity: ActivityLevel,
        countryCode: String,
        fitnessGoal: FitnessGoal,
        targetWeightKg: Double? = nil,
        targetDays: Int? = nil
    ) -> EnergyBalanceResult {
        let b = bmr(gender: gender, age: age, weightKg: weightKg, heightCm: heightCm)
        let t = tdee(bmr: b, activity: activity)
        let bmiValue = bmi(weightKg: weightKg, heightCm: heightCm)
        let (bmiClass, bmiStd, bmiLabel) = classifyBMI(bmi: bmiValue, countryCode: countryCode)
        let minIntake = minCaloricIntake(gender: gender)
        let maxSafe = maxSafeDeficit(tdee: t)
        let maxWeekly = maxWeeklyLossKg(weightKg: weightKg)

        switch fitnessGoal {
        case .healthMaintenance:
            return EnergyBalanceResult(
                bmr: b, tdee: t, bmi: bmiValue,
                bmiClassification: bmiClass, bmiStandard: bmiStd, bmiLabel: bmiLabel,
                dailyDeficit: 0, caloricTarget: t,
                rawDeficit: 0, clampedDeficit: 0,
                isSafe: true, warningLevel: .none, skipTimeline: true,
                requiresDietaryIntervention: false,
                minCaloricIntake: minIntake, maxSafeDeficit: maxSafe,
                maxWeeklyLossKg: maxWeekly
            )
        case .fatLoss, .weightCut, .targetZoneToning:
            let weightDelta = max(0.0, weightKg - (targetWeightKg ?? weightKg))
            let days = targetDays ?? 0
            let raw = rawDailyDeficit(targetWeightLossKg: weightDelta, targetDays: days)
            let level = warningLevel(rawDeficit: raw, maxSafe: maxSafe)
            let clamped = clampedDeficit(raw: raw, maxSafe: maxSafe)
            let isSafe = level != .extreme
            let caloricTarget = max(minIntake, t - clamped)
            let needsDiet = level == .extreme

            return EnergyBalanceResult(
                bmr: b, tdee: t, bmi: bmiValue,
                bmiClassification: bmiClass, bmiStandard: bmiStd, bmiLabel: bmiLabel,
                dailyDeficit: clamped, caloricTarget: caloricTarget,
                rawDeficit: raw, clampedDeficit: clamped,
                isSafe: isSafe, warningLevel: level, skipTimeline: false,
                requiresDietaryIntervention: needsDiet,
                minCaloricIntake: minIntake, maxSafeDeficit: maxSafe,
                maxWeeklyLossKg: maxWeekly
            )
        }
    }
}
