//
//  DomainEnums.swift
//  CoreDomain
//
//  Canonical domain enumerations shared across the LeanHome iOS app.
//  All enums are `Sendable` for Swift 6 strict concurrency and Codable
//  for SwiftData / JSON round-tripping.
//

import Foundation

// MARK: - Biometric / Activity

/// Physical Activity Level multiplier applied to BMR to derive TDEE.
public enum ActivityLevel: String, Codable, Sendable, CaseIterable, Identifiable {
    case sedentary = "SEDENTARY"
    case lightlyActive = "LIGHTLY_ACTIVE"
    case moderatelyActive = "MODERATELY_ACTIVE"
    case veryActive = "VERY_ACTIVE"

    public var id: String { rawValue }

    /// PAL coefficient per the Mifflin-St Jeor TDEE model.
    public var pal: Double {
        switch self {
        case .sedentary:          return 1.2
        case .lightlyActive:      return 1.375
        case .moderatelyActive:   return 1.55
        case .veryActive:         return 1.725
        }
    }

    public var displayName: String {
        switch self {
        case .sedentary:        return "Sedentary"
        case .lightlyActive:    return "Lightly Active"
        case .moderatelyActive: return "Moderately Active"
        case .veryActive:       return "Very Active"
        }
    }
}

/// User gender. `OTHER` resolves to the male Mifflin-St Jeor formula
/// (canonical test-vector behavior).
public enum Gender: String, Codable, Sendable, CaseIterable, Identifiable {
    case male = "MALE"
    case female = "FEMALE"
    case other = "OTHER"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .male:   return "Male"
        case .female: return "Female"
        case .other:  return "Other"
        }
    }
}

// MARK: - BMI classification

/// WHO standard used to classify BMI, determined by country code.
public enum BMIStandard: String, Codable, Sendable {
    case asianWHO = "ASIAN_WHO"
    case internationalWHO = "INTERNATIONAL_WHO"

    public var displayName: String {
        switch self {
        case .asianWHO:         return "Asian WHO Standard"
        case .internationalWHO: return "International WHO Standard"
        }
    }
}

/// BMI classification category.
public enum BMIClassification: String, Codable, Sendable {
    case underweight = "UNDERWEIGHT"
    case normal = "NORMAL"
    case overweight = "OVERWEIGHT"
    case obese = "OBESE"
}

// MARK: - Fitness goal + body shape

public enum FitnessGoal: String, Codable, Sendable, CaseIterable, Identifiable {
    case healthMaintenance = "HEALTH_MAINTENANCE"
    case fatLoss = "FAT_LOSS"
    case weightCut = "WEIGHT_CUT"
    case targetZoneToning = "TARGET_ZONE_TONING"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .healthMaintenance: return "Health Maintenance"
        case .fatLoss:            return "Fat Loss"
        case .weightCut:          return "Weight Cut"
        case .targetZoneToning:   return "Target Zone Toning"
        }
    }

    /// `true` when the goal targets a caloric deficit (requires a timeline).
    public var requiresDeficit: Bool {
        switch self {
        case .fatLoss, .weightCut, .targetZoneToning: return true
        case .healthMaintenance:                      return false
        }
    }
}

/// 2D visual body shape archetype (V1 illustration selection).
public enum BodyShape: String, Codable, Sendable, CaseIterable, Identifiable {
    case invertedTriangle = "INVERTED_TRIANGLE"
    case rectangle = "RECTANGLE"
    case oval = "OVAL"
    case triangle = "TRIANGLE"
    case hourglass = "HOURGLASS"
    case pear = "PEAR"
    case apple = "APPLE"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .invertedTriangle: return "Inverted Triangle"
        case .rectangle:        return "Rectangle"
        case .oval:             return "Oval"
        case .triangle:         return "Triangle"
        case .hourglass:         return "Hourglass"
        case .pear:              return "Pear"
        case .apple:             return "Apple"
        }
    }

    public static func options(for gender: Gender) -> [BodyShape] {
        switch gender {
        case .male, .other:
            return [.invertedTriangle, .rectangle, .oval, .triangle]
        case .female:
            return [.hourglass, .pear, .invertedTriangle, .rectangle, .apple]
        }
    }
}

/// Target muscle focus for `TARGET_ZONE_TONING`.
public enum TargetMuscleGroup: String, Codable, Sendable, CaseIterable, Identifiable {
    case chest = "CHEST"
    case back = "BACK"
    case shoulders = "SHOULDERS"
    case arms = "ARMS"
    case legs = "LEGS"
    case glutes = "GLUTES"
    case core = "CORE"
    case fullBody = "FULL_BODY"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .chest:     return "Chest"
        case .back:      return "Back"
        case .shoulders: return "Shoulders"
        case .arms:      return "Arms"
        case .legs:      return "Legs"
        case .glutes:    return "Glutes"
        case .core:      return "Core"
        case .fullBody:  return "Full Body"
        }
    }
}

// MARK: - Equipment / joints / muscles

public enum Equipment: String, Codable, Sendable, CaseIterable, Identifiable {
    case bodyweightOnly = "BODYWEIGHT_ONLY"
    case resistanceBands = "RESISTANCE_BANDS"
    case dumbbells = "DUMBBELLS"
    case pullUpBar = "PULL_UP_BAR"
    case kettlebell = "KETTLEBELL"
    case barbell = "BARBELL"
    case fullGym = "FULL_GYM"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .bodyweightOnly:  return "Bodyweight Only"
        case .resistanceBands: return "Resistance Bands"
        case .dumbbells:        return "Dumbbells"
        case .pullUpBar:        return "Pull-Up Bar"
        case .kettlebell:      return "Kettlebell"
        case .barbell:          return "Barbell"
        case .fullGym:          return "Full Gym"
        }
    }
}

