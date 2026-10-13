package com.leanhome.core.domain.engine

import com.leanhome.core.domain.model.BodyArchetype
import com.leanhome.core.domain.model.DifficultyLevel
import com.leanhome.core.domain.model.Equipment
import com.leanhome.core.domain.model.Exercise
import com.leanhome.core.domain.model.FitnessGoal
import com.leanhome.core.domain.model.Gender
import com.leanhome.core.domain.model.Joint
import com.leanhome.core.domain.model.Muscle
import com.leanhome.core.domain.model.SplitName
import com.leanhome.core.domain.model.TargetMuscleGroup
import com.leanhome.core.domain.model.UserProfile

/**
 * Workout Routine Generator — offline constraint solver that creates
 * personalized workout splits and exercise sessions based on user equipment
 * availability, joint/injury contraindications, training frequency, and
 * muscle group focus.
 */
class WorkoutGenerator {

    // ── Split Selection ──────────────────────────────────────────────────

    data class SplitPlan(
        val splitType: SplitType,
        val sessions: List<SplitName>,
    )

    enum class SplitType { FULL_BODY, UPPER_LOWER, PPL }

    /**
     * Assigns a balanced weekly split based on training frequency.
     *   F = 2 or 3: Full Body A/B alternating
     *   F = 4:     Upper/Lower (Upper A, Lower A, Upper B, Lower B)
     *   F = 5 or 6: Push/Pull/Legs
     */
    fun selectSplit(daysPerWeek: Int): SplitPlan {
        val sessions = mutableListOf<SplitName>()
        when (daysPerWeek) {
            in 2..3 -> {
                repeat(daysPerWeek) { i ->
                    sessions.add(if (i % 2 == 0) SplitName.FULL_BODY_A else SplitName.FULL_BODY_B)
                }
                return SplitPlan(SplitType.FULL_BODY, sessions)
            }
            4 -> {
                sessions.addAll(listOf(SplitName.UPPER_A, SplitName.LOWER_A, SplitName.UPPER_B, SplitName.LOWER_B))
                return SplitPlan(SplitType.UPPER_LOWER, sessions)
            }
            5 -> {
                sessions.addAll(listOf(SplitName.PUSH, SplitName.PULL, SplitName.LEGS, SplitName.PUSH, SplitName.PULL))
                return SplitPlan(SplitType.PPL, sessions)
            }
            6 -> {
                sessions.addAll(listOf(SplitName.PUSH, SplitName.PULL, SplitName.LEGS, SplitName.PUSH, SplitName.PULL, SplitName.LEGS))
                return SplitPlan(SplitType.PPL, sessions)
            }
            else -> throw IllegalArgumentException("Days per week must be 2-6, got $daysPerWeek")
        }
    }

    // ── Exercise Filtering ──────────────────────────────────────────────

    /**
     * Filter the universe of catalog exercises by:
     * 1. Equipment constraint: ALL required_equipment must be in user's set.
     * 2. Injury exclusion: exclude exercises whose contraindicated_joints
     *    overlap with user's injury flags.
     */
    fun filterExercises(
        exercises: List<Exercise>,
        userEquipment: Set<Equipment>,
        injuryFlags: Set<Joint>,
    ): List<Exercise> {
        return exercises.filter { ex ->
            ex.isAvailableWith(userEquipment) && !ex.isContraindicatedFor(injuryFlags)
        }
    }

    /**
     * Additional filter by difficulty level.
     */
    fun filterByDifficulty(exercises: List<Exercise>, maxDifficulty: DifficultyLevel): List<Exercise> {
        val order = listOf(DifficultyLevel.BEGINNER, DifficultyLevel.INTERMEDIATE, DifficultyLevel.ADVANCED)
        val maxIdx = order.indexOf(maxDifficulty)
        return exercises.filter { order.indexOf(it.difficultyLevel) <= maxIdx }
    }

    /**
     * Filter exercises whose primary muscles contain at least one of the specified muscles.
     */
    fun filterByPrimaryMuscles(exercises: List<Exercise>, targetMuscles: Set<Muscle>): List<Exercise> {
        return exercises.filter { ex ->
            ex.primaryMuscles.any { it in targetMuscles }
        }
    }

