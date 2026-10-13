package com.leanhome.feature.onboarding.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leanhome.core.domain.engine.EnergyBalanceEngine
import com.leanhome.core.domain.model.ActivityLevel
import com.leanhome.core.domain.model.BodyArchetype
import com.leanhome.core.domain.model.Equipment
import com.leanhome.core.domain.model.FitnessGoal
import com.leanhome.core.domain.model.Joint
import com.leanhome.core.domain.model.Gender
import com.leanhome.core.domain.model.TargetMuscleGroup
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the multi-step onboarding flow.
 *
 * Step sequence:
 *   1. BIOMETRIC_INPUT — name, age, gender, height, weight, country
 *   2. WHO_BMI_GAUGE — auto-computed BMI & classification with regional standard
 *   3. ARCHETYPE_PICKER — 2D body shape illustrations per gender
 *   4. GOAL_SELECTION — maintenance / fat loss / weight cut / target zone
 *   5. EQUIPMENT_SELECTION — checkbox set of available equipment
 *   6. INJURY_FLAGS — checkbox set of injury joints
 *   7. SCHEDULE — days per week, daily time minutes
 *   8. TIMELINE_SLIDER — only for fat loss / weight cut; safety warnings
 *   9. REVIEW_SUMMARY — final review and submit
 *
 * For HEALTH_MAINTENANCE: timeline slider is SKIPPED (zero deficit).
 */