public enum JointFlag: String, Codable, Sendable, CaseIterable, Identifiable {
    case neck = "NECK"
    case shoulder = "SHOULDER"
    case elbow = "ELBOW"
    case wrist = "WRIST"
    case lowerBack = "LOWER_BACK"
    case hip = "HIP"
    case knee = "KNEE"
    case ankle = "ANKLE"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .neck:       return "Neck"
        case .shoulder:   return "Shoulder"
        case .elbow:      return "Elbow"
        case .wrist:      return "Wrist"
        case .lowerBack:  return "Lower Back"
        case .hip:        return "Hip"
        case .knee:       return "Knee"
        case .ankle:      return "Ankle"
        }
    }
}

public enum MuscleGroup: String, Codable, Sendable, CaseIterable {
    case chestUpper = "CHEST_UPPER"
    case chestMid = "CHEST_MID"
    case chestLower = "CHEST_LOWER"
    case lats = "LATS"
    case trapezius = "TRAPEZIUS"
    case rhomboids = "RHOMBOIDS"
    case rearDeltoid = "REAR_DELTOID"
    case frontDeltoid = "FRONT_DELTOID"
    case sideDeltoid = "SIDE_DELTOID"
    case biceps = "BICEPS"
    case triceps = "TRICEPS"
    case forearms = "FOREARMS"
    case abs = "ABS"
    case obliques = "OBLIQUES"
    case lowerBack = "LOWER_BACK"
    case glutes = "GLUTES"
    case hamstrings = "HAMSTRINGS"
    case quadriceps = "QUADRICEPS"
    case calves = "CALVES"
    case hipFlexors = "HIP_FLEXORS"
    case adductors = "ADDUCTORS"
    case abductors = "ABDUCTORS"
    case core = "CORE"
    case rotatorCuff = "ROTATOR_CUFF"
    case shoulder = "SHOULDER"
}

// MARK: - Difficulty

public enum DifficultyLevel: String, Codable, Sendable, CaseIterable, Identifiable {
    case beginner = "BEGINNER"
    case intermediate = "INTERMEDIATE"
    case advanced = "ADVANCED"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .beginner:     return "Beginner"
        case .intermediate: return "Intermediate"
        case .advanced:     return "Advanced"
        }
    }

    /// Relative difficulty ranking for session balancing.
    public var rank: Int {
        switch self {
        case .beginner:     return 0
        case .intermediate: return 1
        case .advanced:     return 2
        }
    }
}

// MARK: - Workout split

public enum SplitType: String, Codable, Sendable {
    case fullBody = "FULL_BODY"
    case upperLower = "UPPER_LOWER"
    case ppl = "PPL"
    case focus = "FOCUS"
}

public enum SplitSession: String, Codable, Sendable, CaseIterable, Identifiable {
    case fullBodyA = "FULL_BODY_A"
    case fullBodyB = "FULL_BODY_B"
    case upperA = "UPPER_A"
    case upperB = "UPPER_B"
    case lowerA = "LOWER_A"
    case lowerB = "LOWER_B"
    case push = "PUSH"
    case pull = "PULL"
    case legs = "LEGS"
    case focus = "FOCUS"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .fullBodyA: return "Full Body A"
        case .fullBodyB: return "Full Body B"
        case .upperA:    return "Upper A"
        case .upperB:    return "Upper B"
        case .lowerA:    return "Lower A"
        case .lowerB:    return "Lower B"
        case .push:      return "Push"
        case .pull:      return "Pull"
        case .legs:      return "Legs"
        case .focus:     return "Focus"
        }
    }

    /// Broad classification of the session for muscle targeting.
    var focusArea: SessionFocus {
        switch self {
        case .fullBodyA, .fullBodyB: return .fullBody
        case .upperA, .upperB, .push, .pull: return .upper
        case .lowerA, .lowerB, .legs: return .lower
        case .focus: return .custom
        }
    }
}

enum SessionFocus: Sendable {
    case fullBody, upper, lower, custom
}

// MARK: - Session status / feedback

public enum SessionStatus: String, Codable, Sendable {
    case pending = "PENDING"
    case completed = "COMPLETED"
    case skipped = "SKIPPED"
}

public enum FeelingTag: String, Codable, Sendable, CaseIterable, Identifiable {
    case great = "GREAT"
    case tired = "TIRED"
    case jointDiscomfort = "JOINT_DISCOMFORT"
    case energized = "ENERGIZED"
    case breezy = "BREEZY"
    case brutal = "BRUTAL"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .great:            return "Great"
        case .tired:            return "Tired"
        case .jointDiscomfort:  return "Joint Discomfort"
        case .energized:        return "Energized"
        case .breezy:          return "Breezy"
        case .brutal:          return "Brutal"
        }
    }

    public var emoji: String {
        switch self {
        case .great:           return "😎"
        case .tired:           return "😴"
        case .jointDiscomfort: return "🦴"
        case .energized:       return "⚡️"
        case .breezy:          return "🍃"
        case .brutal:          return "🔥"
        }
    }
}

/// Safety / warning level returned by deficit assessment.
public enum WarningLevel: String, Codable, Sendable {
    case none = "NONE"
    case atCeiling = "AT_CEILING"
    case extreme = "EXTREME"

    public var displayName: String {
        switch self {
        case .none:       return "Safe"
        case .atCeiling:  return "At Ceiling"
        case .extreme:    return "High Risk / Extreme Deficit"
        }
    }
}