    // ── Session Volume Allocation ────────────────────────────────────────

    /**
     * Determine the number of exercises and sets based on the daily time limit.
     *
     * Given total_minutes, rest_seconds_per_set, and average
     * work_seconds_per_set, compute total workable sets and fill the session
     * with 4-7 exercises and 3 sets each (15-25 working sets).
     */
    data class VolumeAllocation(
        val exerciseCount: Int,
        val setsPerExercise: Int,
        val totalWorkingSets: Int,
        val restSeconds: Int,
    )

    fun allocateVolume(dailyTimeMinutes: Int): VolumeAllocation {
        val totalMinutes = dailyTimeMinutes.toDouble()
        // Allocate 4-7 exercises. Default: 5 exercises, 3 sets each = 15 sets.
        // 30 min → 4 exercises, 3 sets each (12 sets) with 60s rest
        val exerciseCount: Int
        val setsPerExercise: Int
        val restSeconds: Int

        when {
            totalMinutes <= 20.0 -> {
                exerciseCount = 4
                setsPerExercise = 2
                restSeconds = 45
            }
            totalMinutes <= 30.0 -> {
                exerciseCount = 4
                setsPerExercise = 3
                restSeconds = 60
            }
            totalMinutes <= 45.0 -> {
                exerciseCount = 5
                setsPerExercise = 3
                restSeconds = 75
            }
            totalMinutes <= 60.0 -> {
                exerciseCount = 6
                setsPerExercise = 3
                restSeconds = 90
            }
            else -> {
                exerciseCount = 7
                setsPerExercise = 3
                restSeconds = 90
            }
        }

        return VolumeAllocation(
            exerciseCount = exerciseCount,
            setsPerExercise = setsPerExercise,
            totalWorkingSets = exerciseCount * setsPerExercise,
            restSeconds = restSeconds,
        )
    }

    // ── Muscle Category Classification ───────────────────────────────────

    /**
     * Map a Muscle to a muscle-group category for split assignment.
     */
    enum class MuscleGroup { UPPER_PUSH, UPPER_PULL, LOWER, CORE, FULL_BODY }

    fun muscleToGroup(muscle: Muscle): MuscleGroup = when (muscle) {
        Muscle.CHEST_UPPER, Muscle.CHEST_MID, Muscle.CHEST_LOWER,
        Muscle.FRONT_DELTOID, Muscle.SIDE_DELTOID, Muscle.TRICEPS,
        Muscle.ROTATOR_CUFF, Muscle.SHOULDER,
        -> MuscleGroup.UPPER_PUSH

        Muscle.LATS, Muscle.TRAPEZIUS, Muscle.RHOMBOIDS, Muscle.REAR_DELTOID,
        Muscle.BICEPS, Muscle.FOREARMS,
        -> MuscleGroup.UPPER_PULL

        Muscle.QUADRICEPS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES,
        Muscle.HIP_FLEXORS, Muscle.ADDUCTORS, Muscle.ABDUCTORS,
        -> MuscleGroup.LOWER

        Muscle.ABS, Muscle.OBLIQUES, Muscle.CORE,
        -> MuscleGroup.CORE

        Muscle.LOWER_BACK -> MuscleGroup.LOWER // lower back trains with lower/posterior chain
    }

    /**
     * Classify an exercise's primary group by its first primary muscle.
     */
    fun exerciseGroup(exercise: Exercise): MuscleGroup {
        return exercise.primaryMuscles.firstOrNull()?.let { muscleToGroup(it) } ?: MuscleGroup.FULL_BODY
    }

    // ── Target Zone Mapping ─────────────────────────────────────────────

