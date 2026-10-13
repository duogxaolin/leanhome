package com.leanhome.core.data.worker

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaException
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.leanhome.core.data.cache.VideoCacheManager
import com.leanhome.core.data.cache.VideoCacheManager.Companion.cacheKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Background pre-caching worker for upcoming-workout media assets.
 *
 * Driven by a [PrefetchManifest] (see `shared/media/prefetch_manifest_schema.json`)
 * produced by the WorkoutGenerator. It downloads every listed asset into the
 * [VideoCacheManager] LRU disk cache over Wi-Fi only — the work request is
 * constrained to [NetworkType.UNMETERED] via [enqueue], so WorkManager defers
 * execution until the device is on a non-metered network (the hard
 * `network_constraint = "UNMETERED"` rule from the manifest schema).
 *
 * Behaviour:
 * - Prefers video assets before SVG (the worker processes VIDEO before SVG so
 *   the heavier, gameplay-critical mp4 is fetched first).
 * - Skips assets already in the cache (idempotent re-runs via [ExistingWorkPolicy.KEEP]).
 * - Surfaces a low-priority notification while running so the OS allows a
 *   long-running download under Android's foreground-service enforcement.
 * - Bounded retries via [runAttemptCount] (capped at [MAX_RETRY_ATTEMPTS]) and
 *   surfaces per-asset progress to WorkManager observers through [setProgress].
 *
 * Inputs are passed via [INPUT_MANIFEST_JSON] (a serialized [PrefetchManifest])
 * so the worker is fully testable without the app being on a particular screen.
 */
class VideoPrefetchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val cacheManager: VideoCacheManager by lazy {
        VideoCacheManager(
            cacheRoot = File(applicationContext.cacheDir, VideoCacheManager.CACHE_DIR_NAME),
        )
    }

    override suspend fun doWork(): Result {
        val raw = inputData.getString(INPUT_MANIFEST_JSON)
            ?: return Result.failure(failureData("missing manifest json input"))

        val manifest = try {
            json.decodeFromString(PrefetchManifest.serializer(), raw)
        } catch (t: Throwable) {
            return Result.failure(failureData("invalid manifest json: ${t.message}"))
        }

        // Staleness guard: workers must not run a >24h-old prefetch plan.
        val ageMs = System.currentTimeMillis() - manifest.createdAtEpochSeconds * 1000L
        if (ageMs > MAX_MANIFEST_STALENESS_MS) {
            return Result.failure(failureData("manifest stale (age=${ageMs / 1000}s)"))
        }

        setForegroundAsyncSafe()

        // VIDEO (heavier, gameplay-critical) before SVG; preserve manifest order within type.
        val ordered = manifest.assets.sortedByDescending { it.assetType.typeRank() }

        var completed = 0
        var skipped = 0
        var requiredFailure = false

        for (asset in ordered) {
            if (isStopped) {
                // Surfaces partial progress even on interruption so re-enqueue is cheap.
                return Result.success(progressData(manifest, completed, skipped))
            }

            // Cache-key integrity check: the manifest's precomputed key must equal
            // what VideoCacheManager derives, guarding divergent hash upgrades.
            val derivedKey = cacheKey(asset.url)
            if (derivedKey != asset.cacheKey) {
                return Result.failure(failureData("cache key mismatch for ${asset.exerciseId}"))
            }

            // Skip if already cached (idempotent re-runs).
            if (cacheManager.getCachedVideo(asset.url) != null) {
                skipped++
                setProgress(progressData(manifest, completed, skipped))
                continue
            }

            try {
                cacheManager.prefetch(asset.url)
                completed++
                setProgress(progressData(manifest, completed, skipped))
            } catch (t: Throwable) {
                if (asset.required) {
                    requiredFailure = true
                    // Continue so one transient single-asset failure doesn't block
                    // the rest of the session's prefetch; retry the whole run later.
                }
            }
        }

        return if (requiredFailure) {
            // Failures surface as retry (with WorkManager backoff) rather than
            // failure so transient CDN errors self-heal. After the cap, give up.
            if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
                Result.failure(
                    failureData("one or more required assets failed after $MAX_RETRY_ATTEMPTS attempts")
                )
            } else {
                Result.retry()
            }
        } else {
            Result.success(progressData(manifest, completed, skipped))
        }
    }

    // -------------------------------------------------------------- Foreground

    /**
     * Promotes this worker to a foreground service with a low-priority
     * notification so long downloads survive Android 14+ strict-foreground
     * enforcement. Falls back silently when the platform denies foreground
     * promotion (e.g. background-launch restrictions on the non-expedited path).
     */
    private suspend fun setForegroundAsyncSafe() {
        try {
            setForeground(buildForegroundInfo())
        } catch (t: OutOfQuotaException) {
            // Expedited quota exhausted; run as a normal background worker.
        } catch (t: Throwable) {
            // Other platform restrictions; WorkManager still runs under the
            // UNMETERED constraint, possibly with a shorter runtime cap.
        }
    }

    private fun buildForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        ensureChannel()
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.packageName)
            .setContentText("Pre-caching workout videos")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val mgr = NotificationManagerCompat.from(applicationContext)
        mgr.createNotificationChannel(
            NotificationChannelCompat.Builder(
                NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW,
            )
                .setName("Media prefetch")
                .setDescription("Pre-caches workout videos over Wi-Fi")
                .build()
        )
    }

    // -------------------------------------------------------------- Helpers

    private fun failureData(message: String): Data = Data.Builder()
        .putString(ERROR_MESSAGE, message)
        .build()

    private fun progressData(manifest: PrefetchManifest, completed: Int, skipped: Int): Data =
        Data.Builder()
            .putString(PROGRESS_MANIFEST_ID, manifest.manifestId)
            .putInt(PROGRESS_COMPLETED, completed)
            .putInt(PROGRESS_SKIPPED, skipped)
            .putInt(PROGRESS_TOTAL, manifest.assets.size)
            .build()

    companion object {
        const val INPUT_MANIFEST_JSON: String = "prefetch_manifest_json"
        const val ERROR_MESSAGE: String = "error_message"
        const val PROGRESS_MANIFEST_ID: String = "progress_manifest_id"
        const val PROGRESS_COMPLETED: String = "progress_completed"
        const val PROGRESS_SKIPPED: String = "progress_skipped"
        const val PROGRESS_TOTAL: String = "progress_total"

        private const val NOTIFICATION_ID = 4171
        private const val NOTIFICATION_CHANNEL_ID = "leanhome.media.prefetch"
        private const val MAX_MANIFEST_STALENESS_MS = 24L * 60 * 60 * 1000 // 24h
        private const val MAX_RETRY_ATTEMPTS = 3

        /**
         * Enqueues a one-time [VideoPrefetchWorker] uniquely identified by the
         * manifest id. The request is constrained to Wi-Fi / unmetered networks
         * (`NetworkType.UNMETERED`, the hard rule from the manifest schema).
         *
         * Re-enqueueing the same [manifest] is a no-op ([ExistingWorkPolicy.KEEP])
         * so daily check-ins cannot schedule duplicate prefetches.
         */
        fun enqueue(
            context: Context,
            manifest: PrefetchManifest,
            json: Json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                isLenient = true
            },
        ) {
            val data = Data.Builder()
                .putString(
                    INPUT_MANIFEST_JSON,
                    json.encodeToString(PrefetchManifest.serializer(), manifest),
                )
                .build()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            val request = OneTimeWorkRequestBuilder<VideoPrefetchWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                manifest.manifestId,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Builds the canonical prefetch manifest id: SHA-256 hex lowercase of
         * the session_id, matching `manifest_id` in
         * `shared/media/prefetch_manifest_schema.json`.
         */
        fun manifestIdForSession(sessionId: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(sessionId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}

// =================================================================== Models

/**
 * Localizable, serializable prefetch manifest mirroring
 * `shared/media/prefetch_manifest_schema.json`.
 */
@Serializable
data class PrefetchManifest(
    @SerialName("manifest_id") val manifestId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at_epoch_seconds") val createdAtEpochSeconds: Long,
    val assets: List<ManifestAsset>,
    val priority: ManifestPriority = ManifestPriority.DEFAULT,
)

@Serializable
data class ManifestAsset(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("asset_type") val assetType: AssetType,
    val url: String,
    @SerialName("cache_key") val cacheKey: String,
    @SerialName("file_size_bytes") val fileSizeBytes: Long,
    val required: Boolean = true,
)

@Serializable
enum class AssetType {
    VIDEO,
    SVG;

    /** Sort rank: VIDEO (1) prioritized over SVG (0). */
    fun typeRank(): Int = if (this == VIDEO) 1 else 0
}

@Serializable
enum class ManifestPriority { LOW, DEFAULT, HIGH }
