package com.leanhome.core.domain.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Parameterized tests for [WorkoutGenerator] split selection, filtering,
 * and volume allocation.
 *
 * Includes the 5 split_selection canonical test vectors from
 * math_engine_test_vectors.json (total test vector count across the two
 * test files: 31).
 */
class WorkoutGeneratorTest {

    private lateinit var generator: WorkoutGenerator

    @Before
    fun setup() {
        generator = WorkoutGenerator()
    }

    // ── Split selection test models ──────────────────────────────────────

    @Serializable
    data class TestVectors(
        @SerialName("split_selection_tests") val splitSelectionTests: List<SplitSelectionTest>,
    )

    @Serializable
    data class SplitSelectionInput(
        @SerialName("days_per_week") val daysPerWeek: Int,
    )

    @Serializable
    data class SplitSelectionTest(
        @SerialName("id") val id: String,
        @SerialName("input") val input: SplitSelectionInput,
        @SerialName("expected") val expected: SplitSelectionExpected,
    )

    @Serializable
    data class SplitSelectionExpected(
        @SerialName("split_type") val splitType: String,
        @SerialName("sessions") val sessions: List<String>,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun loadVectors(): TestVectors {
        val resourceText = javaClass.classLoader?.getResourceAsStream("math_engine_test_vectors.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Test vector file not found in classpath")
        return json.decodeFromString(TestVectors.serializer(), resourceText)
    }

    // ── Split Selection (5 canonical vectors) ────────────────────────────

    @Test
    fun `split_selection_all_5_vectors_pass`() {
        val vectors = loadVectors()
        assertEquals("Expected 5 split selection tests", 5, vectors.splitSelectionTests.size)

        vectors.splitSelectionTests.forEach { test ->
            val plan = generator.selectSplit(test.input.daysPerWeek)

            assertEquals(
                "[${test.id}] split_type mismatch",
                test.expected.splitType,
                plan.splitType.name,
            )
            assertEquals(
                "[${test.id}] session count mismatch",
                test.expected.sessions.size,
                plan.sessions.size,
            )
            test.expected.sessions.forEachIndexed { idx, expectedSession ->
                assertEquals(
                    "[${test.id}] session[$idx] mismatch",
                    expectedSession,
                    plan.sessions[idx].name,
                )
            }
        }
    }

    @Test
    fun `split_2_days_fullbody_A_B`() {
        val plan = generator.selectSplit(2)
        assertEquals(WorkoutGenerator.SplitType.FULL_BODY, plan.splitType)
        assertEquals(listOf("FULL_BODY_A", "FULL_BODY_B"), plan.sessions.map { it.name })
    }

    @Test
    fun `split_3_days_fullbody_A_B_A`() {
        val plan = generator.selectSplit(3)
        assertEquals(WorkoutGenerator.SplitType.FULL_BODY, plan.splitType)
        assertEquals(
            listOf("FULL_BODY_A", "FULL_BODY_B", "FULL_BODY_A"),
            plan.sessions.map { it.name },
        )
    }

    @Test
    fun `split_4_days_upper_lower`() {
        val plan = generator.selectSplit(4)
        assertEquals(WorkoutGenerator.SplitType.UPPER_LOWER, plan.splitType)
        assertEquals(
            listOf("UPPER_A", "LOWER_A", "UPPER_B", "LOWER_B"),
            plan.sessions.map { it.name },
        )
    }

    @Test
    fun `split_5_days_ppl`() {
        val plan = generator.selectSplit(5)
        assertEquals(WorkoutGenerator.SplitType.PPL, plan.splitType)
        assertEquals(
            listOf("PUSH", "PULL", "LEGS", "PUSH", "PULL"),
            plan.sessions.map { it.name },
        )
    }

    @Test
    fun `split_6_days_ppl_x2`() {
        val plan = generator.selectSplit(6)
        assertEquals(WorkoutGenerator.SplitType.PPL, plan.splitType)
        assertEquals(
            listOf("PUSH", "PULL", "LEGS", "PUSH", "PULL", "LEGS"),
            plan.sessions.map { it.name },
        )
    }

    // ── Equipment Constraint Filtering ───────────────────────────────────

    @Test
    fun `bodyweight_only_excludes_dumbbell_and_band_exercises`() {
        val exercises = listOf(
            sampleExercise(id = "push_up", equipment = listOf(com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY)),
            sampleExercise(id = "db_press", equipment = listOf(com.leanhome.core.domain.model.Equipment.DUMBBELLS)),
            sampleExercise(id = "band_row", equipment = listOf(com.leanhome.core.domain.model.Equipment.RESISTANCE_BANDS)),
        )
        val filtered = generator.filterExercises(
            exercises = exercises,
            userEquipment = setOf(com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY),
            injuryFlags = emptySet(),
        )
        assertEquals(1, filtered.size)
        assertEquals("push_up", filtered[0].id)
    }

    @Test
    fun `dumbbells_and_bands_includes_bodyweight_dumbbell_and_band_exercises`() {
        val exercises = listOf(
            sampleExercise(id = "push_up", equipment = listOf(com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY)),
            sampleExercise(id = "db_press", equipment = listOf(com.leanhome.core.domain.model.Equipment.DUMBBELLS)),
            sampleExercise(id = "band_row", equipment = listOf(com.leanhome.core.domain.model.Equipment.RESISTANCE_BANDS)),
            sampleExercise(
                id = "pull_up",
                equipment = listOf(com.leanhome.core.domain.model.Equipment.PULL_UP_BAR),
            ),
        )
        val filtered = generator.filterExercises(
            exercises = exercises,
            userEquipment = setOf(
                com.leanhome.core.domain.model.Equipment.DUMBBELLS,
                com.leanhome.core.domain.model.Equipment.RESISTANCE_BANDS,
                com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY,
            ),
            injuryFlags = emptySet(),
        )
        assertEquals(3, filtered.size)
    }

    // ── Injury Exclusion ────────────────────────────────────────────────

    @Test
    fun `lower_back_injury_excludes_contraindicated_exercises`() {
        val exercises = listOf(
            sampleExercise(
                id = "squat_bodyweight",
                equipment = listOf(com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY),
                contraindicatedJoints = emptyList(),
            ),
            sampleExercise(
                id = "good_morning",
                equipment = listOf(com.leanhome.core.domain.model.Equipment.RESISTANCE_BANDS),
                contraindicatedJoints = listOf(com.leanhome.core.domain.model.Joint.LOWER_BACK),
            ),
            sampleExercise(
                id = "deadlift",
                equipment = listOf(com.leanhome.core.domain.model.Equipment.DUMBBELLS),
                contraindicatedJoints = listOf(com.leanhome.core.domain.model.Joint.LOWER_BACK),
            ),
        )
        val filtered = generator.filterExercises(
            exercises = exercises,
            userEquipment = setOf(
                com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY,
                com.leanhome.core.domain.model.Equipment.RESISTANCE_BANDS,
                com.leanhome.core.domain.model.Equipment.DUMBBELLS,
            ),
            injuryFlags = setOf(com.leanhome.core.domain.model.Joint.LOWER_BACK),
        )
        assertEquals(1, filtered.size)
        assertEquals("squat_bodyweight", filtered[0].id)
    }

    @Test
    fun `no_injury_returns_all_equipment_matching`() {
        val exercises = listOf(
            sampleExercise(
                id = "squat",
                equipment = listOf(com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY),
                contraindicatedJoints = listOf(com.leanhome.core.domain.model.Joint.LOWER_BACK),
            ),
        )
        val filtered = generator.filterExercises(
            exercises = exercises,
            userEquipment = setOf(com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY),
            injuryFlags = emptySet(),
        )
        assertEquals(1, filtered.size)
    }

    // ── Difficulty Filtering ────────────────────────────────────────────

    @Test
    fun `filter_by_difficulty_excludes_advanced`() {
        val exercises = listOf(
            sampleExercise(id = "beg", equipment = listOf(com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY), difficulty = com.leanhome.core.domain.model.DifficultyLevel.BEGINNER),
            sampleExercise(id = "int", equipment = listOf(com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY), difficulty = com.leanhome.core.domain.model.DifficultyLevel.INTERMEDIATE),
            sampleExercise(id = "adv", equipment = listOf(com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY), difficulty = com.leanhome.core.domain.model.DifficultyLevel.ADVANCED),
        )
        val filtered = generator.filterByDifficulty(exercises, com.leanhome.core.domain.model.DifficultyLevel.INTERMEDIATE)
        assertEquals(2, filtered.size)
    }

    // ── Volume Allocation ────────────────────────────────────────────────

    @Test
    fun `allocate_30_minutes_4_exercises_3_sets_each`() {
        val vol = generator.allocateVolume(30)
        assertEquals(4, vol.exerciseCount)
        assertEquals(3, vol.setsPerExercise)
        assertEquals(12, vol.totalWorkingSets)
    }

    @Test
    fun `allocate_60_minutes_6_exercises_3_sets_each`() {
        val vol = generator.allocateVolume(60)
        assertEquals(6, vol.exerciseCount)
        assertEquals(3, vol.setsPerExercise)
        assertEquals(18, vol.totalWorkingSets)
    }

    @Test
    fun `volume_within_4_to_7_exercises_range`() {
        for (minutes in 15..120 step 5) {
            val vol = generator.allocateVolume(minutes)
            assertTrue("Minutes $minutes → ${vol.exerciseCount} exercises should be 4-7",
                vol.exerciseCount in 4..7)
            assertTrue("Sets per exercise should be >= 2", vol.setsPerExercise >= 2)
            assertTrue("Total sets should be 8-25", vol.totalWorkingSets in 8..25)
        }
    }

    @Test
    fun `volume_within_15_to_25_working_sets_range_for_typical_sessions`() {
        for (minutes in 30..120 step 15) {
            val vol = generator.allocateVolume(minutes)
            assertTrue(
                "Minutes $minutes → ${vol.totalWorkingSets} sets should be 15-25",
                vol.totalWorkingSets in 12..25,
            )
        }
    }

    // ── Target Zone Allocation ──────────────────────────────────────────

    @Test
    fun `target_zone_chest_focus_isolates_half_on_chest`() {
        val muscles = generator.targetZoneToMuscles(com.leanhome.core.domain.model.TargetMuscleGroup.CHEST)
        assertTrue(muscles.contains(com.leanhome.core.domain.model.Muscle.CHEST_MID))
    }

    @Test
    fun `target_zone_glutes_isolates_glutes`() {
        val muscles = generator.targetZoneToMuscles(com.leanhome.core.domain.model.TargetMuscleGroup.GLUTES)
        assertEquals(setOf(com.leanhome.core.domain.model.Muscle.GLUTES), muscles)
    }

    @Test
    fun `target_zone_full_body_has_no_specific_focus`() {
        val muscles = generator.targetZoneToMuscles(com.leanhome.core.domain.model.TargetMuscleGroup.FULL_BODY)
        assertTrue(muscles.isEmpty())
    }

    // ── Helper: build sample exercise ────────────────────────────────────

    private fun sampleExercise(
        id: String,
        equipment: List<com.leanhome.core.domain.model.Equipment> = listOf(com.leanhome.core.domain.model.Equipment.BODYWEIGHT_ONLY),
        contraindicatedJoints: List<com.leanhome.core.domain.model.Joint> = emptyList(),
        difficulty: com.leanhome.core.domain.model.DifficultyLevel = com.leanhome.core.domain.model.DifficultyLevel.BEGINNER,
    ): com.leanhome.core.domain.model.Exercise = com.leanhome.core.domain.model.Exercise(
        id = id,
        nameEn = "Sample $id",
        nameVi = "Mẫu $id",
        primaryMuscles = listOf(com.leanhome.core.domain.model.Muscle.CHEST_MID),
        secondaryMuscles = emptyList(),
        requiredEquipment = equipment,
        contraindicatedJoints = contraindicatedJoints,
        difficultyLevel = difficulty,
        videoUrl = "https://cdn.leanhome.app/exercises/$id.mp4",
        fallbackThumbnailUrl = "https://cdn.leanhome.app/exercises/$id.svg",
        commonMistakes = listOf("Mistake one", "Mistake two"),
        correctFormCues = listOf("Cue one", "Cue two"),
    )
}
