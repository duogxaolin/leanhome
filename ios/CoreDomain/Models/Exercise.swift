//
//  Exercise.swift
//  CoreDomain
//
//  Plain domain representation of a catalog exercise.
//  Mirrors shared/schemas/exercise.schema.json exactly.
//

import Foundation

/// A single exercise in the LeanHome catalog.
public struct Exercise: Identifiable, Codable, Sendable, Hashable {
    /// Slug identifier (lowercase, underscore-separated).
    public let id: String
    public let nameEn: String
    public let nameVi: String
    public let primaryMuscles: [MuscleGroup]
    public let secondaryMuscles: [MuscleGroup]
    public let requiredEquipment: [Equipment]
    public let contraindicatedJoints: [JointFlag]
    public let difficultyLevel: DifficultyLevel
    public let videoUrl: String
    public let fallbackThumbnailUrl: String
    public let commonMistakes: [String]
    public let correctFormCues: [String]

    public init(
        id: String,
        nameEn: String,
        nameVi: String,
        primaryMuscles: [MuscleGroup],
        secondaryMuscles: [MuscleGroup],
        requiredEquipment: [Equipment],
        contraindicatedJoints: [JointFlag],
        difficultyLevel: DifficultyLevel,
        videoUrl: String,
        fallbackThumbnailUrl: String,
        commonMistakes: [String],
        correctFormCues: [String]
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

    /// `true` if every piece of `requiredEquipment` is in `available`.
    public func isSelectableWith(equipment available: Set<Equipment>) -> Bool {
        requiredEquipment.allSatisfy { available.contains($0) }
    }

    /// `true` if any of the user's flagged injuries appears in the
    /// exercise's contraindicated joints.
    public func isContraindicatedFor(injuries: Set<JointFlag>) -> Bool {
        contraindicatedJoints.contains { injuries.contains($0) }
    }

    // MARK: Codable (snake_case ↔ camelCase)

    enum CodingKeys: String, CodingKey {
        case id
        case nameEn = "name_en"
        case nameVi = "name_vi"
        case primaryMuscles = "primary_muscles"
        case secondaryMuscles = "secondary_muscles"
        case requiredEquipment = "required_equipment"
        case contraindicatedJoints = "contraindicated_joints"
        case difficultyLevel = "difficulty_level"
        case videoUrl = "video_url"
        case fallbackThumbnailUrl = "fallback_thumbnail_url"
        case commonMistakes = "common_mistakes"
        case correctFormCues = "correct_form_cues"
    }
}
