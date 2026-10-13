package com.leanhome.core.data.mapper

import com.leanhome.core.data.entities.ExerciseEntity
import com.leanhome.core.data.entities.UserProfileEntity
import com.leanhome.core.data.entities.WorkoutSessionEntity
import com.leanhome.core.domain.model.ActivityLevel
import com.leanhome.core.domain.model.BodyArchetype
import com.leanhome.core.domain.model.DifficultyLevel
import com.leanhome.core.domain.model.Equipment
import com.leanhome.core.domain.model.Exercise
import com.leanhome.core.domain.model.FeelingTag
import com.leanhome.core.domain.model.FitnessGoal
import com.leanhome.core.domain.model.Gender
import com.leanhome.core.domain.model.Joint
import com.leanhome.core.domain.model.Muscle
import com.leanhome.core.domain.model.SessionExercise
import com.leanhome.core.domain.model.SessionStatus
import com.leanhome.core.domain.model.SplitName
import com.leanhome.core.domain.model.TargetMuscleGroup
import com.leanhome.core.domain.model.UserProfile
import com.leanhome.core.domain.model.WorkoutSession
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val MAPPER_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private fun parseStringList(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        MAPPER_JSON.decodeFromString(ListSerializer(kotlinx.serialization.builtins.serializer<String>()), raw)
    }.getOrDefault(emptyList())
}

private fun parseJointList(raw: String): List<Joint> =
    parseStringList(raw).mapNotNull { runCatching { Joint.fromString(it) }.getOrNull() }

private fun parseMuscleList(raw: String): List<Muscle> =
    parseStringList(raw).mapNotNull { runCatching { Muscle.fromString(it) }.getOrNull() }

private fun parseEquipmentList(raw: String): List<Equipment> =
    parseStringList(raw).mapNotNull { runCatching { Equipment.fromString(it) }.getOrNull() }

private fun encodeStringList(list: List<String>): String =
    MAPPER_JSON.encodeToString(ListSerializer(kotlinx.serialization.builtins.serializer<String>()), list)

/**
 * Maps Room entities to domain models and vice versa.
 * JSON arrays in SQLite TEXT columns are parsed with kotlinx.serialization.
 */
object EntityMappers {

    // ── Exercise ─────────────────────────────────────────────────────────

    fun exerciseEntityToDomain(entity: ExerciseEntity): Exercise = Exercise(
        id = entity.id,
        nameEn = entity.name_en,
        nameVi = entity.name_vi,
        primaryMuscles = parseMuscleList(entity.primary_muscles),
        secondaryMuscles = parseMuscleList(entity.secondary_muscles),
        requiredEquipment = parseEquipmentList(entity.required_equipment),
        contraindicatedJoints = parseJointList(entity.contraindicated_joints),
        difficultyLevel = DifficultyLevel.fromString(entity.difficulty_level),
        videoUrl = entity.video_url,
        fallbackThumbnailUrl = entity.fallback_thumbnail_url,
        commonMistakes = parseStringList(entity.common_mistakes),
        correctFormCues = parseStringList(entity.correct_form_cues),
    )

    fun exerciseDomainToEntity(exercise: Exercise): ExerciseEntity = ExerciseEntity(
        id = exercise.id,
        name_en = exercise.nameEn,
        name_vi = exercise.nameVi,
        primary_muscles = encodeStringList(exercise.primaryMuscles.map { it.name }),
        secondary_muscles = encodeStringList(exercise.secondaryMuscles.map { it.name }),
        required_equipment = encodeStringList(exercise.requiredEquipment.map { it.name }),
        contraindicated_joints = encodeStringList(exercise.contraindicatedJoints.map { it.name }),
        difficulty_level = exercise.difficultyLevel.name,
        video_url = exercise.videoUrl,
        fallback_thumbnail_url = exercise.fallbackThumbnailUrl,
        common_mistakes = encodeStringList(exercise.commonMistakes),
        correct_form_cues = encodeStringList(exercise.correctFormCues),
    )

    // ── UserProfile ──────────────────────────────────────────────────────

