//
//  SwiftDataModels.swift
//  CoreData
//
//  SwiftData `@Model` classes matching design.md / build_seed_db.py schema.
//  Relationships to JSON-array columns use `String` + transformer for arrays.
//

import Foundation
import SwiftData

// MARK: - Codable array transformer (JSON-array → String columns)

/// Stores `[String]` arrays as a JSON-encoded String column, decoding known
/// enum values back to plain strings (matching the shared schema).
final class StringArrayTransformer: ValueTransformer {
    static let name = NSValueTransformerName(rawValue: "StringArrayTransformer")

    override class func transformedValueClass() -> AnyClass { NSString.self }

    override func reverseTransformedValue(_ value: Any?) -> Any? {
        guard let data = value as? Data,
              let array = try? JSONDecoder().decode([String].self, from: data) else {
            return []
        }
        return array
    }

    override func transformedValue(_ value: Any?) -> Any? {
        guard let array = value as? [String] else { return "[]".data(using: .utf8) }
        return (try? JSONEncoder().encode(array)) ?? "[]".data(using: .utf8)
    }

    static func register() {
        if ValueTransformer(forName: name) == nil {
            ValueTransformer.setValueTransformer(StringArrayTransformer(), forName: name)
        }
    }
}

// MARK: - ExerciseModel

/// SwiftData model mirroring the `exercises` SQLite table.
@Model
public final class ExerciseModel {
    #Index<Equipment>([.bodyweightOnly]) // not a real SwiftData index hint; kept for clarity
    @Attribute(.unique) public var id: String
    public var nameEn: String
    public var nameVi: String
    /// Stored as JSON-array string per the schema.
    public var primaryMuscles: String
    public var secondaryMuscles: String
    public var requiredEquipment: String
    public var contraindicatedJoints: String
    public var difficultyLevel: String
    public var videoUrl: String
    public var fallbackThumbnailUrl: String
    public var commonMistakes: String
    public var correctFormCues: String

    public init(
        id: String,
        nameEn: String,
        nameVi: String,
        primaryMuscles: String,
        secondaryMuscles: String,
        requiredEquipment: String,
        contraindicatedJoints: String,
        difficultyLevel: String,
        videoUrl: String,
        fallbackThumbnailUrl: String,
        commonMistakes: String,
        correctFormCues: String
    ) {
        self.id = id
        self.nameEn = nameEn
        self.nameVi = nameVi
        self.primaryMuscles = primaryMuscles
        self.secondaryMuscles = secondaryMuscles
        self.requiredEquipment = requiredEquipment
        self.contraindicatedJoints = contraindicatedJoints
        self.difficultyLevel = difficultyLevel
        self.videoUrl = videoUrl
        self.fallbackThumbnailUrl = fallbackThumbnailUrl
        self.commonMistakes = commonMistakes
        self.correctFormCues = correctFormCues
    }
}

extension ExerciseModel {
    /// Decode the JSON-array string columns into a domain `Exercise`.
    public func toDomain() -> Exercise? {
        func decode<T: Decodable>(_ type: [T].Type, _ s: String, map: (T) -> T) -> [T] {
            guard let d = s.data(using: .utf8),
                  let v = try? JSONDecoder().decode(type, from: d) else { return [] }
            return v.map { map($0) }
        }
        func strings(_ s: String) -> [String] {
            guard let d = s.data(using: .utf8) else { return [] }
            return (try? JSONDecoder().decode([String].self, from: d)) ?? []
        }

        let primary = strings(primaryMuscles).compactMap { MuscleGroup(rawValue: $0) }
        let secondary = strings(secondaryMuscles).compactMap { MuscleGroup(rawValue: $0) }
        let equipment = strings(requiredEquipment).compactMap { Equipment(rawValue: $0) }
        let joints = strings(contraindicatedJoints).compactMap { JointFlag(rawValue: $0) }
        guard let diff = DifficultyLevel(rawValue: difficultyLevel) else { return nil }

        return Exercise(
            id: id,
            nameEn: nameEn,
            nameVi: nameVi,
            primaryMuscles: primary,
            secondaryMuscles: secondary,
            requiredEquipment: equipment,
            contraindicatedJoints: joints,
            difficultyLevel: diff,
            videoUrl: videoUrl,
            fallbackThumbnailUrl: fallbackThumbnailUrl,
            commonMistakes: strings(commonMistakes),
            correctFormCues: strings(correctFormCues)
        )
    }
}

// MARK: - UserProfileModel

/// SwiftData model mirroring the `user_profiles` SQLite table.
@Model
public final class UserProfileModel {
    @Attribute(.unique) public var id: UUID
    public var name: String
    public var age: Int
    public var gender: String
    public var heightCm: Double
    public var weightKg: Double
    public var countryCode: String
    public var bodyShape: String
    public var fitnessGoal: String
    public var targetMuscleGroup: String?
    public var availableEquipment: String
    public var injuryFlags: String
    public var activityLevel: String
    public var daysPerWeek: Int
    public var dailyTimeMinutes: Int
    public var targetWeightKg: Double?
    public var targetDays: Int?
    public var bmr: Double
    public var tdee: Double
    public var createdAt: Int

