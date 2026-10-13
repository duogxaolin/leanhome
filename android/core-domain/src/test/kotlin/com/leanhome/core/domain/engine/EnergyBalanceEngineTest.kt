package com.leanhome.core.domain.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Parameterized tests for [EnergyBalanceEngine] loading ALL 31 canonical
 * mathematical test vectors from math_engine_test_vectors.json.
 *
 * Every test vector MUST pass identically to the Swift (iOS) implementation.
 */
class EnergyBalanceEngineTest {

    private lateinit var engine: EnergyBalanceEngine

    @Before
    fun setup() {
        engine = EnergyBalanceEngine()
    }

    // ── Test vector JSON models ──────────────────────────────────────────

    @Serializable
    data class TestVectors(
        @SerialName("bmr_tdee_tests") val bmrTdeeTests: List<BmrTdeeTest>,
        @SerialName("deficit_calculation_tests") val deficitCalculationTests: List<DeficitCalcTest>,
        @SerialName("safety_threshold_tests") val safetyThresholdTests: List<SafetyThresholdTest>,
        @SerialName("bmi_classification_tests") val bmiClassificationTests: List<BmiClassificationTest>,
        @SerialName("maintenance_goal_tests") val maintenanceGoalTests: List<MaintenanceGoalTest>,
        @SerialName("split_selection_tests") val splitSelectionTests: List<SplitSelectionTest>,
    )

    @Serializable
    data class BmrTdeeInput(
        @SerialName("gender") val gender: String,
        @SerialName("age") val age: Int,
        @SerialName("weight_kg") val weightKg: Double,
        @SerialName("height_cm") val heightCm: Double,
        @SerialName("activity_level") val activityLevel: String,
    )

    @Serializable
    data class BmrTdeeTest(
        @SerialName("id") val id: String,
        @SerialName("input") val input: BmrTdeeInput,
        @SerialName("expected") val expected: BmrTdeeExpected,
    )

    @Serializable
    data class BmrTdeeExpected(
        @SerialName("bmr") val bmr: Double,
        @SerialName("tdee") val tdee: Double,
    )

    @Serializable
    data class DeficitCalcInput(
        @SerialName("target_weight_loss_kg") val targetWeightLossKg: Double,
        @SerialName("target_days") val targetDays: Int,
        @SerialName("tdee") val tdee: Double? = null,
        @SerialName("weight_kg") val weightKg: Double? = null,
    )

    @Serializable
    data class DeficitCalcTest(
        @SerialName("id") val id: String,
        @SerialName("input") val input: DeficitCalcInput,
        @SerialName("expected") val expected: DeficitCalcExpected,
    )

    @Serializable
    data class DeficitCalcExpected(
        @SerialName("daily_deficit") val dailyDeficit: Double,
        @SerialName("is_safe") val isSafe: Boolean,
        @SerialName("warning_level") val warningLevel: String,
        @SerialName("clamped_deficit") val clampedDeficit: Double? = null,
        @SerialName("requires_dietary_intervention") val requiresDietaryIntervention: Boolean? = null,
    )

    @Serializable
    data class SafetyThresholdInput(
        @SerialName("weight_kg") val weightKg: Double? = null,
        @SerialName("gender") val gender: String? = null,
        @SerialName("tdee") val tdee: Double? = null,
    )

    @Serializable
    data class SafetyThresholdTest(
        @SerialName("id") val id: String,
        @SerialName("input") val input: SafetyThresholdInput,
        @SerialName("expected") val expected: SafetyThresholdExpected,
    )

    @Serializable
    data class SafetyThresholdExpected(
        @SerialName("max_weekly_loss_kg") val maxWeeklyLossKg: Double? = null,
        @SerialName("min_caloric_intake") val minCaloricIntake: Double? = null,
        @SerialName("max_safe_deficit") val maxSafeDeficit: Double? = null,
    )

    @Serializable
    data class BmiClassificationInput(
        @SerialName("country_code") val countryCode: String? = null,
        @SerialName("bmi") val bmi: Double? = null,
        @SerialName("weight_kg") val weightKg: Double? = null,
        @SerialName("height_cm") val heightCm: Double? = null,
    )

    @Serializable
    data class BmiClassificationTest(
        @SerialName("id") val id: String,
        @SerialName("input") val input: BmiClassificationInput,
        @SerialName("expected") val expected: BmiClassificationExpected,
    )

    @Serializable
    data class BmiClassificationExpected(
        @SerialName("classification") val classification: String? = null,
        @SerialName("standard") val standard: String? = null,
        @SerialName("label") val label: String? = null,
        @SerialName("bmi") val bmi: Double? = null,
    )

