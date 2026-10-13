//
//  WorkoutGeneratorTests.swift
//  LeanHomeTests
//
//  Runs the canonical split-selection vectors plus targeted logic checks
//  for the constraint solver behavior.
//

import XCTest
@testable import CoreDomain

final class WorkoutGeneratorTests: XCTestCase {

    var suite: TestVectorSuite!

    override func setUpWithError() throws {
        suite = try TestVectorLoader.loadSuite()
    }

    // MARK: - Split selection (5 vectors)

    func testSplitSelection() throws {
        let vectors = suite.split_selection_tests
        XCTAssertEqual(vectors.count, 5, "Expected 5 split vectors")

        for v in vectors {
            let days = v.input.days_per_week
            let type = WorkoutGenerator.splitType(forDaysPerWeek: days)
            let labels = WorkoutGenerator.weeklySessions(days: days)

            if let expectedType = SplitType(rawValue: v.expected.split_type) {
                XCTAssertEqual(type, expectedType)
            }
            XCTAssertEqual(labels.count, v.expected.sessions.count)
            for (i, expected) in v.expected.sessions.enumerated() {
                XCTAssertEqual(labels[i].rawValue, expected, "Day \(i) mismatch for \(days) days/week")
            }
        }
    }

    // MARK: - Total vector count assertion

    func testAllVectorsLoaded() throws {
        let total =
            suite.bmr_tdee_tests.count +
            suite.deficit_calculation_tests.count +
            suite.safety_threshold_tests.count +
            suite.bmi_classification_tests.count +
            suite.maintenance_goal_tests.count +
            suite.split_selection_tests.count
        XCTAssertEqual(total, 31, "All 31 canonical vectors MUST be loaded")
    }

    // MARK: - Equipment filtering

    func testEquipmentFilteringBodyweightOnly() {
        let catalog = exerciseFixtures()
        let pool = WorkoutGenerator.selectableExercises(
            catalog: catalog,
            availableEquipment: [.bodyweightOnly],
            injuries: []
        )
        // Nothing requiring DUMBBELLS / BANDS / PULL_UP_BAR survives.
        XCTAssertTrue(pool.allSatisfy { ex in
            ex.requiredEquipment.allSatisfy { $0 == .bodyweightOnly }
        })
    }

    func testEquipmentFilteringDumbbellsAndBands() {
        let catalog = exerciseFixtures()
        let pool = WorkoutGenerator.selectableExercises(
            catalog: catalog,
            availableEquipment: [.dumbbells, .resistanceBands, .bodyweightOnly],
            injuries: []
        )
        // Pool must include a band exercise and a dumbbell exercise.
        XCTAssertTrue(pool.contains { $0.requiredEquipment.contains(.dumbbells) })
        XCTAssertTrue(pool.contains { $0.requiredEquipment.contains(.resistanceBands) })
        // No pull-up-bar exercises leak through when the bar is unavailable.
        XCTAssertFalse(pool.contains { $0.requiredEquipment.contains(.pullUpBar) })
    }

    // MARK: - Injury exclusion

    func testInjuryExclusion() {
        let catalog = exerciseFixtures()
        let pool = WorkoutGenerator.selectableExercises(
            catalog: catalog,
            availableEquipment: [.bodyweightOnly, .dumbbells, .resistanceBands, .pullUpBar],
            injuries: [.lowerBack]
        )
        XCTAssertTrue(pool.allSatisfy { !$0.contraindicatedJoints.contains(.lowerBack) })
    }

    // MARK: - Volume allocation

    func testVolumeAllocation30Minutes() {
        let vol = WorkoutGenerator.volumeAllocation(dailyTimeMinutes: 30, targetGoal: .fatLoss)
        XCTAssertGreaterThanOrEqual(vol.exerciseCount, 4)
        XCTAssertLessThanOrEqual(vol.exerciseCount, 7)
        let totalSets = vol.exerciseCount * vol.setsPerExercise
        XCTAssertGreaterThanOrEqual(totalSets, 15)
        XCTAssertLessThanOrEqual(totalSets, 25)
    }

    func testVolumeRestBounds() {
        for g in [FitnessGoal.fatLoss, .targetZoneToning, .healthMaintenance] {
            let vol = WorkoutGenerator.volumeAllocation(dailyTimeMinutes: 45, targetGoal: g)
            XCTAssertGreaterThanOrEqual(vol.restSeconds, 15)
            XCTAssertLessThanOrEqual(vol.restSeconds, 300)
        }
    }