    /**
     * Map a TargetMuscleGroup to the raw Muscle enums it encompasses.
     */
    fun targetZoneToMuscles(zone: TargetMuscleGroup): Set<Muscle> = when (zone) {
        TargetMuscleGroup.CHEST -> setOf(Muscle.CHEST_UPPER, Muscle.CHEST_MID, Muscle.CHEST_LOWER)
        TargetMuscleGroup.BACK -> setOf(Muscle.LATS, Muscle.TRAPEZIUS, Muscle.RHOMBOIDS, Muscle.REAR_DELTOID)
        TargetMuscleGroup.SHOULDERS -> setOf(Muscle.FRONT_DELTOID, Muscle.SIDE_DELTOID, Muscle.REAR_DELTOID)
        TargetMuscleGroup.ARMS -> setOf(Muscle.BICEPS, Muscle.TRICEPS, Muscle.FOREARMS)
        TargetMuscleGroup.LEGS -> setOf(Muscle.QUADRICEPS, Muscle.HAMSTRINGS, Muscle.CALVES, Muscle.ADDUCTORS, Muscle.ABDUCTORS)
        TargetMuscleGroup.GLUTES -> setOf(Muscle.GLUTES)
        TargetMuscleGroup.CORE -> setOf(Muscle.ABS, Muscle.OBLIQUES, Muscle.CORE, Muscle.LOWER_BACK)
        TargetMuscleGroup.FULL_BODY -> emptySet() // means no specific focus
    }

    // ── Session Generation ──────────────────────────────────────────────

    data class SessionPlan(
        val splitName: SplitName,
        val exercises: List<SessionExerciseSpec>,
    )

    data class SessionExerciseSpec(
        val exercise: Exercise,
        val sets: Int,
        val reps: Int,
        val restSeconds: Int,
        val role: ExerciseRole,
    )

    enum class ExerciseRole { PRIMARY_COMPOUND, SECONDARY_COMPOUND, ISOLATION_ACCESSORY, CARDIO_CONDITIONING }

    /**
     * Generate a session for a given split day.
     *
     * Selects 4-7 exercises: 1 primary compound, 2 secondary compound,
     * 2-3 isolation/accessory movements targeting the user's focus.
     * Maintains compound balance across the full body.
     */
    fun generateSession(
        splitName: SplitName,
        candidateExercises: List<Exercise>,
        userEquipment: Set<Equipment>,
        injuryFlags: Set<Joint>,
        profile: UserProfile,
    ): SessionPlan {
        // Step 1: filter by equipment and injury
        val filtered = filterExercises(candidateExercises, userEquipment, injuryFlags)

        // Step 2: Determine target muscles for this split
        val targetGroups = musclesForSplit(splitName)

        // Step 3: Partition filtered exercises by group and role
        val upperPush = filterByGroup(filtered, MuscleGroup.UPPER_PUSH)
        val upperPull = filterByGroup(filtered, MuscleGroup.UPPER_PULL)
        val lower = filterByGroup(filtered, MuscleGroup.LOWER)
        val core = filterByGroup(filtered, MuscleGroup.CORE)

        // Step 4: Determine max difficulty based on goal (fat loss = intermediate, maintenance = intermediate, weight cut = beginner-intermediate)
        val maxDiff = maxDifficultyForGoal(profile.fitnessGoal, profile.bodyArchetype)
        val safeFiltered = filterByDifficulty(filtered, maxDiff)

        // Step 5: Allocate volume
        val vol = allocateVolume(profile.dailyTimeMinutes)

        // Step 6: Select exercises for this split
        val selected = selectForSplit(
            splitName = splitName,
            exercises = safeFiltered,
            upperPush = filterByGroup(safeFiltered, MuscleGroup.UPPER_PUSH),
            upperPull = filterByGroup(safeFiltered, MuscleGroup.UPPER_PULL),
            lower = filterByGroup(safeFiltered, MuscleGroup.LOWER),
            core = filterByGroup(safeFiltered, MuscleGroup.CORE),
            vol = vol,
            profile = profile,
        )

        return SessionPlan(
            splitName = splitName,
            exercises = selected.map { ex ->
                SessionExerciseSpec(
                    exercise = ex.exercise,
                    sets = ex.sets,
                    reps = ex.reps,
                    restSeconds = vol.restSeconds,
                    role = ex.role,
                )
            }
        )
    }

    private data class SelectedExercise(
        val exercise: Exercise,
        val sets: Int,
        val reps: Int,
        val role: ExerciseRole,
    )

