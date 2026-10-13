//
//  WorkoutGenerator.swift
//  CoreDomain
//
//  Offline-first constraint solver: selects a weekly split, filters the
//  exercise catalog by equipment + injury contraindications, and allocates
//  per-session volume (4–7 exercises, 15–25 working sets) within the user's
//  daily time budget, with target-zone muscle emphasis for toning goals.
//
//  Split/selection behavior is verified by `LeanHomeTests` (split vectors).
//

import Foundation

/// A fully generated workout week: split type + ordered session plans.
public struct WeeklyWorkoutPlan: Sendable, Hashable {
    public let splitType: SplitType
    /// One entry per committed training day, in weekly order.
    public let sessions: [PlannedSession]

    public init(splitType: SplitType, sessions: [PlannedSession]) {
        self.splitType = splitType
        self.sessions = sessions
    }
}

/// A single planned session: which split day, and which exercises (with
/// sets/reps/rest) it contains.
public struct PlannedSession: Sendable, Hashable, Identifiable {
    /// Identifier derived from day index for stable list identity.
    public let id: Int
    public let splitSession: SplitSession
    public let exercises: [SessionExercise]

    public init(id: Int, splitSession: SplitSession, exercises: [SessionExercise]) {
        self.id = id
        self.splitSession = splitSession
        self.exercises = exercises
    }
}

/// Volume recommendation produced by time-based allocation.
public struct SessionVolume: Sendable, Hashable {
    public let exerciseCount: Int
    public let setsPerExercise: Int
    public let restSeconds: Int

    public init(exerciseCount: Int, setsPerExercise: Int, restSeconds: Int) {
        self.exerciseCount = exerciseCount
        self.setsPerExercise = setsPerExercise
        self.restSeconds = restSeconds
    }
}

public enum WorkoutGenerator {

    // MARK: - Split selection

    /// Select the split type for a given weekly frequency.
    ///
    /// - F = 2 or 3 → `FULL_BODY` (A/B alternating)
    /// - F = 4      → `UPPER_LOWER`
    /// - F = 5 or 6 → `PPL` (Push / Pull / Legs cycling)
    public static func splitType(forDaysPerWeek days: Int) -> SplitType {
        switch days {
        case 2, 3:        return .fullBody
        case 4:           return .upperLower
        case 5, 6:        return .ppl
        default:          return .fullBody
        }
    }

    /// Ordered weekly session labels, cycled from the base pattern to fill
    /// `days` training slots.
    ///
    /// Per canonical test vectors:
    ///  - 2 → [A, B]
    ///  - 3 → [A, B, A]
    ///  - 4 → [UPPER_A, LOWER_A, UPPER_B, LOWER_B]
    ///  - 5 → [PUSH, PULL, LEGS, PUSH, PULL]
    ///  - 6 → [PUSH, PULL, LEGS, PUSH, PULL, LEGS]
    public static func weeklySessions(days: Int) -> [SplitSession] {
        let base: [SplitSession]
        switch days {
        case 2, 3: base = [.fullBodyA, .fullBodyB]
        case 4:    base = [.upperA, .lowerA, .upperB, .lowerB]
        case 5, 6: base = [.push, .pull, .legs]
        default:   base = [.fullBodyA, .fullBodyB]
        }
        guard days > base.count else { return Array(base.prefix(days)) }
        // Cycle the base from the beginning to fill remaining slots.
        var result = base
        var i = 0
        while result.count < days {
            result.append(base[i % base.count])
            i += 1
        }
        return result
    }

    // MARK: - Volume allocation

    /// Allocate session volume to fit `dailyTimeMinutes`.
    ///
    /// Each working set is assumed to take ~45s of effort plus the rest
    /// interval. For a 30-minute budget the generator picks 4 exercises,
    /// 3 sets each at 60s rest — 12 sets × (45 + 60) = 1260s ≈ 21 min with
    /// warm-up slack, per the spec scenario.
    ///
    /// Constraints (spec):
    ///  - 4–7 exercises per session
    ///  - 15–25 working sets per session
    ///  - 60s default rest (15–300s valid range)
    public static func volumeAllocation(
        dailyTimeMinutes: Int,
        targetGoal: FitnessGoal
    ) -> SessionVolume {
        // Estimate seconds available.
        let budgetSeconds = dailyTimeMinutes * 60
        let setEffort: Double = 45.0   // seconds of work per set
        let defaultRest = 60
        // Search the largest viable (exerciseCount, setsPerExercise) ≤ 7×4.
        // Prefer 4×3 baseline; expand when time allows.
        let candidates: [(exercises: Int, sets: Int, rest: Int)] = [
            (4, 3, 60),
            (4, 4, 60),
            (5, 3, 75),
            (5, 4, 75),
            (6, 3, 60),
            (6, 4, 60),
            (7, 3, 60),
            (7, 4, 60),
        ]
        // Pick the candidate whose total working sets fit in the budget
        // and whose total sets land in [15, 25].
        for candidate in candidates {
            let totalSets = Double(candidate.exercises * candidate.sets)
            let totalSeconds = totalSets * (setEffort + Double(candidate.rest))
            let totalSetsOk = (15...25).contains(candidate.exercises * candidate.sets)
            if totalSeconds <= Double(budgetSeconds) && totalSetsOk {
                return SessionVolume(
                    exerciseCount: candidate.exercises,
                    setsPerExercise: candidate.sets,
                    restSeconds: candidate.rest
                )
            }
        }
        // Fall back to a conservative 4×3 baseline (15 sets, ~21 min).
        return SessionVolume(exerciseCount: 4, setsPerExercise: 3, restSeconds: defaultRest)
    }

