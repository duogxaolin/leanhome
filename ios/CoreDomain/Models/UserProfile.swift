//
//  UserProfile.swift
//  CoreDomain
//
//  Plain domain representation of a user profile.
//  Mirrors shared/schemas/user-profile.schema.json exactly.
//

import Foundation

/// Biometric and fitness goal profile collected during onboarding.
public struct UserProfile: Identifiable, Codable, Sendable, Hashable {
    public var id: UUID
    public var name: String
    public var age: Int
    public var gender: Gender
    public var heightCm: Double
    public var weightKg: Double
    public var countryCode: String
    public var bodyShape: BodyShape
    public var fitnessGoal: FitnessGoal
    public var targetMuscleGroup: TargetMuscleGroup?
    public var availableEquipment: [Equipment]
    public var injuryFlags: [JointFlag]
    public var activityLevel: ActivityLevel
    public var daysPerWeek: Int
    public var dailyTimeMinutes: Int
    public var targetWeightKg: Double?
    public var targetDays: Int?
    public var bmr: Double
    public var tdee: Double
    public var createdAt: Int

    public init(
        id: UUID = UUID(),
        name: String,
        age: Int,
        gender: Gender,
        heightCm: Double,
        weightKg: Double,
        countryCode: String,
        bodyShape: BodyShape,
        fitnessGoal: FitnessGoal,
        targetMuscleGroup: TargetMuscleGroup? = nil,
        availableEquipment: [Equipment],
        injuryFlags: [JointFlag] = [],
        activityLevel: ActivityLevel = .sedentary,
        daysPerWeek: Int,
        dailyTimeMinutes: Int,
        targetWeightKg: Double? = nil,
        targetDays: Int? = nil,
        bmr: Double,
        tdee: Double,
        createdAt: Int = Int(Date().timeIntervalSince1970)
    ) {
        self.id = id
        self.name = name
        self.age = age
        self.gender = gender
        self.heightCm = heightCm
        self.weightKg = weightKg
        self.countryCode = countryCode
        self.bodyShape = bodyShape
        self.fitnessGoal = fitnessGoal
        self.targetMuscleGroup = targetMuscleGroup
        self.availableEquipment = availableEquipment
        self.injuryFlags = injuryFlags
        self.activityLevel = activityLevel
        self.daysPerWeek = daysPerWeek
        self.dailyTimeMinutes = dailyTimeMinutes
        self.targetWeightKg = targetWeightKg
        self.targetDays = targetDays
        self.bmr = bmr
        self.tdee = tdee
        self.createdAt = createdAt
    }

    // MARK: Codable (snake_case)

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case age
        case gender
        case heightCm = "height_cm"
        case weightKg = "weight_kg"
        case countryCode = "country_code"
        case bodyShape = "body_shape"
        case fitnessGoal = "fitness_goal"
        case targetMuscleGroup = "target_muscle_group"
        case availableEquipment = "available_equipment"
        case injuryFlags = "injury_flags"
        case activityLevel = "activity_level"
        case daysPerWeek = "days_per_week"
        case dailyTimeMinutes = "daily_time_minutes"
        case targetWeightKg = "target_weight_kg"
        case targetDays = "target_days"
        case bmr
        case tdee
        case createdAt = "created_at"
    }
}
