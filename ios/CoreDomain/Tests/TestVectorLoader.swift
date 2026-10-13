//
//  TestVectorLoader.swift
//  LeanHomeTests
//
//  Types + loader for the canonical `math_engine_test_vectors.json` suite.
//  Mirrors the JSON structure exactly so the XCTest suite can run all
//  31 vectors with a single Bundle resource lookup.
//

import Foundation

// MARK: - Vector shapes

struct BMRTDEEVector: Codable {
    let id: String
    let input: BMRTDEEInput
    let expected: BMRTDEEExpected
    struct BMRTDEEInput: Codable {
        let gender: String
        let age: Int
        let weight_kg: Double
        let height_cm: Double
        let activity_level: String
    }
    struct BMRTDEEExpected: Codable {
        let bmr: Double
        let tdee: Double
    }
}

struct DeficitVector: Codable {
    let id: String
    let input: DeficitInput
    let expected: DeficitExpected
    struct DeficitInput: Codable {
        let target_weight_loss_kg: Double
        let target_days: Int
        let tdee: Double?
        let weight_kg: Double?
    }
    struct DeficitExpected: Codable {
        let daily_deficit: Double
        let is_safe: Bool?
        let warning_level: String?
        let clamped_deficit: Double?
        let requires_dietary_intervention: Bool?
    }
}

struct SafetyVector: Codable {
    let id: String
    let input: SafetyInput
    let expected: SafetyExpected
    struct SafetyInput: Codable {
        let weight_kg: Double?
        let gender: String?
        let tdee: Double?
    }
    struct SafetyExpected: Codable {
        let max_weekly_loss_kg: Double?
        let min_caloric_intake: Double?
        let max_safe_deficit: Double?
    }
}

struct BMIVector: Codable {
    let id: String
    let input: BMIInput
    let expected: BMIExpected
    struct BMIInput: Codable {
        let country_code: String?
        let bmi: Double?
        let weight_kg: Double?
        let height_cm: Double?
    }
    struct BMIExpected: Codable {
        let classification: String?
        let standard: String?
        let label: String?
        let bmi: Double?
    }
}

struct MaintenanceVector: Codable {
    let id: String
    let input: MaintenanceInput
    let expected: MaintenanceExpected
    struct MaintenanceInput: Codable {
        let fitness_goal: String
        let tdee: Double
    }
    struct MaintenanceExpected: Codable {
        let daily_deficit: Double
        let caloric_target: Double
        let skip_timeline: Bool
    }
}

struct SplitVector: Codable {
    let id: String
    let input: SplitInput
    let expected: SplitExpected
    struct SplitInput: Codable {
        let days_per_week: Int
    }
    struct SplitExpected: Codable {
        let split_type: String
        let sessions: [String]
    }
}

struct TestVectorSuite: Codable {
    let bmr_tdee_tests: [BMRTDEEVector]
    let deficit_calculation_tests: [DeficitVector]
    let safety_threshold_tests: [SafetyVector]
    let bmi_classification_tests: [BMIVector]
    let maintenance_goal_tests: [MaintenanceVector]
    let split_selection_tests: [SplitVector]
}

enum TestVectorLoader {
    /// Load the canonical test-vector suite from the test bundle.
    static func loadSuite() throws -> TestVectorSuite {
        let bundle = Bundle(for: TestVectorBundleAnchor.self)
        guard let url = bundle.url(
            forResource: "math_engine_test_vectors",
            withExtension: "json"
        ) else {
            // Fallback: relative path for CLI `swift test`.
            let rel = URL(fileURLWithPath: "CoreDomain/Tests/Resources/math_engine_test_vectors.json")
            if FileManager.default.fileExists(atPath: rel.path) {
                let data = try Data(contentsOf: rel)
                return try JSONDecoder().decode(TestVectorSuite.self, from: data)
            }
            throw NSError(domain: "TestVectors", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Could not locate math_engine_test_vectors.json",
            ])
        }
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(TestVectorSuite.self, from: data)
    }
}

/// Anchor class so `Bundle(for:)` resolves to this test target's bundle.
final class TestVectorBundleAnchor {}