    fun profileEntityToDomain(entity: UserProfileEntity): UserProfile = UserProfile(
        id = entity.id,
        name = entity.name,
        age = entity.age,
        gender = Gender.fromString(entity.gender),
        heightCm = entity.height_cm,
        weightKg = entity.weight_kg,
        countryCode = entity.country_code,
        bodyArchetype = BodyArchetype.valueOf(entity.body_shape),
        fitnessGoal = FitnessGoal.fromString(entity.fitness_goal),
        targetMuscleGroup = entity.target_muscle_group?.let {
            runCatching { TargetMuscleGroup.fromString(it) }.getOrNull()
        },
        availableEquipment = parseEquipmentList(entity.available_equipment),
        injuryFlags = parseJointList(entity.injury_flags),
        activityLevel = entity.activity_level?.let {
            runCatching { ActivityLevel.fromString(it) }.getOrNull() ?: ActivityLevel.SEDENTARY
        } ?: ActivityLevel.SEDENTARY,
        daysPerWeek = entity.days_per_week,
        dailyTimeMinutes = entity.daily_time_minutes,
        targetWeightKg = entity.target_weight_kg,
        targetDays = entity.target_days,
        bmr = entity.bmr,
        tdee = entity.tdee,
        createdAt = entity.created_at,
    )

    fun profileDomainToEntity(profile: UserProfile): UserProfileEntity = UserProfileEntity(
        id = profile.id,
        name = profile.name,
        age = profile.age,
        gender = profile.gender.name,
        height_cm = profile.heightCm,
        weight_kg = profile.weightKg,
        country_code = profile.countryCode,
        body_shape = profile.bodyArchetype.name,
        fitness_goal = profile.fitnessGoal.name,
        target_muscle_group = profile.targetMuscleGroup?.name,
        available_equipment = encodeStringList(profile.availableEquipment.map { it.name }),
        injury_flags = encodeStringList(profile.injuryFlags.map { it.name }),
        activity_level = profile.activityLevel.name,
        days_per_week = profile.daysPerWeek,
        daily_time_minutes = profile.dailyTimeMinutes,
        target_weight_kg = profile.targetWeightKg,
        target_days = profile.targetDays,
        bmr = profile.bmr,
        tdee = profile.tdee,
        created_at = profile.createdAt,
    )

    // ── WorkoutSession ──────────────────────────────────────────────────

    fun sessionEntityToDomain(entity: WorkoutSessionEntity): WorkoutSession {
        val exercisesRaw = if (entity.exercises.isBlank()) "[]" else entity.exercises
        val exercises = runCatching {
            MAPPER_JSON.decodeFromString(ListSerializer(SessionExercise.serializer()), exercisesRaw)
        }.getOrDefault(emptyList())

        return WorkoutSession(
            id = entity.id,
            userId = entity.user_id,
            scheduledDate = entity.scheduled_date,
            splitName = SplitName.fromString(entity.split_name),
            status = SessionStatus.fromString(entity.status),
            exercises = exercises,
            completedDurationSeconds = entity.completed_duration_seconds,
            rpeScore = entity.rpe_score,
            feedbackTags = entity.feedback_tags?.let { tags ->
                parseStringList(tags).mapNotNull { runCatching { FeelingTag.fromString(it) }.getOrNull() }
            } ?: emptyList(),
            feedbackNotes = entity.feedback_notes,
            createdAt = entity.created_at,
        )
    }

    fun sessionDomainToEntity(session: WorkoutSession): WorkoutSessionEntity = WorkoutSessionEntity(
        id = session.id,
        user_id = session.userId,
        scheduled_date = session.scheduledDate,
        split_name = session.splitName.name,
        status = session.status.name,
        exercises = MAPPER_JSON.encodeToString(ListSerializer(SessionExercise.serializer()), session.exercises),
        completed_duration_seconds = session.completedDurationSeconds,
        rpe_score = session.rpeScore,
        feedback_tags = if (session.feedbackTags.isEmpty()) null
            else encodeStringList(session.feedbackTags.map { it.name }),
        feedback_notes = session.feedbackNotes,
        created_at = session.createdAt,
    )
}
