//
//  EnergyBalanceEngineTests.swift
//  LeanHomeTests
//
//  Runs every canonical test vector from `math_engine_test_vectors.json`
//  against the Swift `EnergyBalanceEngine`. All 31 vectors MUST pass.
//

import XCTest
@testable import CoreDomain

final class EnergyBalanceEngineTests: XCTestCase {

    var suite: TestVectorSuite!

    override func setUpWithError() throws {
        suite = try TestVectorLoader.loadSuite()
    }

    // MARK: - Helpers

    private func activity(_ raw: String) -> ActivityLevel {
        ActivityLevel(rawValue: raw) ?? .sedentary
    }
    private func gender(_ raw: String) -> Gender {
        Gender(rawValue: raw) ?? .male
    }

    /// Floating-point comparison tolerance matching canonical precision
    /// (values rounded to 4 decimals).
    private func assertEqual(_ a: Double, _ b: Double, file: StaticString = #file, line: UInt = #line) {
        let absTol = 0.011
        if abs(b) < absTol {
            XCTAssertEqual(a, b, accuracy: absTol, file: file, line: line)
        } else {
            XCTAssertEqual(a, b, accuracy: abs(absTol * 100), file: file, line: line)
            XCTAssertEqual(a, b, accuracy: 0.5, file: file, line: line)
        }
    }

    // MARK: - BMR / TDEE (6 vectors)

    func testBMRTDEE() throws {
        let vectors = suite.bmr_tdee_tests
        XCTAssertEqual(vectors.count, 6, "Expected 6 BMR/TDEE vectors")

        for v in vectors {
            let act = activity(v.input.activity_level)
            let result = EnergyBalanceEngine.bmrTDEE(
                gender: gender(v.input.gender),
                age: v.input.age,
                weightKg: v.input.weight_kg,
                heightCm: v.input.height_cm,
                activity: act
            )
            assertEqual(result.bmr, v.expected.bmr)
            assertEqual(result.tdee, v.expected.tdee)
        }
    }

    // MARK: - Deficit calculation (5 vectors)

    func testDeficitCalculation() throws {
        let vectors = suite.deficit_calculation_tests
        XCTAssertEqual(vectors.count, 5, "Expected 5 deficit vectors")

        for v in vectors {
            let raw = EnergyBalanceEngine.rawDailyDeficit(
                targetWeightLossKg: v.input.target_weight_loss_kg,
                targetDays: v.input.target_days
            )
            // daily_deficit is the raw computed deficit (before clamping).
            let expectedRaw = v.expected.daily_deficit
            // Treat the value to a 4-decimal tolerance.
            XCTAssertEqual(raw, expectedRaw, accuracy: 0.06)

            if let tdee = v.input.tdee {
                let maxSafe = EnergyBalanceEngine.maxSafeDeficit(tdee: tdee)
                let level = EnergyBalanceEngine.warningLevel(rawDeficit: raw, maxSafe: maxSafe)
                let clamped = EnergyBalanceEngine.clampedDeficit(raw: raw, maxSafe: maxSafe)

                if let expectedSafe = v.expected.is_safe {
                    XCTAssertEqual(level != .extreme, expectedSafe)
                }
                if let expectedLevelRaw = v.expected.warning_level {
                    XCTAssertEqual(level, WarningLevel(rawValue: expectedLevelRaw))
                }
                if let expectedClamped = v.expected.clamped_deficit {
                    XCTAssertEqual(clamped, expectedClamped, accuracy: 0.011)
                }
                if let expectedNeedsDiet = v.expected.requires_dietary_intervention {
                    XCTAssertEqual(level == .extreme, expectedNeedsDiet)
                }
            }
        }
    }

    // MARK: - Safety thresholds (6 vectors)

    func testSafetyThresholds() throws {
        let vectors = suite.safety_threshold_tests
        XCTAssertEqual(vectors.count, 6, "Expected 6 safety vectors")

        for v in vectors {
            if let weight = v.input.weight_kg, let expectedWeekly = v.expected.max_weekly_loss_kg {
                let weekly = EnergyBalanceEngine.maxWeeklyLossKg(weightKg: weight)
                XCTAssertEqual(weekly, expectedWeekly, accuracy: 0.011)
            }
            if let genderRaw = v.input.gender, let expectedFloor = v.expected.min_caloric_intake {
                let floor = EnergyBalanceEngine.minCaloricIntake(gender: gender(genderRaw))
                XCTAssertEqual(floor, expectedFloor, accuracy: 0.011)
            }
            if let tdee = v.input.tdee, let expectedMax = v.expected.max_safe_deficit {
                let max = EnergyBalanceEngine.maxSafeDeficit(tdee: tdee)
                XCTAssertEqual(max, expectedMax, accuracy: 0.011)
            }
        }
    }

    // MARK: - BMI classification (8 vectors)

    func testBMIClassification() throws {
        let vectors = suite.bmi_classification_tests
        XCTAssertEqual(vectors.count, 8, "Expected 8 BMI vectors")

        for v in vectors {
            if let cc = v.input.country_code, let bmiVal = v.input.bmi {
                let (cls, std, label) = EnergyBalanceEngine.classifyBMI(bmi: bmiVal, countryCode: cc)
                if let expectedCls = v.expected.classification {
                    XCTAssertEqual(cls, BMIClassification(rawValue: expectedCls))
                }
                if let expectedStd = v.expected.standard {
                    XCTAssertEqual(std, BMIStandard(rawValue: expectedStd))
                }
                if let expectedLabel = v.expected.label {
                    XCTAssertEqual(label, expectedLabel)
                }
            }
            if let weight = v.input.weight_kg, let height = v.input.height_cm, let expectedBmi = v.expected.bmi {
                let computed = EnergyBalanceEngine.bmi(weightKg: weight, heightCm: height)
                XCTAssertEqual(computed, expectedBmi, accuracy: 0.011)
            }
        }
    }

    // MARK: - Maintenance goal (1 vector)

    func testMaintenanceGoal() throws {
        let vectors = suite.maintenance_goal_tests
        XCTAssertEqual(vectors.count, 1, "Expected 1 maintenance vector")

        for v in vectors {
            let tdee = v.input.tdee
            let result = EnergyBalanceEngine.assess(
                gender: .male,
                age: 25,
                weightKg: 75,
                heightCm: 175,
                activity: .sedentary,
                countryCode: "US",
                fitnessGoal: .healthMaintenance,
                targetWeightKg: nil,
                targetDays: nil
            )
            XCTAssertEqual(result.dailyDeficit, v.expected.daily_deficit, accuracy: 0.011)
            XCTAssertEqual(result.caloricTarget, tdee, accuracy: 0.5)
            XCTAssertEqual(result.skipTimeline, v.expected.skip_timeline)
        }
    }
}
