//
//  OnboardingViewModel.swift
//  FeatureOnboarding
//
//  Observable onboarding state with field-by-field validation per the
//  user-onboarding-profiling spec.
//

import SwiftUI
import CoreDomain

/// Onboarding state. Each step (TabView tab) reads from this model.
@MainActor
public final class OnboardingViewModel: ObservableObject {
    // STEP 1 — Biometric
    @Published public var name: String = ""
    @Published public var age: Int = 28
    @Published public var gender: Gender = .male
    @Published public var heightCm: Double = 175
    @Published public var weightKg: Double = 78
    @Published public var countryCode: String = "US"
    @Published public var activityLevel: ActivityLevel = .sedentary

    // STEP 2 — BMI gauge (derived)
    public var bmiValue: Double {
        EnergyBalanceEngine.bmi(weightKg: weightKg, heightCm: heightCm)
    }
    public var bmiClassification: (BMIClassification, BMIStandard, String) {
        EnergyBalanceEngine.classifyBMI(bmi: bmiValue, countryCode: countryCode)
    }

    // STEP 3 — Archetype
    @Published public var bodyShape: BodyShape?

    // STEP 4 — Goal
    @Published public var fitnessGoal: FitnessGoal = .fatLoss
    @Published public var targetMuscleGroup: TargetMuscleGroup?

    // STEP 5 — Equipment & injuries
    @Published public var availableEquipment: Set<Equipment> = [.bodyweightOnly]
    @Published public var injuryFlags: Set<JointFlag> = []

    // STEP 5b — Exercise selection (exclude unwanted exercises)
    @Published public var excludedExerciseIds: Set<String> = []

    // STEP 6 — Schedule + timeline
    @Published public var daysPerWeek: Int = 3
    @Published public var dailyTimeMinutes: Int = 30
    @Published public var targetWeightKg: Double?
    @Published public var targetDays: Int?
    // Latest assessed energy result, recomputed on demand.
    public private(set) var energyResult: EnergyBalanceResult?

    // MARK: - Validation

    public enum ValidationError: LocalizedError, Equatable {
        case nameTooShort, nameTooLong
        case ageOutOfRange
        case heightOutOfRange, weightOutOfRange
        case countryCodeInvalid
        case archetypeMissing
        case equipmentEmpty
        case targetZoneMuscleMissing
        case timelineMissing
        case timelineExtreme

        public var errorDescription: String? {
            switch self {
            case .nameTooShort:            return "Name must be at least 1 character."
            case .nameTooLong:             return "Name must be at most 50 characters."
            case .ageOutOfRange:           return "Age must be between 13 and 99."
            case .heightOutOfRange:        return "Height must be between 100 and 250 cm."
            case .weightOutOfRange:        return "Weight must be between 30 and 300 kg."
            case .countryCodeInvalid:      return "Country code must be 2 letters (ISO 3166-1)."
            case .archetypeMissing:        return "Please select a body shape archetype."
            case .equipmentEmpty:          return "Select at least one available equipment."
            case .targetZoneMuscleMissing: return "Pick a target muscle to tone."
            case .timelineMissing:         return "Choose a target timeline (days)."
            case .timelineExtreme:         return "This timeline exceeds safe deficit limits."
            }
        }
    }

    /// Validate the biometric step.
    public func validateBiometric() -> [ValidationError] {
        var errors: [ValidationError] = []
        if name.trimmingCharacters(in: .whitespaces).isEmpty { errors.append(.nameTooShort) }
        if name.count > 50 { errors.append(.nameTooLong) }
        if !(13...99).contains(age) { errors.append(.ageOutOfRange) }
        if !(100...250).contains(heightCm) { errors.append(.heightOutOfRange) }
        if !(30...300).contains(weightKg) { errors.append(.weightOutOfRange) }
        if countryCode.count != 2 { errors.append(.countryCodeInvalid) }
        return errors
    }

    public func validateArchetype() -> [ValidationError] {
        bodyShape == nil ? [.archetypeMissing] : []
    }

    public func validateGoal() -> [ValidationError] {
        if fitnessGoal == .targetZoneToning && targetMuscleGroup == nil {
            return [.targetZoneMuscleMissing]
        }
        return []
    }

    public func validateEquipment() -> [ValidationError] {
        availableEquipment.isEmpty ? [.equipmentEmpty] : []
    }

    /// Compute + validate the deficit/timeline step.
    public func assessment() -> EnergyBalanceResult {
        let result = EnergyBalanceEngine.assess(
            gender: gender, age: age, weightKg: weightKg, heightCm: heightCm,
            activity: activityLevel, countryCode: countryCode,
            fitnessGoal: fitnessGoal,
            targetWeightKg: targetWeightKg,
            targetDays: targetDays
        )
        return result
    }

    public func validateTimeline() -> [ValidationError] {
        switch fitnessGoal {
        case .healthMaintenance:
            return []
        case .fatLoss, .weightCut, .targetZoneToning:
            var errors: [ValidationError] = []
            if targetDays == nil { errors.append(.timelineMissing) }
            let result = assessment()
            if result.warningLevel == .extreme { errors.append(.timelineExtreme) }
            return errors
        }
    }

    /// Validate the whole flow; returns `nil` if OK or first error otherwise.
    public func validateAll() -> ValidationError? {
        for step in [validateBiometric(), validateArchetype(), validateGoal(),
                     validateEquipment(), validateTimeline()].joined() {
            return step
        }
        return nil
    }

    /// Build the finalized `UserProfile`.
    public func buildProfile() -> UserProfile {
        let r = EnergyBalanceEngine.bmrTDEE(
            gender: gender, age: age, weightKg: weightKg,
            heightCm: heightCm, activity: activityLevel
        )
        return UserProfile(
            name: name, age: age, gender: gender,
            heightCm: heightCm, weightKg: weightKg,
            countryCode: countryCode,
            bodyShape: bodyShape ?? .rectangle,
            fitnessGoal: fitnessGoal,
            targetMuscleGroup: targetMuscleGroup,
            availableEquipment: Array(availableEquipment),
            injuryFlags: Array(injuryFlags),
            activityLevel: activityLevel,
            daysPerWeek: daysPerWeek,
            dailyTimeMinutes: dailyTimeMinutes,
            targetWeightKg: targetWeightKg,
            targetDays: targetDays,
            bmr: r.bmr, tdee: r.tdee
        )
    }
}