    // MARK: - Target zone toning emphasis

    func testTargetZoneChestToning() {
        let catalog = exerciseFixtures()
        let profile = UserProfile(
            name: "Tester",
            age: 25,
            gender: .male,
            heightCm: 175,
            weightKg: 75,
            countryCode: "US",
            bodyShape: .invertedTriangle,
            fitnessGoal: .targetZoneToning,
            targetMuscleGroup: .chest,
            availableEquipment: [.bodyweightOnly, .dumbbells, .resistanceBands, .pullUpBar],
            injuryFlags: [],
            daysPerWeek: 3,
            dailyTimeMinutes: 45,
            bmr: 1723.75,
            tdee: 2068.5
        )
        let plan = WorkoutGenerator.generateWeeklyPlan(catalog: catalog, profile: profile)
        XCTAssertFalse(plan.sessions.isEmpty)

        let session = plan.sessions.first!
        let chestMuscles = Set(WorkoutGenerator.muscleFocus(for: .chest))
        // Isolation (non-compound) exercises for chest zone.
        let isolations = session.exercises.compactMap { sex -> Exercise? in
            catalog.first { $0.id == sex.exerciseId }
        }.filter { !WorkoutGenerator.isCompound($0) }

        if !isolations.isEmpty {
            let chestIsolations = isolations.filter { ex in
                ex.primaryMuscles.contains { chestMuscles.contains($0) }
            }
            let ratio = Double(chestIsolations.count) / Double(isolations.count)
            XCTAssertGreaterThanOrEqual(ratio, 0.5, "≥50% of isolation volume must target chest")
        }
    }

    // MARK: - Maintenance goal: balanced, no crash

    func testMaintenanceGoalSplitIsFullBody() {
        let profile = UserProfile(
            name: "Maintain",
            age: 30,
            gender: .other,
            heightCm: 170,
            weightKg: 70,
            countryCode: "US",
            bodyShape: .rectangle,
            fitnessGoal: .healthMaintenance,
            availableEquipment: [.bodyweightOnly],
            injuryFlags: [],
            daysPerWeek: 2,
            dailyTimeMinutes: 30,
            bmr: 1623.75,
            tdee: 1948.5
        )
        let plan = WorkoutGenerator.generateWeeklyPlan(catalog: exerciseFixtures(), profile: profile)
        XCTAssertEqual(plan.splitType, .fullBody)
        XCTAssertEqual(plan.sessions.count, 2)
    }

    // MARK: - Fatigue auto-regulation

    func testFatigueAdjustmentAverageHighRPE() {
        XCTAssertEqual(
            WorkoutGenerator.fatigueAdjustment(recentRPE: [9, 10, 9]),
            .deload
        )
    }

    func testFatigueAdjustmentLowRPEProgressiveOverload() {
        XCTAssertEqual(
            WorkoutGenerator.fatigueAdjustment(recentRPE: [4, 5, 5]),
            .progressiveOverload
        )
    }

    func testFatigueAdjustmentModerateRPE() {
        XCTAssertEqual(
            WorkoutGenerator.fatigueAdjustment(recentRPE: [7, 6, 8]),
            .none
        )
    }

    func testFatigueAdjustmentInsufficientSessions() {
        XCTAssertEqual(
            WorkoutGenerator.fatigueAdjustment(recentRPE: [9, 9]),
            .none
        )
    }

    // MARK: - Fixtures

