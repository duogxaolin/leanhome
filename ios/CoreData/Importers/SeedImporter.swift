//
//  SeedImporter.swift
//  CoreData
//
//  Reads `exercises_seed.json` from the CoreData bundle and inserts all 60
//  exercises into SwiftData on first launch.
//

import Foundation
import SwiftData
import CoreDomain

/// First-launch seeding of the exercise catalog from the bundled JSON.
public enum SeedImporter {

    /// `true` if the catalog has no exercises yet.
    public static func needsSeeding(in context: ModelContext) -> Bool {
        let descriptor = FetchDescriptor<ExerciseModel>()
        let count = (try? context.fetchCount(descriptor)) ?? 0
        return count == 0
    }

    /// Insert the bundled exercise catalog if it is empty.
    /// Returns the number of exercises inserted.
    @discardableResult
    public static func seedIfNeeded(in context: ModelContext) throws -> Int {
        guard needsSeeding(in: context) else { return 0 }
        return try seedCatalog(in: context)
    }

    /// Decode the bundled `exercises_seed.json` and insert every exercise.
    @discardableResult
    public static func seedCatalog(in context: ModelContext) throws -> Int {
        guard let url = Bundle.module.url(
            forResource: "exercises_seed",
            withExtension: "json"
        ) else {
            throw SeedError.bundleResourceMissing
        }
        let data = try Data(contentsOf: url)

        // Each top-level array element is the exercise JSON object exactly as
        // shared/schemas/exercise.json defines it, so we decode into the raw
        // dictionaries and persist strings verbatim to mirror the SQLite schema.
        let json = try JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        guard let exercises = json else {
            throw SeedError.invalidJSON
        }

        let inserted = try exercises.reduce(into: 0) { count, dict in
            let model = try ExerciseModel(json: dict)
            context.insert(model)
            count += 1
        }
        try context.save()
        return inserted
    }

    /// Load all exercises from the bundled catalog and return domain objects,
    /// without touching SwiftData. Useful for the in-memory generator/tests.
    public static func loadDomainCatalog() throws -> [Exercise] {
        guard let url = Bundle.module.url(
            forResource: "exercises_seed", withExtension: "json"
        ) else {
            throw SeedError.bundleResourceMissing
        }
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode([Exercise].self, from: data)
    }

    public enum SeedError: Error, LocalizedError {
        case bundleResourceMissing
        case invalidJSON
        case missingField(String)

        public var errorDescription: String? {
            switch self {
            case .bundleResourceMissing:
                return "exercises_seed.json not found in the CoreData bundle resources."
            case .invalidJSON:
                return "exercises_seed.json could not be parsed as a JSON array."
            case .missingField(let f):
                return "Seed JSON is missing required field: \(f)."
            }
        }
    }
}

extension ExerciseModel {
    convenience init(json dict: [String: Any]) throws {
        guard let id = dict["id"] as? String else { throw SeedImporter.SeedError.missingField("id") }
        guard let nameEn = dict["name_en"] as? String else { throw SeedImporter.SeedError.missingField("name_en") }
        guard let nameVi = dict["name_vi"] as? String else { throw SeedImporter.SeedError.missingField("name_vi") }

        func encodeReset(_ v: Any?) -> String {
            guard let v = v else { return "[]" }
            let data = try? JSONSerialization.data(withJSONObject: v)
            return (data.flatMap { String(data: $0, encoding: .utf8) }) ?? "[]"
        }

        let primary = encodeReset(dict["primary_muscles"])
        let secondary = encodeReset(dict["secondary_muscles"])
        let equipment = encodeReset(dict["required_equipment"])
        let joints = encodeReset(dict["contraindicated_joints"])
        guard let difficulty = dict["difficulty_level"] as? String else { throw SeedImporter.SeedError.missingField("difficulty_level") }
        guard let videoUrl = dict["video_url"] as? String else { throw SeedImporter.SeedError.missingField("video_url") }
        guard let thumb = dict["fallback_thumbnail_url"] as? String else { throw SeedImporter.SeedError.missingField("fallback_thumbnail_url") }
        let mistakes = encodeReset(dict["common_mistakes"])
        let cues = encodeReset(dict["correct_form_cues"])

        self.init(
            id: id,
            nameEn: nameEn,
            nameVi: nameVi,
            primaryMuscles: primary,
            secondaryMuscles: secondary,
            requiredEquipment: equipment,
            contraindicatedJoints: joints,
            difficultyLevel: difficulty,
            videoUrl: videoUrl,
            fallbackThumbnailUrl: thumb,
            commonMistakes: mistakes,
            correctFormCues: cues
        )
    }
}
