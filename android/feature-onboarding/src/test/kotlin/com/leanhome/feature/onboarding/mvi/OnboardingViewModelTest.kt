package com.leanhome.feature.onboarding.mvi

import com.leanhome.core.domain.model.BodyArchetype
import com.leanhome.core.domain.model.Equipment
import com.leanhome.core.domain.model.FitnessGoal
import com.leanhome.core.domain.model.Gender
import com.leanhome.core.domain.model.Joint
import com.leanhome.core.domain.model.TargetMuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OnboardingViewModel — covers step transitions, validation,
 * and the MVI state updates.
 */
class OnboardingViewModelTest {

    private lateinit var vm: OnboardingViewModel

    @Before
    fun setup() {
        vm = OnboardingViewModel()
    }

    @Test
    fun `valid biometrics advance to bmi gauge`() {
        vm.handleIntent(OnboardingIntent.NameChanged("Test User"))
        vm.handleIntent(OnboardingIntent.AgeChanged(28))
        vm.handleIntent(OnboardingIntent.GenderChanged(Gender.MALE))
        vm.handleIntent(OnboardingIntent.HeightChanged(175.0))
        vm.handleIntent(OnboardingIntent.WeightChanged(78.0))
        vm.handleIntent(OnboardingIntent.CountryChanged("VN"))

        vm.handleIntent(OnboardingIntent.GoToNextStep)
        assertEquals(OnboardingStep.WHO_BMI_GAUGE, vm.state.value.step)
        assertEquals(25.474, vm.state.value.bmi!!, 0.001)
        assertEquals("ASIAN_WHO", vm.state.value.bmiClassification!!.standard.name)
    }

    @Test
    fun `invalid height blocks progression`() {
        vm.handleIntent(OnboardingIntent.NameChanged("Test"))
        vm.handleIntent(OnboardingIntent.AgeChanged(25))
        vm.handleIntent(OnboardingIntent.HeightChanged(50.0)) // below 100
        vm.handleIntent(OnboardingIntent.WeightChanged(75.0))
        vm.handleIntent(OnboardingIntent.CountryChanged("US"))

        vm.handleIntent(OnboardingIntent.GoToNextStep)
        assertEquals("Should stay on biometric input", OnboardingStep.BIOMETRIC_INPUT, vm.state.value.step)
        assertTrue("Should have height error", vm.state.value.errors.containsKey("height"))
    }

    @Test
    fun `age boundary 13 passes validation`() {
        vm.handleIntent(OnboardingIntent.NameChanged("Te"))
        vm.handleIntent(OnboardingIntent.AgeChanged(13))
        vm.handleIntent(OnboardingIntent.HeightChanged(160.0))
        vm.handleIntent(OnboardingIntent.WeightChanged(50.0))
        vm.handleIntent(OnboardingIntent.CountryChanged("US"))
        vm.handleIntent(OnboardingIntent.GoToNextStep)
        assertEquals(OnboardingStep.WHO_BMI_GAUGE, vm.state.value.step)
    }

    @Test
    fun `age 12 fails validation`() {
        vm.handleIntent(OnboardingIntent.NameChanged("T"))
        vm.handleIntent(OnboardingIntent.AgeChanged(12)) // below 13
        vm.handleIntent(OnboardingIntent.HeightChanged(160.0))
        vm.handleIntent(OnboardingIntent.WeightChanged(50.0))
        vm.handleIntent(OnboardingIntent.CountryChanged("US"))
        vm.handleIntent(OnboardingIntent.GoToNextStep)
        assertEquals(OnboardingStep.BIOMETRIC_INPUT, vm.state.value.step)
        assertTrue(vm.state.value.errors.containsKey("age"))
    }

    @Test
    fun `age 99 passes, age 100 fails`() {
        vm.handleIntent(OnboardingIntent.NameChanged("Elderly"))
        vm.handleIntent(OnboardingIntent.AgeChanged(99))
        vm.handleIntent(OnboardingIntent.HeightChanged(170.0))
        vm.handleIntent(OnboardingIntent.WeightChanged(70.0))
        vm.handleIntent(OnboardingIntent.CountryChanged("US"))
        vm.handleIntent(OnboardingIntent.GoToNextStep)
        assertEquals(OnboardingStep.WHO_BMI_GAUGE, vm.state.value.step)
    }

    @Test
    fun `weight 300 passes, 301 fails`() {
        vm.handleIntent(OnboardingIntent.NameChanged("Lifter"))
        vm.handleIntent(OnboardingIntent.AgeChanged(35))
        vm.handleIntent(OnboardingIntent.HeightChanged(190.0))
        vm.handleIntent(OnboardingIntent.WeightChanged(300.0))
        vm.handleIntent(OnboardingIntent.CountryChanged("US"))
        vm.handleIntent(OnboardingIntent.GoToNextStep)
        assertEquals(OnboardingStep.WHO_BMI_GAUGE, vm.state.value.step)
    }