    /// A small synthetic catalog broad enough to exercise every filter/logic path
    /// without depending on the bundled seed JSON.
    private func exerciseFixtures() -> [Exercise] {
        [
            Exercise(
                id: "push_up_standard", nameEn: "Push-Up", nameVi: "Hít đất",
                primaryMuscles: [.chestMid, .chestLower],
                secondaryMuscles: [.triceps, .frontDeltoid, .abs],
                requiredEquipment: [.bodyweightOnly],
                contraindicatedJoints: [.wrist, .shoulder],
                difficultyLevel: .beginner,
                videoUrl: "https://cdn.leanhome.app/push_up_standard.mp4",
                fallbackThumbnailUrl: "https://cdn.leanhome.app/push_up_standard.svg",
                commonMistakes: ["Arching lower back", "Flaring elbows"],
                correctFormCues: ["45° elbow angle", "Brace the core"]
            ),
            Exercise(
                id: "db_bench_press", nameEn: "DB Bench Press", nameVi: "Đẩy tạ",
                primaryMuscles: [.chestUpper, .chestMid],
                secondaryMuscles: [.triceps, .frontDeltoid],
                requiredEquipment: [.dumbbells],
                contraindicatedJoints: [.shoulder, .wrist, .elbow],
                difficultyLevel: .intermediate,
                videoUrl: "https://cdn.leanhome.app/db_bench_press.mp4",
                fallbackThumbnailUrl: "https://cdn.leanhome.app/db_bench_press.svg",
                commonMistakes: ["Bouncing at bottom", "Uneven arm path"],
                correctFormCues: ["Control the descent", "Feet planted"]
            ),
            Exercise(
                id: "db_fly", nameEn: "DB Fly", nameVi: "Đẩy cánh tạ",
                primaryMuscles: [.chestMid, .chestUpper],
                secondaryMuscles: [.frontDeltoid],
                requiredEquipment: [.dumbbells],
                contraindicatedJoints: [.shoulder, .elbow],
                difficultyLevel: .intermediate,
                videoUrl: "https://cdn.leanhome.app/db_fly.mp4",
                fallbackThumbnailUrl: "https://cdn.leanhome.app/db_fly.svg",
                commonMistakes: ["Going too heavy", "Bouncing at the bottom"],
                correctFormCues: ["Slight elbow bend", "Slow eccentric"]
            ),
            Exercise(
                id: "squat_goblet", nameEn: "Goblet Squat", nameVi: "Squat",
                primaryMuscles: [.quadriceps, .glutes],
                secondaryMuscles: [.hamstrings, .core],
                requiredEquipment: [.dumbbells],
                contraindicatedJoints: [.knee, .ankle, .wrist],
                difficultyLevel: .intermediate,
                videoUrl: "https://cdn.leanhome.app/squat_goblet.mp4",
                fallbackThumbnailUrl: "https://cdn.leanhome.app/squat_goblet.svg",
                commonMistakes: ["Knees collapsing inward", "Rounding lower back"],
                correctFormCues: ["Knees track over toes", "Brace before descending"]
            ),
            Exercise(
                id: "deadlift_romania", nameEn: "Romanian Deadlift", nameVi: "RDL",
                primaryMuscles: [.hamstrings, .glutes],
                secondaryMuscles: [.lowerBack, .core],
                requiredEquipment: [.dumbbells],
                contraindicatedJoints: [.lowerBack, .knee],
                difficultyLevel: .advanced,
                videoUrl: "https://cdn.leanhome.app/deadlift_romania.mp4",
                fallbackThumbnailUrl: "https://cdn.leanhome.app/deadlift_romania.svg",
                commonMistakes: ["Rounding the back", "Locking knees"],
                correctFormCues: ["Neutral spine", "Hip hinge"]
            ),
            Exercise(
                id: "band_chest_press", nameEn: "Band Chest Press", nameVi: "Đẩy band",
                primaryMuscles: [.chestMid],
                secondaryMuscles: [.triceps, .frontDeltoid],
                requiredEquipment: [.resistanceBands],
                contraindicatedJoints: [.shoulder, .wrist],
                difficultyLevel: .beginner,
                videoUrl: "https://cdn.leanhome.app/band_chest_press.mp4",
                fallbackThumbnailUrl: "https://cdn.leanhome.app/band_chest_press.svg",
                commonMistakes: ["Shrugging shoulders", "Over-stretching band"],
                correctFormCues: ["Stable stance", "Control the return"]
            ),
            Exercise(
                id: "plank_side", nameEn: "Side Plank", nameVi: "Plank nghiêng",
                primaryMuscles: [.obliques, .abs],
                secondaryMuscles: [.shoulder, .core],
                requiredEquipment: [.bodyweightOnly],
                contraindicatedJoints: [.wrist, .shoulder, .elbow],
                difficultyLevel: .intermediate,
                videoUrl: "https://cdn.leanhome.app/plank_side.mp4",
                fallbackThumbnailUrl: "https://cdn.leanhome.app/plank_side.svg",
                commonMistakes: ["Sagging hips", "Rotating torso"],
                correctFormCues: ["Stack hips", "Long line head-to-heel"]
            ),
        ]
    }
}