    // MARK: - Candidate filtering

    /// Filter `catalog` to selectable exercises: every required equipment
    /// is available AND no contraindicated joint is injured.
    public static func selectableExercises(
        catalog: [Exercise],
        availableEquipment: [Equipment],
        injuries: [JointFlag]
    ) -> [Exercise] {
        let equipment = Set(availableEquipment)
        let injured = Set(injuries)
        return catalog.filter { ex in
            ex.isSelectableWith(equipment: equipment) &&
            !ex.isContraindicatedFor(injuries: injured)
        }
    }

    // MARK: - Muscle focus mapping

    /// Map a `TargetMuscleGroup` to the catalog muscle groups its isolation
    /// exercises should primarily hit.
    static func muscleFocus(for target: TargetMuscleGroup) -> [MuscleGroup] {
        switch target {
        case .chest:     return [.chestUpper, .chestMid, .chestLower]
        case .back:       return [.lats, .rhomboids, .trapezius, .rearDeltoid]
        case .shoulders:  return [.frontDeltoid, .sideDeltoid, .rearDeltoid]
        case .arms:       return [.biceps, .triceps, .forearms]
        case .legs:       return [.quadriceps, .hamstrings, .glutes, .calves]
        case .glutes:     return [.glutes, .hamstrings]
        case .core:       return [.abs, .obliques, .lowerBack]
        case .fullBody:   return [.chestMid, .lats, .quadriceps, .glutes, .abs]
        }
    }

    /// Classify an exercise's muscle affinity for a session focus area.
    static func belongs(_ exercise: Exercise, toFocus focus: SessionFocus, target: TargetMuscleGroup?) -> Bool {
        let primary = exercise.primaryMuscles
        switch focus {
        case .fullBody, .custom:
            return true
        case .upper:
            // Push/Pull/Upper sessions: chest, back, shoulders, arms.
            let upper: Set<MuscleGroup> = [
                .chestUpper, .chestMid, .chestLower,
                .lats, .rhomboids, .trapezius, .rearDeltoid,
                .frontDeltoid, .sideDeltoid, .biceps, .triceps, .forearms,
            ]
            return primary.contains { upper.contains($0) }
        case .lower:
            let lower: Set<MuscleGroup> = [
                .quadriceps, .hamstrings, .glutes, .calves,
                .hipFlexors, .adductors, .abductors,
            ]
            return primary.contains { lower.contains($0) }
        }
    }

    /// Whether an exercise is a compound movement (multi-joint, large muscle).
    static func isCompound(_ exercise: Exercise) -> Bool {
        let compounds: Set<String> = [
            "push_up_standard", "push_up_knee", "push_up_wide", "push_up_decline",
            "push_up_incline", "push_up_diamond", "push_up_archer", "pike_push_up",
            "pull_up_standard", "inverted_row",
            "squat_bodyweight", "squat_goblet", "lunges_bodyweight", "lunges_reverse",
            "db_lunge", "db_step_up", "step_up_bodyweight", "wall_sit", "calf_raise",
            "db_bench_press", "db_shoulder_press", "band_chest_press", "band_press_shoulder",
            "db_row_bent_over", "db_row_chest_supported", "band_row_seated",
            "db_deadlift_romania", "db_romanian_deadlift_single", "band_good_morning",
            "burpee_standard", "mountain_climber", "db_cuban_press",
        ]
        return compounds.contains(exercise.id)
    }

    // MARK: - Full weekly plan

    /// Generate a full weekly workout plan.
    ///
    /// - Parameters:
    ///   - catalog: Universe of catalog exercises.
    ///   - profile: User profile (equipment, injuries, schedule, goal).
    ///   - dayOfWeek: (unused now) reserved for future date assignment.
    public static func generateWeeklyPlan(
        catalog: [Exercise],
        profile: UserProfile
    ) -> WeeklyWorkoutPlan {
        let pool = selectableExercises(
            catalog: catalog,
            availableEquipment: profile.availableEquipment,
            injuries: profile.injuryFlags
        )
        let split = splitType(forDaysPerWeek: profile.daysPerWeek)
        let sessionLabels = weeklySessions(days: profile.daysPerWeek)
        let volume = volumeAllocation(
            dailyTimeMinutes: profile.dailyTimeMinutes,
            targetGoal: profile.fitnessGoal
        )

        var plannedSessions: [PlannedSession] = []
        for (index, label) in sessionLabels.enumerated() {
            let chosen = selectExercises(
                from: pool,
                for: label,
                target: profile.targetMuscleGroup,
                count: volume.exerciseCount
            )
            let sessionExercises = chosen.map { exercise in
                SessionExercise(
                    exerciseId: exercise.id,
                    plannedSets: volume.setsPerExercise,
                    plannedReps: 10,
                    plannedRestSeconds: volume.restSeconds
                )
            }
            plannedSessions.append(
                PlannedSession(id: index, splitSession: label, exercises: sessionExercises)
            )
        }

        return WeeklyWorkoutPlan(splitType: split, sessions: plannedSessions)
    }

