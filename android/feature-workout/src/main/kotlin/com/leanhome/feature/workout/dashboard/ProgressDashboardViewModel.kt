package com.leanhome.feature.workout.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leanhome.core.domain.model.WeightLog
import com.leanhome.core.domain.repository.WeightLogRepository
import com.leanhome.core.domain.usecase.DashboardSnapshot
import com.leanhome.core.domain.usecase.GetProgressDashboardUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * MVI state for the progress dashboard.
 */
data class ProgressDashboardState(
    val snapshot: DashboardSnapshot? = null,
    val isLoading: Boolean = false,
    val weighInInput: String = "",
    val weighInPromptVisible: Boolean = false,
    val message: String? = null,
)

/**
 * Intent set for the progress dashboard.
 */
sealed class ProgressDashboardIntent {
    object Refresh : ProgressDashboardIntent()
    object ShowWeighInPrompt : ProgressDashboardIntent()
    object HideWeighInPrompt : ProgressDashboardIntent()
    data class WeighInInputChanged(val value: String) : ProgressDashboardIntent()
    object SubmitWeighIn : ProgressDashboardIntent()
}

/**
 * ViewModel backing [ProgressDashboardScreen].
 *
 * Loads the [DashboardSnapshot] via [GetProgressDashboardUseCase] and exposes
 * a weigh-in flow that records new [WeightLog] entries through
 * [WeightLogRepository] before refreshing the snapshot.
 */
class ProgressDashboardViewModel(
    private val userId: String,
    private val getDashboard: GetProgressDashboardUseCase,
    private val weightLogRepository: WeightLogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressDashboardState())
    val state: StateFlow<ProgressDashboardState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun handleIntent(intent: ProgressDashboardIntent) {
        when (intent) {
            is ProgressDashboardIntent.Refresh -> refresh()
            is ProgressDashboardIntent.ShowWeighInPrompt -> _state.update {
                it.copy(weighInPromptVisible = true)
            }
            is ProgressDashboardIntent.HideWeighInPrompt -> _state.update {
                it.copy(weighInPromptVisible = false, weighInInput = "")
            }
            is ProgressDashboardIntent.WeighInInputChanged -> _state.update {
                it.copy(weighInInput = intent.value)
            }
            is ProgressDashboardIntent.SubmitWeighIn -> submitWeighIn()
        }
    }

    private fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val snapshot = getDashboard()
            val latest = weightLogRepository.getLatestLog(userId)
            val promptDue = latest == null || isDue(latest.recordedAt)
            _state.update {
                it.copy(
                    snapshot = snapshot,
                    isLoading = false,
                    weighInPromptVisible = promptDue && !it.weighInPromptVisible,
                    message = if (snapshot.plateauAlert) PLATEAU_MESSAGE else null,
                )
            }
        }
    }

    private fun submitWeighIn() {
        val parsed = _state.value.weighInInput.trim().toDoubleOrNull()
        if (parsed == null || parsed <= 0.0) {
            _state.update { it.copy(message = "Enter a valid weight in kg.") }
            return
        }
        viewModelScope.launch {
            weightLogRepository.insert(
                WeightLog(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    weightKg = parsed,
                    recordedAt = System.currentTimeMillis(),
                )
            )
            _state.update { it.copy(weighInPromptVisible = false, weighInInput = "") }
            refresh()
        }
    }

    /**
     * A weigh-in is due when 7+ days have elapsed since the last log.
     */
    private fun isDue(recordedAt: Long): Boolean {
        val elapsed = System.currentTimeMillis() - recordedAt
        return elapsed >= SEVEN_DAYS_MILLIS
    }

    private companion object {
        const val SEVEN_DAYS_MILLIS = 7L * 86_400_000L
        const val PLATEAU_MESSAGE =
            "Weight has stalled for 2 weeks despite 85%+ adherence. " +
                "Consider recalculating calories or activity."
    }
}