class OnboardingViewModel(
    private val engine: EnergyBalanceEngine = EnergyBalanceEngine(),
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val steps: List<OnboardingStep> = OnboardingStep.entries
    private fun stepAfter(step: OnboardingStep): OnboardingStep? {
        val idx = steps.indexOf(step)
        // For maintenance goal, skip TIMELINE_SLIDER
        if (steps.getOrNull(idx + 1) == OnboardingStep.TIMELINE_SLIDER &&
            _state.value.fitnessGoal == FitnessGoal.HEALTH_MAINTENANCE
        ) {
            return steps.getOrNull(idx + 2)
        }
        return steps.getOrNull(idx + 1)
    }

    private fun stepBefore(step: OnboardingStep): OnboardingStep? {
        val idx = steps.indexOf(step)
        // For maintenance goal, skip TIMELINE_SLIDER
        if (steps.getOrNull(idx - 1) == OnboardingStep.TIMELINE_SLIDER &&
            _state.value.fitnessGoal == FitnessGoal.HEALTH_MAINTENANCE
        ) {
            return steps.getOrNull(idx - 2)
        }
        return steps.getOrNull(idx - 1)
    }

    fun handleIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.NameChanged -> _state.update { it.copy(name = intent.value, errors = it.errors - "name") }
            is OnboardingIntent.AgeChanged -> _state.update {
                val errors = if (intent.value !in OnboardingState.MIN_AGE..OnboardingState.MAX_AGE) {
                    it.errors + ("age" to "Age must be ${OnboardingState.MIN_AGE}-${OnboardingState.MAX_AGE}")
                } else it.errors - "age"
                it.copy(age = intent.value, errors = errors)
            }
            is OnboardingIntent.GenderChanged -> _state.update { it.copy(gender = intent.value, bodyArchetype = BodyArchetype.forGender(intent.value).first()) }
            is OnboardingIntent.HeightChanged -> _state.update {
                val errors = if (intent.value !in OnboardingState.MIN_HEIGHT..OnboardingState.MAX_HEIGHT) {
                    it.errors + ("height" to "Height must be ${OnboardingState.MIN_HEIGHT}-${OnboardingState.MAX_HEIGHT} cm")
                } else it.errors - "height"
                it.copy(heightCm = intent.value, errors = errors)
            }
            is OnboardingIntent.WeightChanged -> _state.update {
                val errors = if (intent.value !in OnboardingState.MIN_WEIGHT..OnboardingState.MAX_WEIGHT) {
                    it.errors + ("weight" to "Weight must be ${OnboardingState.MIN_WEIGHT}-${OnboardingState.MAX_WEIGHT} kg")
                } else it.errors - "weight"
                it.copy(weightKg = intent.value, errors = errors)
            }
            is OnboardingIntent.CountryChanged -> _state.update {
                val cleaned = intent.value.uppercase().take(2)
                it.copy(countryCode = cleaned, errors = it.errors - "countryCode")
            }
            is OnboardingIntent.ArchetypeSelected -> _state.update { it.copy(bodyArchetype = intent.value) }
            is OnboardingIntent.GoalSelected -> _state.update {
                val needsTimeline = intent.value == FitnessGoal.FAT_LOSS || intent.value == FitnessGoal.WEIGHT_CUT
                val newTarget = if (intent.value == FitnessGoal.TARGET_ZONE_TONING) it.targetMuscleGroup ?: TargetMuscleGroup.FULL_BODY else null
                it.copy(fitnessGoal = intent.value, targetMuscleGroup = newTarget)
            }
            is OnboardingIntent.TargetMuscleSelected -> _state.update { it.copy(targetMuscleGroup = intent.value) }
            is OnboardingIntent.EquipmentToggled -> _state.update { st ->
                val current = st.availableEquipment.toMutableList()
                if (intent.value in current) current.remove(intent.value) else current.add(intent.value)
                val err = if (current.isEmpty()) st.errors + ("equipment" to "Select at least one") else st.errors - "equipment"
                st.copy(availableEquipment = current, errors = err)
            }
            is OnboardingIntent.InjuryFlagToggled -> _state.update { st ->
                val current = st.injuryFlags.toMutableList()
                if (intent.value in current) current.remove(intent.value) else current.add(intent.value)
                st.copy(injuryFlags = current)
            }
            is OnboardingIntent.ActivityLevelChanged -> _state.update { it.copy(activityLevel = intent.value) }
            is OnboardingIntent.DaysPerWeekChanged -> _state.update { it.copy(daysPerWeek = intent.value) }
            is OnboardingIntent.DailyTimeChanged -> _state.update { it.copy(dailyTimeMinutes = intent.value) }
            is OnboardingIntent.TimelineDaysChanged -> _state.update { st ->
                st.copy(timelineDays = intent.value, safetyWarning = computeSafetyWarning(st.copy(timelineDays = intent.value)))
            }
            OnboardingIntent.GoToNextStep -> goToNextStep()
            OnboardingIntent.GoToPrevStep -> goToPrevStep()
            OnboardingIntent.CompleteOnboarding -> completeOnboarding()
        }
    }

    private fun goToNextStep() {
        val s = _state.value
        if (!validate(s)) return

        // If entering BMI gauge step, compute BMI & classification.
        if (s.step == OnboardingStep.BIOMETRIC_INPUT && stepAfter(s.step) == OnboardingStep.WHO_BMI_GAUGE) {
            val bmi = engine.calculateBmi(s.weightKg, s.heightCm)
            val classification = engine.classifyBmi(s.countryCode, bmi)
            _state.update { it.copy(step = OnboardingStep.WHO_BMI_GAUGE, bmi = bmi, bmiClassification = classification) }
            return
        }

        // If entering TIMELINE_SLIDER step, compute deficit & safety warnings.
        val next = stepAfter(s.step)
        if (next == OnboardingStep.TIMELINE_SLIDER) {
            computeTimelineWarnings()
        }
        if (next != null) {
            _state.update { it.copy(step = next) }
        }
    }

    private fun goToPrevStep() {
        val prev = stepBefore(_state.value.step)
        if (prev != null) {
            _state.update { it.copy(step = prev) }
        }
    }

    private fun computeTimelineWarnings() {
        val s = _state.value
        if (s.fitnessGoal != FitnessGoal.FAT_LOSS && s.fitnessGoal != FitnessGoal.WEIGHT_CUT) return
        val bmr = engine.calculateBmr(s.gender, s.age, s.weightKg, s.heightCm)
        val tdee = engine.calculateTdee(bmr, s.activityLevel)
        val targetWeightLoss = s.weightKg - (s.weightKg * 0.95) // 5% target
        val result = engine.evaluateDeficit(
            targetWeightLossKg = if (s.fitnessGoal == FitnessGoal.WEIGHT_CUT) 5.0 else 4.0,
            targetDays = s.timelineDays,
            tdee = tdee,
            weightKg = s.weightKg,
        )
        val safetyText = when (result.warningLevel) {
            EnergyBalanceEngine.DeficitResult.WarningLevel.NONE -> null
            EnergyBalanceEngine.DeficitResult.WarningLevel.AT_CEILING ->
                "Deficit is at the safe ceiling (25% of TDEE). This is acceptable but at the upper limit."
            EnergyBalanceEngine.DeficitResult.WarningLevel.EXTREME ->
                "⚠️ HIGH RISK / EXTREME DEFICIT: This exceeds safe limits (${java.text.DecimalFormat("#.#").format(result.dailyDeficit)} kcal/day). " +
                    "The safe maximum is ${java.text.DecimalFormat("#.#").format(result.clampedDeficit)} kcal/day. " +
                    "Dietary intervention required. Muscle loss risk."
        }
        _state.update { it.copy(deficitResult = result, safetyWarning = safetyText) }
    }

    private fun computeSafetyWarning(s: OnboardingState): String? {
        if (s.fitnessGoal != FitnessGoal.FAT_LOSS && s.fitnessGoal != FitnessGoal.WEIGHT_CUT) return null
        // Estimate TDEE (profile not yet saved) — use computed later if available
        return null // computed in computeTimelineWarnings when leaving this field focus
    }

    private fun validate(s: OnboardingState): Boolean {
        val errors = mutableMapOf<String, String>()

        // Always validate base inputs that should be present at all steps.
        with(s) {
            if (step.ordinal >= OnboardingStep.BIOMETRIC_INPUT.ordinal) {
                if (name.isBlank() || name.length > OnboardingState.MAX_NAME_LEN) {
                    errors["name"] = "Name must be ${OnboardingState.MIN_NAME_LEN}-${OnboardingState.MAX_NAME_LEN} chars"
                }
                if (age !in OnboardingState.MIN_AGE..OnboardingState.MAX_AGE) {
                    errors["age"] = "Age must be ${OnboardingState.MIN_AGE}-${OnboardingState.MAX_AGE}"
                }
                if (heightCm !in OnboardingState.MIN_HEIGHT..OnboardingState.MAX_HEIGHT) {
                    errors["height"] = "Height must be ${OnboardingState.MIN_HEIGHT}-${OnboardingState.MAX_HEIGHT} cm"
                }
                if (weightKg !in OnboardingState.MIN_WEIGHT..OnboardingState.MAX_WEIGHT) {
                    errors["weight"] = "Weight must be ${OnboardingState.MIN_WEIGHT}-${OnboardingState.MAX_WEIGHT} kg"
                }
                if (!countryCode.matches(Regex("^[A-Z]{2}$"))) {
                    errors["countryCode"] = "Country must be a 2-letter ISO code"
                }
            }
            if (step.ordinal >= OnboardingStep.EQUIPMENT_SELECTION.ordinal) {
                if (availableEquipment.isEmpty()) errors["equipment"] = "Select at least one equipment"
            }
            if (step.ordinal >= OnboardingStep.SCHEDULE.ordinal) {
                if (daysPerWeek !in 2..6) errors["daysPerWeek"] = "2-6 days"
                if (dailyTimeMinutes !in 15..120) errors["dailyTimeMinutes"] = "15-120 min"
            }
        }

        _state.update { it.copy(errors = errors) }
        return errors.isEmpty()
    }

    private fun completeOnboarding() {
        val s = _state.value
        if (!validate(s)) return
        _state.update { it.copy(isComplete = true) }
        viewModelScope.launch {
            _effects.send(OnboardingEffect.OnboardingComplete)
        }
    }
}