    private fun filterByGroup(exercises: List<Exercise>, group: MuscleGroup): List<Exercise> {
        return exercises.filter { exerciseGroup(it) == group }
    }

    private fun maxDifficultyForGoal(goal: FitnessGoal, archetype: BodyArchetype): DifficultyLevel {
        return when (goal) {
            FitnessGoal.HEALTH_MAINTENANCE -> DifficultyLevel.INTERMEDIATE
            FitnessGoal.FAT_LOSS -> DifficultyLevel.INTERMEDIATE
            FitnessGoal.WEIGHT_CUT -> DifficultyLevel.BEGINNER
            FitnessGoal.TARGET_ZONE_TONING -> DifficultyLevel.ADVANCED
        }
    }

    /**
     * Determine the target muscle groups for a given split name.
     */
    private fun musclesForSplit(splitName: SplitName): Set<MuscleGroup> = when (splitName) {
        SplitName.FULL_BODY_A, SplitName.FULL_BODY_B ->
            setOf(MuscleGroup.UPPER_PUSH, MuscleGroup.UPPER_PULL, MuscleGroup.LOWER, MuscleGroup.CORE)
        SplitName.UPPER_A, SplitName.UPPER_B ->
            setOf(MuscleGroup.UPPER_PUSH, MuscleGroup.UPPER_PULL, MuscleGroup.CORE)
        SplitName.LOWER_A, SplitName.LOWER_B ->
            setOf(MuscleGroup.LOWER, MuscleGroup.CORE)
        SplitName.PUSH -> setOf(MuscleGroup.UPPER_PUSH)
        SplitName.PULL -> setOf(MuscleGroup.UPPER_PULL, MuscleGroup.CORE)
        SplitName.LEGS -> setOf(MuscleGroup.LOWER, MuscleGroup.CORE)
        SplitName.FOCUS -> setOf(MuscleGroup.UPPER_PUSH, MuscleGroup.UPPER_PULL, MuscleGroup.LOWER)
    }

    /**
     * For maintenance goal, focus on cardio/conditioning.
     */
    private fun isCardioExercise(exercise: Exercise): Boolean {
        val cardioIds = setOf(
            "jumping_jack", "mountain_climber", "burpee_standard", "high_knees",
            "calf_raise", "wall_sit"
        )
        return exercise.id in cardioIds
    }