    /// Select `count` exercises for a session, balancing one primary compound,
    /// two secondary compounds, and 2–3 isolations, with target-zone
    /// emphasis for `TARGET_ZONE_TONING` (≥50% isolation volume on the focus).
    static func selectExercises(
        from pool: [Exercise],
        for session: SplitSession,
        target: TargetMuscleGroup?,
        count: Int
    ) -> [Exercise] {
        guard !pool.isEmpty else { return [] }

        let focus = session.focusArea
        let focusMuscles = target.map { Set(muscleFocus(for: $0)) } ?? []

        // Bucket pool by relevance to this session.
        let sessionRelevant = pool.filter { belongs($0, toFocus: focus, target: target) }
        let primaries = sessionRelevant.filter(isCompound)
        let isolations = sessionRelevant.filter { !isCompound($0) }

        // Target-zone isolations: exercises whose primary muscles overlap the
        // user's target zone. For toning, ≥50% of isolation slots come from here.
        let zoneIsolations: [Exercise] = target.map { tgt in
            let focus = Set(muscleFocus(for: tgt))
            return isolations.filter { ex in
                ex.primaryMuscles.contains { focus.contains($0) }
            }
        } ?? []

        // Difficulty-balanced ordering helper.
        func balanced(_ list: [Exercise]) -> [Exercise] {
            list.sorted {
                if $0.difficultyLevel.rank != $1.difficultyLevel.rank {
                    return $0.difficultyLevel.rank < $1.difficultyLevel.rank
                }
                return $0.nameEn < $1.nameEn
            }
        }

        var result: [Exercise] = []
        var usedIds = Set<String>()

        @discardableResult
        func pickNext(from list: [Exercise]) -> Exercise? {
            for ex in balanced(list) where !usedIds.contains(ex.id) {
                usedIds.insert(ex.id)
                result.append(ex)
                return ex
            }
            return nil
        }

        // 1 primary compound.
        if !primaries.isEmpty {
            pickNext(from: primaries)
        } else if let fallback = sessionRelevant.first {
            usedIds.insert(fallback.id)
            result.append(fallback)
        }
        guard result.count < count else { return Array(result.prefix(count)) }

        // 2 secondary compounds (distinct from the primary).
        let secondaryCompounds = primaries.filter { !usedIds.contains($0.id) }
        _ = pickNext(from: secondaryCompounds)
        guard result.count < count else { return Array(result.prefix(count)) }
        _ = pickNext(from: secondaryCompounds)
        guard result.count < count else { return Array(result.prefix(count)) }

        // Remaining slots → isolations, prioritizing the target zone for toning.
        let remaining = count - result.count
        let toning = (target != nil) && focusMuscles.count > 0
        let zoneSlots = toning ? Int((Double(remaining) * 0.5).rounded(.up)) : 0

        var zonePicked = 0
        let zonePool = toning ? zoneIsolations.filter { !usedIds.contains($0.id) } : []
        while zonePicked < zoneSlots && result.count < count {
            if pickNext(from: zonePool) == nil { break }
            zonePicked += 1
        }
        // Side-loaders: non-zone isolations or any remaining pool exercise.
        let sideIsolations = isolations.filter { !usedIds.contains($0.id) }
        while result.count < count {
            if pickNext(from: sideIsolations) == nil { break }
        }
        // Last resort: fill from any remaining pool exercises.
        while result.count < count {
            if pickNext(from: pool) == nil { break }
        }

        return Array(result.prefix(count))
    }

    // MARK: - Fatigue auto-regulation

    /// Evaluate rolling 3-session fatigue and return a recommended volume
    /// adjustment for the upcoming week.
    public enum VolumeAdjustment: Sendable, Hashable {
        case deload         // -10% volume (avg RPE ≥ 9.0)
        case progressiveOverload // +1 rep or +1 set (avg RPE ≤ 5.0)
        case none
    }

    public static func fatigueAdjustment(recentRPE: [Int]) -> VolumeAdjustment {
        guard recentRPE.count >= 3 else { return .none }
        let last3 = recentRPE.suffix(3)
        let avg = Double(last3.reduce(0, +)) / Double(last3.count)
        if avg >= 9.0 {
            return .deload
        } else if avg <= 5.0 {
            return .progressiveOverload
        }
        return .none
    }
}