    @Serializable
    data class MaintenanceGoalInput(
        @SerialName("fitness_goal") val fitnessGoal: String,
        @SerialName("tdee") val tdee: Double,
    )

    @Serializable
    data class MaintenanceGoalTest(
        @SerialName("id") val id: String,
        @SerialName("input") val input: MaintenanceGoalInput,
        @SerialName("expected") val expected: MaintenanceGoalExpected,
    )

    @Serializable
    data class MaintenanceGoalExpected(
        @SerialName("daily_deficit") val dailyDeficit: Double,
        @SerialName("caloric_target") val caloricTarget: Double,
        @SerialName("skip_timeline") val skipTimeline: Boolean,
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

    // ── Test vector loading ──────────────────────────────────────────────

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

    // ── BMR/TDEE tests (6 vectors) ────────────────────────────────────────

    @Test
    fun `bmr_tdee_all_vectors_pass`() {
        val vectors = loadVectors()
        assertEquals("Expected 6 BMR/TDEE tests", 6, vectors.bmrTdeeTests.size)

        vectors.bmrTdeeTests.forEach { test ->
            val gender = com.leanhome.core.domain.model.Gender.fromString(test.input.gender)
            val activity = com.leanhome.core.domain.model.ActivityLevel.fromString(test.input.activityLevel)
            val bmr = engine.calculateBmr(gender, test.input.age, test.input.weightKg, test.input.heightCm)
            val tdee = engine.calculateTdee(bmr, activity)

            assertApproxEquals(
                message = "[${test.id}] BMR mismatch",
                expected = test.expected.bmr,
                actual = bmr,
                tolerance = 0.01,
            )
            assertApproxEquals(
                message = "[${test.id}] TDEE mismatch",
                expected = test.expected.tdee,
                actual = tdee,
                tolerance = 0.01,
            )
        }
    }

    @Test
    fun `bmr_male_25_sedentary_individual`() {
        val bmr = engine.calculateBmr(com.leanhome.core.domain.model.Gender.MALE, 25, 75.0, 175.0)
        assertApproxEquals("[bmr_male_25_sedentary] BMR", 1723.75, bmr, 0.01)
        val tdee = engine.calculateTdee(bmr, com.leanhome.core.domain.model.ActivityLevel.SEDENTARY)
        assertApproxEquals("[bmr_male_25_sedentary] TDEE", 2068.5, tdee, 0.01)
    }

    @Test
    fun `bmr_female_13_min_age_boundary`() {
        val bmr = engine.calculateBmr(com.leanhome.core.domain.model.Gender.FEMALE, 13, 50.0, 160.0)
        assertApproxEquals("[bmr_female_13_moderate] BMR", 1274.0, bmr, 0.01)
        val tdee = engine.calculateTdee(bmr, com.leanhome.core.domain.model.ActivityLevel.MODERATELY_ACTIVE)
        assertApproxEquals("[bmr_female_13_moderate] TDEE", 1974.7, tdee, 0.01)
    }

    @Test
    fun `bmr_male_99_max_age_boundary`() {
        val bmr = engine.calculateBmr(com.leanhome.core.domain.model.Gender.MALE, 99, 70.0, 170.0)
        assertApproxEquals("[bmr_male_99_sedentary] BMR", 1272.5, bmr, 0.01)
    }

    @Test
    fun `bmr_other_gender_uses_male_formula`() {
        val bmr = engine.calculateBmr(com.leanhome.core.domain.model.Gender.OTHER, 30, 70.0, 175.0)
        assertApproxEquals("[bmr_other_gender_male_formula] BMR", 1648.75, bmr, 0.01)
    }

    // ── Deficit calculation tests (5 vectors) ─────────────────────────────

    @Test
    fun `deficit_calculation_all_vectors_pass`() {
        val vectors = loadVectors()
        assertEquals("Expected 5 deficit calculation tests", 5, vectors.deficitCalculationTests.size)

        vectors.deficitCalculationTests.forEach { test ->
            val weightKg = test.input.weightKg ?: 75.0
            val result = engine.evaluateDeficit(
                targetWeightLossKg = test.input.targetWeightLossKg,
                targetDays = test.input.targetDays,
                tdee = test.input.tdee,
                weightKg = weightKg,
            )

            assertApproxEquals(
                message = "[${test.id}] daily deficit mismatch",
                expected = test.expected.dailyDeficit,
                actual = result.dailyDeficit,
                tolerance = 0.01,
            )
            assertEquals(
                "[${test.id}] is_safe mismatch",
                test.expected.isSafe,
                result.isSafe,
            )
            assertEquals(
                "[${test.id}] warning_level mismatch (expected ${test.expected.warningLevel}, got ${result.warningLevel})",
                test.expected.warningLevel,
                result.warningLevel.name,
            )
            if (test.expected.clampedDeficit != null) {
                assertNotNull(
                    "[${test.id}] expected clamped_deficit but got null",
                    result.clampedDeficit,
                )
                assertApproxEquals(
                    message = "[${test.id}] clamped_deficit mismatch",
                    expected = test.expected.clampedDeficit,
                    actual = result.clampedDeficit!!,
                    tolerance = 0.01,
                )
            }
            if (test.expected.requiresDietaryIntervention != null) {
                assertEquals(
                    "[${test.id}] requires_dietary_intervention mismatch",
                    test.expected.requiresDietaryIntervention,
                    result.requiresDietaryIntervention,
                )
            }
        }
    }

    @Test
    fun `deficit_normal_60day`() {
        val deficit = engine.calculateDailyDeficit(5.0, 60)
        assertApproxEquals("[deficit_normal_60day]", 641.667, deficit, 0.01)
    }

    @Test
    fun `deficit_extreme_30day_triggers_warning`() {
        val result = engine.evaluateDeficit(5.0, 30, 2068.5, 75.0)
        assertFalse("Should be unsafe", result.isSafe)
        assertEquals("Should be EXTREME", EnergyBalanceEngine.DeficitResult.WarningLevel.EXTREME, result.warningLevel)
        assertNotNull("Should have clamped deficit", result.clampedDeficit)
        assertTrue("Should require dietary intervention", result.requiresDietaryIntervention)
        assertApproxEquals("Clamped deficit", 517.125, result.clampedDeficit!!, 0.01)
    }

    @Test
    fun `deficit_1000kcal_hard_cap_applies`() {
        val result = engine.evaluateDeficit(10.0, 50, 5000.0, 120.0)
        assertFalse("Should be unsafe (exceeds 1000 cap)", result.isSafe)
        assertApproxEquals("Clamped to 1000", 1000.0, result.clampedDeficit!!, 0.01)
    }

    // ── Safety threshold tests (6 vectors) ───────────────────────────────

    @Test
    fun `safety_thresholds_all_vectors_pass`() {
        val vectors = loadVectors()
        assertEquals("Expected 6 safety threshold tests", 6, vectors.safetyThresholdTests.size)

        vectors.safetyThresholdTests.forEach { test ->
            test.expected.maxWeeklyLossKg?.let { expected ->
                val weightKg = test.input.weightKg ?: 75.0
                val actual = engine.maxWeeklyWeightLossKg(weightKg)
                assertApproxEquals("[${test.id}] max_weekly_loss", expected, actual, 0.001)
            }

            test.expected.minCaloricIntake?.let { expected ->
                val gender = test.input.gender?.let {
                    com.leanhome.core.domain.model.Gender.fromString(it)
                } ?: com.leanhome.core.domain.model.Gender.MALE
                val actual = engine.minCaloricIntake(gender)
                assertApproxEquals("[${test.id}] min_caloric_intake", expected, actual, 0.001)
            }

            test.expected.maxSafeDeficit?.let { expected ->
                val tdee = test.input.tdee ?: 1500.0
                val actual = engine.maxSafeDailyDeficit(tdee)
                assertApproxEquals("[${test.id}] max_safe_deficit", expected, actual, 0.001)
            }
        }
    }

    @Test
    fun `max_weekly_loss_75kg_is_0_75`() {
        assertApproxEquals("75kg → 0.75 kg/wk", 0.75, engine.maxWeeklyWeightLossKg(75.0), 0.001)
    }

    @Test
    fun `min_intake_male_1500`() {
        assertApproxEquals("Male", 1500.0,
            engine.minCaloricIntake(com.leanhome.core.domain.model.Gender.MALE), 0.001)
    }

    @Test
    fun `min_intake_female_1200`() {
        assertApproxEquals("Female", 1200.0,
            engine.minCaloricIntake(com.leanhome.core.domain.model.Gender.FEMALE), 0.001)
    }

    @Test
    fun `safety_cap_small_tdee_25pct_binding`() {
        assertApproxEquals("Small TDEE 1500 → 375", 375.0, engine.maxSafeDailyDeficit(1500.0), 0.001)
    }

    @Test
    fun `safety_cap_large_tdee_1000_hard_cap`() {
        assertApproxEquals("Large TDEE 5000 → 1000", 1000.0, engine.maxSafeDailyDeficit(5000.0), 0.001)
    }

    // ── BMI classification tests (8 vectors) ─────────────────────────────

    @Test
    fun `bmi_classification_all_vectors_pass`() {
        val vectors = loadVectors()
        assertEquals("Expected 8 BMI classification tests", 8, vectors.bmiClassificationTests.size)

        vectors.bmiClassificationTests.forEach { test ->
            // Pure BMI computation test case
            if (test.input.bmi == null && test.input.weightKg != null && test.input.heightCm != null) {
                val bmi = engine.calculateBmi(test.input.weightKg, test.input.heightCm)
                assertApproxEquals("[${test.id}] bmi", test.expected.bmi!!, bmi, 0.001)
                return@forEach
            }

            val countryCode = test.input.countryCode ?: "US"
            val bmi = test.input.bmi ?: run {
                val w = test.input.weightKg ?: 78.0
                val h = test.input.heightCm ?: 175.0
                engine.calculateBmi(w, h)
            }

            val result = engine.classifyBmi(countryCode, bmi)
            test.expected.classification?.let {
                assertEquals("[${test.id}] classification", it, result.classification.name)
            }
            test.expected.standard?.let {
                assertEquals("[${test.id}] standard", it, result.standard.name)
            }
            test.expected.label?.let {
                assertEquals("[${test.id}] label", it, result.label)
            }
        }
    }

    @Test
    fun `bmi_asian_overweight_classification`() {
        val result = engine.classifyBmi("VN", 24.0)
        assertEquals("OVERWEIGHT", result.classification.name)
        assertEquals("ASIAN_WHO", result.standard.name)
        assertEquals("Overweight / Pre-obese (Asian WHO Standard)", result.label)
    }

    @Test
    fun `bmi_international_normal_classification`() {
        val result = engine.classifyBmi("US", 24.0)
        assertEquals("NORMAL", result.classification.name)
        assertEquals("INTERNATIONAL_WHO", result.standard.name)
        assertEquals("Normal weight (International WHO Standard)", result.label)
    }

    @Test
    fun `bmi_computation_from_height_weight`() {
        val bmi = engine.calculateBmi(78.0, 175.0)
        assertApproxEquals("BMI 78kg/175cm → 25.474", 25.474, bmi, 0.001)
    }

    @Test
    fun `bmi_asian_obese_KR_26`() {
        val result = engine.classifyBmi("KR", 26.0)
        assertEquals("OBESE", result.classification.name)
        assertEquals("ASIAN_WHO", result.standard.name)
    }

    @Test
    fun `bmi_underweight_TH_17`() {
        val result = engine.classifyBmi("TH", 17.0)
        assertEquals("UNDERWEIGHT", result.classification.name)
    }

    @Test
    fun `bmi_international_obese_AU_31`() {
        val result = engine.classifyBmi("AU", 31.0)
        assertEquals("OBESE", result.classification.name)
        assertEquals("INTERNATIONAL_WHO", result.standard.name)
    }

    // ── Maintenance goal tests (1 vector) ────────────────────────────────

    @Test
    fun `maintenance_goal_all_vectors_pass`() {
        val vectors = loadVectors()
        assertEquals("Expected 1 maintenance goal test", 1, vectors.maintenanceGoalTests.size)

        vectors.maintenanceGoalTests.forEach { test ->
            val result = engine.calculateMaintenance(test.input.tdee)

            assertEquals("[${test.id}] daily_deficit", test.expected.dailyDeficit, result.dailyDeficit, 0.001)
            assertApproxEquals("[${test.id}] caloric_target", test.expected.caloricTarget, result.caloricTarget, 0.001)
            assertEquals("[${test.id}] skip_timeline", test.expected.skipTimeline, result.skipTimeline)
        }
    }

    @Test
    fun `maintenance_zero_deficit_caloric_target_tdee`() {
        val result = engine.calculateMaintenance(2068.5)
        assertEquals("Zero deficit", 0.0, result.dailyDeficit, 0.001)
        assertApproxEquals("Target = TDEE", 2068.5, result.caloricTarget, 0.001)
        assertTrue("Should skip timeline", result.skipTimeline)
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private fun assertApproxEquals(message: String, expected: Double, actual: Double, tolerance: Double) {
        val diff = abs(expected - actual)
        assertTrue("$message — expected $expected but got $actual (diff=$diff, tolerance=$tolerance)", diff <= tolerance)
    }
}