    private fun selectForSplit(
        splitName: SplitName,
        exercises: List<Exercise>,
        upperPush: List<Exercise>,
        upperPull: List<Exercise>,
        lower: List<Exercise>,
        core: List<Exercise>,
        vol: VolumeAllocation,
        profile: UserProfile,
    ): List<SelectedExercise> {
        val selected = mutableListOf<SelectedExercise>()
        val used = mutableSetOf<String>()

        // For maintenance goal: prioritize general conditioning
        val isMaintenance = profile.fitnessGoal == FitnessGoal.HEALTH_MAINTENANCE
        if (isMaintenance) {
            val cardioCandidates = exercises.filter { isCardioExercise(it) }
            if (cardioCandidates.isNotEmpty()) {
                cardioCandidates.take(2).forEach { ex ->
                    selected.add(SelectedExercise(ex, vol.setsPerExercise, 12, ExerciseRole.CARDIO_CONDITIONING))
                    used.add(ex.id)
                }
            }
        }

        // Determine how many exercises from each group
        when (splitName) {
            SplitName.FULL_BODY_A, SplitName.FULL_BODY_B -> {
                addExercisesToSelected(upperPush, 1, ExerciseRole.PRIMARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(lower, 1, ExerciseRole.PRIMARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(upperPull, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(lower, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                // Isolation: target zone + core
                val isolationPool = pickIsolationPool(profile, exercises, used)
                addExercisesToSelected(isolationPool, vol.exerciseCount - selected.size, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
                if (selected.size < 4) addExercisesToSelected(core, 4 - selected.size, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
            }
            SplitName.UPPER_A, SplitName.UPPER_B -> {
                addExercisesToSelected(upperPush, 1, ExerciseRole.PRIMARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(upperPull, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(upperPush, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(upperPull, 1, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
                val upperIsolation = exercises.filter { exerciseGroup(it) == MuscleGroup.UPPER_PUSH || exerciseGroup(it) == MuscleGroup.UPPER_PULL }.filterNot { it.id in used }
                addExercisesToSelected(upperIsolation, vol.exerciseCount - selected.size, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
                if (selected.size < 4) addExercisesToSelected(core, 4 - selected.size, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
            }
            SplitName.LOWER_A, SplitName.LOWER_B -> {
                addExercisesToSelected(lower, 1, ExerciseRole.PRIMARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(lower, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(lower, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(lower, vol.exerciseCount - 3, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
                if (selected.size < 4) addExercisesToSelected(core, 4 - selected.size, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
            }
            SplitName.PUSH -> {
                addExercisesToSelected(upperPush, 1, ExerciseRole.PRIMARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(upperPush, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(upperPush, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(upperPush, vol.exerciseCount - selected.size, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
            }
            SplitName.PULL -> {
                addExercisesToSelected(upperPull, 1, ExerciseRole.PRIMARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(upperPull, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(upperPull, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(upperPull, vol.exerciseCount - 3, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
                if (selected.size < 4) addExercisesToSelected(core, 4 - selected.size, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
            }
            SplitName.LEGS -> {
                addExercisesToSelected(lower, 1, ExerciseRole.PRIMARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(lower, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(lower, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(lower, vol.exerciseCount - 3, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
            }
            SplitName.FOCUS -> {
                // Full body with target zone emphasis
                addExercisesToSelected(upperPush, 1, ExerciseRole.PRIMARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(lower, 1, ExerciseRole.PRIMARY_COMPOUND, vol, selected, used)
                addExercisesToSelected(upperPull, 1, ExerciseRole.SECONDARY_COMPOUND, vol, selected, used)
                val isolationPool = pickIsolationPool(profile, exercises, used)
                addExercisesToSelected(isolationPool, vol.exerciseCount - selected.size, ExerciseRole.ISOLATION_ACCESSORY, vol, selected, used)
            }
        }

        return selected.sortedBy { exerciseGroup(it.exercise).ordinal }
    }

    /**
     * Pick isolation exercises for the user's target zone.
     * For TARGET_ZONE_TONING, ≥50% of isolation volume must target the selected muscle.
     */
    private fun pickIsolationPool(profile: UserProfile, allExercises: List<Exercise>, used: Set<String>): List<Exercise> {
        val available = allExercises.filterNot { it.id in used }

        if (profile.fitnessGoal != FitnessGoal.TARGET_ZONE_TONING || profile.targetMuscleGroup == null) {
            return available
        }

        val targetMuscles = targetZoneToMuscles(profile.targetMuscleGroup)
        val focused = available.filter { ex ->
            ex.primaryMuscles.any { it in targetMuscles }
        }
        val nonFocused = available.filter { ex ->
            ex.primaryMuscles.none { it in targetMuscles }
        }
        // Allocate ≥50% from the focused pool
        return focused + nonFocused
    }

    private fun addExercisesToSelected(
        pool: List<Exercise>,
        count: Int,
        role: ExerciseRole,
        vol: VolumeAllocation,
        selected: MutableList<SelectedExercise>,
        used: MutableSet<String>,
    ) {
        if (count <= 0) return
        // If pool is the same object as used exclusions are already applied, but filter just in case
        val candidates = pool.filterNot { it.id in used }
        val reps = defaultRepsForRole(role, vol)
        candidates.take(count).forEach { ex ->
            selected.add(SelectedExercise(ex, vol.setsPerExercise, reps, role))
            used.add(ex.id)
        }
    }

    private fun defaultRepsForRole(role: ExerciseRole, vol: VolumeAllocation): Int = when {
        role == ExerciseRole.PRIMARY_COMPOUND -> 10
        role == ExerciseRole.SECONDARY_COMPOUND -> 12
        role == ExerciseRole.CARDIO_CONDITIONING -> 20
        else -> 15
    }
}
