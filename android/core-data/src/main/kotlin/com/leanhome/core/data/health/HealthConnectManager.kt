package com.leanhome.core.data.health

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Read-only bridge to Google Health Connect.
 *
 * Provides step-count, active-energy, and weight reads over a trailing window.
 * The class is **read-only**: it exposes no insert/save methods and the manifest
 * requests only `READ_*` health permissions. The assignment asks for
 * `requestPermissions()`, `readStepCount(days)`, `readActiveEnergy(days)`, and
 * `readLatestWeight()`.
 *
 * The permission contract is surfaced to the host Activity / Fragment via
 * [permissionContract]; it owns the actual launcher. After the host receives
 * the granted-permissions callback, it forwards through `PermissionsGranted`
 * on the dashboard ViewModel — the manager then performs read-only queries.
 */
class HealthConnectManager(private val context: Context) {

    /**
     * The full read-permission set this manager needs. All entries are read
     * permissions — the manager never requests writes.
     */
    private val readPermissions: Set<String> = setOf(
        "androidx.health.connect.permission.READ_STEPS",
        "androidx.health.connect.permission.READ_ACTIVE_CALORIES_BURNED",
        "androidx.health.connect.permission.READ_WEIGHT",
    )

    /**
     * Frozen permission set exposed to the UI for an
     * `ActivityResultLauncher<Set<String>>` contract. The host registers a
     * `rememberLauncherForActivityResult(permissionContract)` and forwards
     * the granted set to the ViewModel.
     */
    val permissions: Set<String> get() = readPermissions

    /**
     * The Health Connect Activity-result contract that surfaces the system
     * permission dialog. The host Activity / Fragment registers it via
     * `rememberLauncherForActivityResult(permissionContract)` and launches
     * it with [permissions] to request read access.
     */
    val permissionContract: ActivityResultContract<Set<String>, Set<String>>
        get() = PermissionController.createRequestPermissionResultContract()

    /**
     * Whether the Health Connect APK is present and ready on this device.
     * Lower API devices or uninstalled Health Connect return false; the UI
     * uses this to hide the import row entirely.
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        HealthConnectClient.getSdkStatus(context) ==
            HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * True if every permission in [permissions] has been granted.
     */
    suspend fun hasAllPermissions(): Boolean = withContext(Dispatchers.IO) {
        val client = clientSafe() ?: return@withContext false
        val granted = try {
            client.permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            emptySet()
        }
        granted.containsAll(readPermissions)
    }

    /**
     * Opens system Health Connect settings so the user can re-grant after a
     * denial. Health Connect uses the system Settings surface rather than a
     * one-shot runtime dialog on some API levels.
     */
    fun openHealthConnectSettings(): Intent =
        Intent("android.health.connect.action.SHOW_MIGRATION_INFO").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    /**
     * Marker that the caller is requesting read permissions. The contract
     * must be launched by an Activity (the manager has no Activity of its
     * own); callers register [permissionContract] in
     * `rememberLauncherForActivityResult` and launch with [permissions].
     *
     * If Health Connect is unavailable, the method is a safe no-op.
     */
    suspend fun requestPermissions() {
        if (!isAvailable()) return
    }

    /**
     * Total step count over the trailing [days] calendar days, by ISO-8601
     * day boundaries in the local time zone.
     *
     * Returns 0 when the user has no data in the window (rather than null) so
     * the dashboard can render a daily average of 0 without an extra branch.
     */
    suspend fun readStepCount(days: Int): Long = withContext(Dispatchers.IO) {
        val client = clientSafe() ?: return@withContext 0L
        if (!hasGrantedReadPermission(client)) return@withContext 0L
        val (start, end) = window(days)
        val request = AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(start, end),
        )
        try {
            return@withContext client.aggregate(request)[StepsRecord.COUNT_TOTAL]
        } catch (_: Exception) {
            return@withContext 0L
        }
    }

    /**
     * Total active calories burned in kilocalories over the trailing [days]
     * days. Returns 0 when Health Connect is unavailable or denied.
     */
    suspend fun readActiveEnergy(days: Int): Double = withContext(Dispatchers.IO) {
        val client = clientSafe() ?: return@withContext 0.0
        if (!hasGrantedReadPermission(client)) return@withContext 0.0
        val (start, end) = window(days)
        val request = AggregateRequest(
            metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(start, end),
        )
        try {
            return@withContext client.aggregate(request)[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (_: Exception) {
            return@withContext 0.0
        }
    }

    /**
     * Most recent body-mass record in kilograms, or null when no weight has
     * been logged in Health Connect.
     */
    suspend fun readLatestWeight(): Double? = withContext(Dispatchers.IO) {
        val client = clientSafe() ?: return@withContext null
        if (!hasGrantedReadPermission(client)) return@withContext null
        val (start, end) = window(latestWeightLookbackDays)
        val request = ReadRecordsRequest(
            recordType = WeightRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end),
        )
        try {
            return@withContext client.readRecords(request).records
                .maxByOrNull { it.time }?.weight?.inKilograms
        } catch (_: Exception) {
            return@withContext null
        }
    }

    // -- internals -------------------------------------------------------

    /**
     * True if the caller has been granted every permission in
     * [readPermissions] for the supplied [client].
     */
    private suspend fun hasGrantedReadPermission(client: HealthConnectClient): Boolean {
        return try {
            client.permissionController.getGrantedPermissions().containsAll(readPermissions)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Non-null [HealthConnectClient] if and only if Health Connect is
     * available on this device. Otherwise null so the read methods can no-op.
     */
    private fun clientSafe(): HealthConnectClient? {
        if (HealthConnectClient.getSdkStatus(context) !=
            HealthConnectClient.SDK_AVAILABLE) return null
        return HealthConnectClient.getOrCreate(context)
    }

    /**
     * Local-day [start, end) window over the last [days] days. Truncated to
     * day boundaries so the dashboard's daily-average arithmetic is meaningful.
     */
    private fun window(days: Int): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val startDay = now.toLocalDate().minusDays(days.toLong() - 1L).atStartOfDay(zone)
        return startDay.toInstant() to now.toInstant()
    }

    private companion object {
        // Look back 12 months for the most recent weight reading; this covers
        // long gaps without pulling the entire historical record.
        const val latestWeightLookbackDays: Int = 365
    }
}
