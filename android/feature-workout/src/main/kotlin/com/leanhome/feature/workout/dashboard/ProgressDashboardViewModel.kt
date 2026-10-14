package com.leanhome.feature.workout.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leanhome.core.data.health.HealthConnectManager
import com.leanhome.core.domain.model.ActivityLevel
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
    /** Health import UX state (idle / requesting / success / denied / unavailable). */
    val healthImport: HealthImportState = HealthImportState.Idle,
) {
    /** True when the device has the Health Connect APK installed and reachable. */
    val isHealthAvailable: Boolean
        get() = healthImport !is HealthImportState.Unavailable
}

/**
 * Health import UX/UI state machine — surfaced from [ProgressDashboardViewModel].
 *
 * Sealed so the Compose layer can exhaustively switch and render distinct UI
 * (button, spinner, summary card, denial fallback) without boolean flags.
 */
sealed class HealthImportState {
    /** No import attempted yet this session. */
    object Idle : HealthImportState()
    /** Permission request or data read in progress. */
    object Loading : HealthImportState()
    /** Permission granted, data retrieved — includes the daily step average. */
    data class Success(
        val dailyAverageSteps: Long,
        val totalSteps: Long,
        val totalActiveEnergyKcal: Double,
        /** Activity level estimated from the step average for TDEE hint. */
        val estimatedActivityLevel: ActivityLevel,
        val windowDays: Int,
    ) : HealthImportState()
    /** User denied Health permission (or revoked it after grant). */
    object Denied : HealthImportState()
    /** Health Connect is not available on this device. */
    object Unavailable : HealthImportState()
}

/**
 * Intent set for the progress dashboard.
 */
sealed class ProgressDashboardIntent {
    object Refresh : ProgressDashboardIntent()
    object ShowWeighInPrompt : ProgressDashboardIntent()
    object HideWeighInPrompt : ProgressDashboardIntent()
    data class WeighInInputChanged(val value: String) : ProgressDashboardIntent()
    object SubmitWeighIn : ProgressDashboardIntent()

    /**
     * User tapped 'Import from Health'. The screen hosts the permission
     * launcher (via [ProgressDashboardViewModel.healthConnectManager]); if
     * permission is already granted, this intent performs reads immediately.
     * If not granted, the screen launches the contract and the result is
     * routed back through [PermissionsGranted].
     */
    object ImportFromHealth : ProgressDashboardIntent()

    /**
     * Permission contract callback finished — the host now expects the
     * ViewModel to perform read-only Health Connect queries.
     */
    object PermissionsGranted : ProgressDashboardIntent()

    /** User tapped Retry after denial — re-runs the availability + permission flow. */
    object RetryHealthImport : ProgressDashboardIntent()
}

/**
 * ViewModel backing [ProgressDashboardScreen].
 *
 * Loads the [DashboardSnapshot] via [GetProgressDashboardUseCase] and exposes
 * a weigh-in flow that records new [WeightLog] entries through
 * [WeightLogRepository] before refreshing the snapshot. Also exposes a
 * read-only Health Connect import ([ImportFromHealth]) that retrieves the
 * trailing 7 days of step + active-energy data and estimates the user's
 * [ActivityLevel] for TDEE recalibration. The import flow never writes back
 * to Health Connect.
 */