    public init(profile: UserProfile) {
        self.id = profile.id
        self.name = profile.name
        self.age = profile.age
        self.gender = profile.gender.rawValue
        self.heightCm = profile.heightCm
        self.weightKg = profile.weightKg
        self.countryCode = profile.countryCode
        self.bodyShape = profile.bodyShape.rawValue
        self.fitnessGoal = profile.fitnessGoal.rawValue
        self.targetMuscleGroup = profile.targetMuscleGroup?.rawValue
        self.availableEquipment = json(profile.availableEquipment.map(\.rawValue))
        self.injuryFlags = json(profile.injuryFlags.map(\.rawValue))
        self.activityLevel = profile.activityLevel.rawValue
        self.daysPerWeek = profile.daysPerWeek
        self.dailyTimeMinutes = profile.dailyTimeMinutes
        self.targetWeightKg = profile.targetWeightKg
        self.targetDays = profile.targetDays
        self.bmr = profile.bmr
        self.tdee = profile.tdee
        self.createdAt = profile.createdAt
    }

    public func toDomain() -> UserProfile {
        func dec<T: RawRepresentable>(_ raw: String, _ type: T.Type) -> T? where T.RawValue == String {
            T(rawValue: raw)
        }
        let equip = decodeStrArray(availableEquipment).compactMap { Equipment(rawValue: $0) }
        let injuries = decodeStrArray(injuryFlags).compactMap { JointFlag(rawValue: $0) }
        return UserProfile(
            id: id,
            name: name, age: age,
            gender: Gender(rawValue: gender) ?? .other,
            heightCm: heightCm, weightKg: weightKg,
            countryCode: countryCode,
            bodyShape: BodyShape(rawValue: bodyShape) ?? .rectangle,
            fitnessGoal: FitnessGoal(rawValue: fitnessGoal) ?? .healthMaintenance,
            targetMuscleGroup: targetMuscleGroup.flatMap { TargetMuscleGroup(rawValue: $0) },
            availableEquipment: equip,
            injuryFlags: injuries,
            activityLevel: ActivityLevel(rawValue: activityLevel) ?? .sedentary,
            daysPerWeek: daysPerWeek,
            dailyTimeMinutes: dailyTimeMinutes,
            targetWeightKg: targetWeightKg,
            targetDays: targetDays,
            bmr: bmr, tdee: tdee, createdAt: createdAt
        )
    }
}

// MARK: - WorkoutSessionModel

/// SwiftData model mirroring the `workout_sessions` SQLite table.
@Model
public final class WorkoutSessionModel {
    @Attribute(.unique) public var id: UUID
    public var userId: UUID
    public var scheduledDate: String
    public var splitName: String
    public var status: String
    /// JSON-encoded `[SessionExercise]` per schema.
    public var exercises: String
    public var completedDurationSeconds: Int?
    public var rpeScore: Int?
    public var feedbackTags: String?
    public var feedbackNotes: String?
    public var createdAt: Int

    public init(session: WorkoutSession) {
        self.id = session.id
        self.userId = session.userId
        self.scheduledDate = session.scheduledDate
        self.splitName = session.splitName.rawValue
        self.status = session.status.rawValue
        self.exercises = jsonEncodable(session.exercises)
        self.completedDurationSeconds = session.completedDurationSeconds
        self.rpeScore = session.rpeScore
        self.feedbackTags = session.feedbackTags.isEmpty
            ? nil
            : json(session.feedbackTags.map(\.rawValue))
        self.feedbackNotes = session.feedbackNotes
        self.createdAt = session.createdAt
    }

    public func toDomain() -> WorkoutSession {
        let exData = exercises.data(using: .utf8) ?? Data()
        let plannedExercises = (try? JSONDecoder().decode([SessionExercise].self, from: exData)) ?? []
        let tagsData = feedbackTags?.data(using: .utf8)
        let tags: [FeelingTag] = tagsData.flatMap {
            (try? JSONDecoder().decode([String].self, from: $0)) ?? []
        }.compactMap { FeelingTag(rawValue: $0) }
        return WorkoutSession(
            id: id,
            userId: userId,
            scheduledDate: scheduledDate,
            splitName: SplitSession(rawValue: splitName) ?? .fullBodyA,
            status: SessionStatus(rawValue: status) ?? .pending,
            exercises: plannedExercises,
            completedDurationSeconds: completedDurationSeconds,
            rpeScore: rpeScore,
            feedbackTags: tags,
            feedbackNotes: feedbackNotes,
            createdAt: createdAt
        )
    }
}

// MARK: - WeightLogModel

/// SwiftData model for weekly weigh-in records. Stores one entry per weigh-in
/// with the measured weight, date, and user association.
@Model
public final class WeightLogModel {
    @Attribute(.unique) public var id: UUID
    public var userId: UUID
    public var weightKg: Double
    /// ISO 8601 date string (YYYY-MM-dd).
    public var loggedDate: String
    public var createdAt: Int

    public init(
        id: UUID = UUID(),
        userId: UUID,
        weightKg: Double,
        loggedDate: String,
        createdAt: Int = Int(Date().timeIntervalSince1970)
    ) {
        self.id = id
        self.userId = userId
        self.weightKg = weightKg
        self.loggedDate = loggedDate
        self.createdAt = createdAt
    }
}

// MARK: - JSON helpers

private func json(_ array: [String]) -> String {
    (try? String(data: JSONEncoder().encode(array), encoding: .utf8)) ?? "[]"
}

private func jsonEncodable<T: Encodable>(_ value: T) -> String {
    guard let data = try? JSONEncoder().encode(value),
          let s = String(data: data, encoding: .utf8) else { return "[]" }
    return s
}

private func decodeStrArray(_ s: String) -> [String] {
    guard let data = s.data(using: .utf8) else { return [] }
    return (try? JSONDecoder().decode([String].self, from: data)) ?? []
}