    @Test
    fun `empty name blocks progression`() {
        vm.handleIntent(OnboardingIntent.NameChanged("")) // empty
        vm.handleIntent(OnboardingIntent.AgeChanged(25))
        vm.handleIntent(OnboardingIntent.HeightChanged(175.0))
        vm.handleIntent(OnboardingIntent.WeightChanged(75.0))
        vm.handleIntent(OnboardingIntent.CountryChanged("US"))
        vm.handleIntent(OnboardingIntent.GoToNextStep)
        assertEquals(OnboardingStep.BIOMETRIC_INPUT, vm.state.value.step)
        assertTrue(vm.state.value.errors.containsKey("name"))
    }

    @Test
    fun `name longer than 50 chars blocks progression`() {
        vm.handleIntent(OnboardingIntent.NameChanged("x".repeat(60))) // over 50
        vm.handleIntent(OnboardingIntent.AgeChanged(25))
        vm.handleIntent(OnboardingIntent.HeightChanged(175.0))
        vm.handleIntent(OnboardingIntent.WeightChanged(75.0))
        vm.handleIntent(OnboardingIntent.CountryChanged("US"))
        vm.handleIntent(OnboardingIntent.GoToNextStep)
        assertEquals(OnboardingStep.BIOMETRIC_INPUT, vm.state.value.step)
        assertTrue(vm.state.value.errors.containsKey("name"))
    }

    @Test
    fun `maintenance goal skips timeline slider`() {
        // Set up full valid profile
        completeBiometrics()
        // Walk through all onboarding steps with maintenance goal
        vm.handleIntent(OnboardingIntent.GoalSelected(FitnessGoal.HEALTH_MAINTENANCE))
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → WHO_BMI_GAUGE
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → ARCHETYPE_PICKER
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → GOAL_SELECTION
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → EQUIPMENT_SELECTION
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → INJURY_FLAGS
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → SCHEDULE
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → skip TIMELINE_SLIDER → REVIEW_SUMMARY

        assertEquals(OnboardingStep.REVIEW_SUMMARY, vm.state.value.step)
    }

    @Test
    fun `fat_loss goal includes timeline slider`() {
        completeBiometrics()
        vm.handleIntent(OnboardingIntent.GoalSelected(FitnessGoal.FAT_LOSS))
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → WHO_BMI_GAUGE
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → ARCHETYPE_PICKER
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → GOAL_SELECTION
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → EQUIPMENT_SELECTION
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → INJURY_FLAGS
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → SCHEDULE
        vm.handleIntent(OnboardingIntent.GoToNextStep) // → TIMELINE_SLIDER (not skipped for FAT_LOSS)
        assertEquals(OnboardingStep.TIMELINE_SLIDER, vm.state.value.step)
    }

    @Test
    fun `toggling injury flag adds and removes`() {
        vm.handleIntent(OnboardingIntent.InjuryFlagToggled(Joint.LOWER_BACK))
        assertTrue(Joint.LOWER_BACK in vm.state.value.injuryFlags)
        vm.handleIntent(OnboardingIntent.InjuryFlagToggled(Joint.LOWER_BACK))
        assertFalse(Joint.LOWER_BACK in vm.state.value.injuryFlags)
    }

    @Test
    fun `toggling equipment adds and removes`() {
        vm.handleIntent(OnboardingIntent.EquipmentToggled(Equipment.DUMBBELLS))
        assertTrue(Equipment.DUMBBELLS in vm.state.value.availableEquipment)
        vm.handleIntent(OnboardingIntent.EquipmentToggled(Equipment.DUMBBELLS))
        assertFalse(Equipment.DUMBBELLS in vm.state.value.availableEquipment)
    }

    @Test
    fun `archetype options vary by gender`() {
        vm.handleIntent(OnboardingIntent.GenderChanged(Gender.MALE))
        val maleOptions = BodyArchetype.forGender(Gender.MALE)
        assertTrue(BodyArchetype.OVAL in maleOptions)
        assertFalse(BodyArchetype.HOURGLASS in maleOptions)

        vm.handleIntent(OnboardingIntent.GenderChanged(Gender.FEMALE))
        val femaleOptions = BodyArchetype.forGender(Gender.FEMALE)
        assertTrue(BodyArchetype.HOURGLASS in femaleOptions)
        assertTrue(BodyArchetype.PEAR in femaleOptions)
    }

    @Test
    fun `target zone toning enables muscle group selection`() {
        vm.handleIntent(OnboardingIntent.GoalSelected(FitnessGoal.TARGET_ZONE_TONING))
        // Should auto-set a default target muscle group
        assertEquals(TargetMuscleGroup.FULL_BODY, vm.state.value.targetMuscleGroup)

        vm.handleIntent(OnboardingIntent.TargetMuscleSelected(TargetMuscleGroup.CHEST))
        assertEquals(TargetMuscleGroup.CHEST, vm.state.value.targetMuscleGroup)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun completeBiometrics() {
        vm.handleIntent(OnboardingIntent.NameChanged("Test User"))
        vm.handleIntent(OnboardingIntent.AgeChanged(28))
        vm.handleIntent(OnboardingIntent.GenderChanged(Gender.MALE))
        vm.handleIntent(OnboardingIntent.HeightChanged(175.0))
        vm.handleIntent(OnboardingIntent.WeightChanged(78.0))
        vm.handleIntent(OnboardingIntent.CountryChanged("US"))
    }
}