class ProgressDashboardViewModel(
    private val userId: String,
    private val getDashboard: GetProgressDashboardUseCase,
    private val weightLogRepository: WeightLogRepository,
    private val healthConnect: HealthConnectManager? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressDashboardState())
    val state: StateFlow<ProgressDashboardState> = _state.asStateFlow()

    /**
     * Read-only Health Connect manager — exposed so the screen can register
     * `rememberLauncherForActivityResult(manager.permissionContract)` and
     * launch it before reads happen.
     */
    val healthConnectManager: HealthConnectManager? get() = healthConnect

    init {
        refresh()
        checkHealthAvailability()
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
            is ProgressDashboardIntent.ImportFromHealth -> handleHealthIntent()
            is ProgressDashboardIntent.PermissionsGranted -> importFromHealth()
            is ProgressDashboardIntent.RetryHealthImport -> importFromHealth()
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

    private fun handleHealthIntent() {
        val manager = healthConnect
        if (manager == null) {
            _state.update { it.copy(healthImport = HealthImportState.Unavailable) }
            return
        }
        if (_state.value.healthImport is HealthImportState.Unavailable) return
        // ImportFromHealth arrives only after the screen-side [startHealthImport]
        // confirmed the permission set is already granted (or returned via the
        // [PermissionsGranted] contract callback). Proceed straight to reads.
        importFromHealth()
    }

    /**
     * Read-only query flow: assumes permission has been granted (either
     * pre-checked via [handleHealthIntent] or returned from the screen's
     * permission launcher as [PermissionsGranted]). Reads steps + active
     * energy over the trailing window and surfaces the result via
     * [HealthImportState]. No data is ever written back to Health Connect.
     */
    private fun importFromHealth() {
        val manager = healthConnect ?: run {
            _state.update { it.copy(healthImport = HealthImportState.Unavailable) }
            return
        }
        _state.update { it.copy(healthImport = HealthImportState.Loading) }
        viewModelScope.launch {
            // Health Connect is unavailable on lower API levels or uninstalled APK.
            if (!manager.isAvailable()) {
                _state.update { it.copy(healthImport = HealthImportState.Unavailable) }
                return@launch
            }
            // Mark that the manager's read-only permission request was initiated;
            // the actual contract launch happened earlier from the screen launcher.
            manager.requestPermissions()
            if (!manager.hasAllPermissions()) {
                _state.update { it.copy(healthImport = HealthImportState.Denied) }
                return@launch
            }
            // Read-only: never writes back to Health Connect.
            val days = HEALTH_IMPORT_WINDOW_DAYS
            val totalSteps = runCatching { manager.readStepCount(days) }.getOrDefault(0L)
            val totalEnergyKcal = runCatching { manager.readActiveEnergy(days) }.getOrDefault(0.0)
            val dailyAverageSteps = if (days > 0) totalSteps / days else 0L
            _state.update {
                it.copy(
                    healthImport = HealthImportState.Success(
                        dailyAverageSteps = dailyAverageSteps,
                        totalSteps = totalSteps,
                        totalActiveEnergyKcal = totalEnergyKcal,
                        estimatedActivityLevel = estimateActivityLevel(dailyAverageSteps),
                        windowDays = days,
                    ),
                )
            }
        }
    }

    /**
     * Maps the trailing average step count to a coarse [ActivityLevel]. Used as
     * a hint for TDEE recalibration; the profile's actual TDEE is computed
     * separately by the energy engine.
     */
    private fun estimateActivityLevel(dailyAverageSteps: Long): ActivityLevel = when {
        dailyAverageSteps < 5_000 -> ActivityLevel.SEDENTARY
        dailyAverageSteps < 7_500 -> ActivityLevel.LIGHTLY_ACTIVE
        dailyAverageSteps < 10_000 -> ActivityLevel.MODERATELY_ACTIVE
        else -> ActivityLevel.VERY_ACTIVE
    }

    /**
     * Probes the platform on init so the dashboard can render the import row
     * only when Health Connect is actually reachable.
     */
    private fun checkHealthAvailability() {
        val manager = healthConnect ?: run {
            _state.update { it.copy(healthImport = HealthImportState.Unavailable) }
            return
        }
        viewModelScope.launch {
            val available = runCatching { manager.isAvailable() }.getOrDefault(false)
            if (!available) {
                _state.update { it.copy(healthImport = HealthImportState.Unavailable) }
            }
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
        const val HEALTH_IMPORT_WINDOW_DAYS = 7
    }
}
