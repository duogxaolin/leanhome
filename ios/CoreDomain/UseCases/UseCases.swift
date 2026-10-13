//
//  UseCases.swift
//  CoreDomain
//
//  Thin use-case orchestration: builds an `EnergyBalanceResult` together
//  with a `WeeklyWorkoutPlan` from a user profile + catalog.
//

import Foundation

/// Composed result carrying the energy assessment and the generated plan.
public struct ProgramResult: Sendable, Hashable {
    public let energy: EnergyBalanceResult
    public let plan: WeeklyWorkoutPlan
}

/// Build a full program for a user given the exercise catalog.
public enum GenerateProgramUseCase {

    /// Produce the user's energy/balance assessment plus a weekly workout plan.
    public static func execute(
        profile: UserProfile,
        catalog: [Exercise],
        excludedExerciseIds: Set<String> = [],
        volumeAdjustment: WorkoutGenerator.VolumeAdjustment = .none
    ) -> ProgramResult {
        let energy = EnergyBalanceEngine.assess(
            gender: profile.gender,
            age: profile.age,
            weightKg: profile.weightKg,
            heightCm: profile.heightCm,
            activity: profile.activityLevel,
            countryCode: profile.countryCode,
            fitnessGoal: profile.fitnessGoal,
            targetWeightKg: profile.targetWeightKg,
            targetDays: profile.targetDays
        )
        let plan = WorkoutGenerator.generateWeeklyPlan(
            catalog: catalog,
            profile: profile,
            excludedExerciseIds: excludedExerciseIds,
            volumeAdjustment: volumeAdjustment
        )
        return ProgramResult(energy: energy, plan: plan)
    }
}
