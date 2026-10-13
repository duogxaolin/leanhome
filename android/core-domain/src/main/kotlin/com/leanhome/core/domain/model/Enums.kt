package com.leanhome.core.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Gender {
    MALE,
    FEMALE,
    OTHER;

    companion object {
        fun fromString(value: String): Gender =
            entries.first { it.name == value.uppercase() }
    }
}

@Serializable
enum class ActivityLevel(val pal: Double) {
    @SerialName("SEDENTARY") SEDENTARY(1.2),
    @SerialName("LIGHTLY_ACTIVE") LIGHTLY_ACTIVE(1.375),
    @SerialName("MODERATELY_ACTIVE") MODERATELY_ACTIVE(1.55),
    @SerialName("VERY_ACTIVE") VERY_ACTIVE(1.725);

    companion object {
        fun fromString(value: String): ActivityLevel =
            entries.first { it.name == value.uppercase() }
    }
}

@Serializable
enum class BodyArchetype {
    INVERTED_TRIANGLE,
    RECTANGLE,
    OVAL,
    TRIANGLE,
    HOURGLASS,
    PEAR,
    APPLE;

    companion object {
        fun forGender(gender: Gender): List<BodyArchetype> = when (gender) {
            Gender.MALE -> listOf(INVERTED_TRIANGLE, RECTANGLE, OVAL, TRIANGLE)
            Gender.FEMALE -> listOf(HOURGLASS, PEAR, INVERTED_TRIANGLE, RECTANGLE, APPLE)
            Gender.OTHER -> listOf(INVERTED_TRIANGLE, RECTANGLE, OVAL, TRIANGLE, HOURGLASS, PEAR, APPLE)
        }
    }
}

@Serializable
enum class FitnessGoal {
    HEALTH_MAINTENANCE,
    FAT_LOSS,
    WEIGHT_CUT,
    TARGET_ZONE_TONING;

    companion object {
        fun fromString(value: String): FitnessGoal =
            entries.first { it.name == value.uppercase() }
    }
}

@Serializable
enum class TargetMuscleGroup {
    CHEST,
    BACK,
    SHOULDERS,
    ARMS,
    LEGS,
    GLUTES,
    CORE,
    FULL_BODY;

    companion object {
        fun fromString(value: String): TargetMuscleGroup =
            entries.first { it.name == value.uppercase() }
    }
}

@Serializable
enum class Equipment {
    BODYWEIGHT_ONLY,
    RESISTANCE_BANDS,
    DUMBBELLS,
    PULL_UP_BAR,
    KETTLEBELL,
    BARBELL,
    FULL_GYM;

    companion object {
        fun fromString(value: String): Equipment =
            entries.first { it.name == value.uppercase() }
    }
}

@Serializable
enum class Joint {
    NECK,
    SHOULDER,
    ELBOW,
    WRIST,
    LOWER_BACK,
    HIP,
    KNEE,
    ANKLE;

    companion object {
        fun fromString(value: String): Joint =
            entries.first { it.name == value.uppercase() }
    }
}

@Serializable
enum class Muscle {
    CHEST_UPPER,
    CHEST_MID,
    CHEST_LOWER,
    LATS,
    TRAPEZIUS,
    RHOMBOIDS,
    REAR_DELTOID,
    FRONT_DELTOID,
    SIDE_DELTOID,
    BICEPS,
    TRICEPS,
    FOREARMS,
    ABS,
    OBLIQUES,
    LOWER_BACK,
    GLUTES,
    HAMSTRINGS,
    QUADRICEPS,
    CALVES,
    HIP_FLEXORS,
    ADDUCTORS,
    ABDUCTORS,
    ROTATOR_CUFF,
    SHOULDER,
    CORE;

    companion object {
        fun fromString(value: String): Muscle =
            entries.firstOrNull { it.name == value.uppercase() }
                ?: throw IllegalArgumentException("Unknown muscle: $value")
    }
}

@Serializable
enum class DifficultyLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED;

    companion object {
        fun fromString(value: String): DifficultyLevel =
            entries.first { it.name == value.uppercase() }
    }
}
