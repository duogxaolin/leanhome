package com.leanhome.core.data.cache

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * On-disk Least-Recently-Used (LRU) video cache for the LeanHome media pipeline.
 *
 * Maps a CDN asset URL (see `shared/media/cdn_asset_schema.json`) to a local
 * file named by the SHA-256 hex digest of the URL. Enforces a configurable
 * storage ceiling (default 500 MB per the design doc) and evicts the
 * least-recently-accessed entries first when the ceiling is exceeded.
 *
 * Asset layout on disk:
 * ```
 * <cacheRoot>/leanhome_video_cache/
 *   <sha256>.mp4        # raw cached video bytes
 *   <sha256>.meta       # JSON sidecar: { url, cacheKey, firstCachedAt, lastAccessedAt, fileSizeBytes }
 * ```
 *
 * The cache is offline-first: [getFallbackSvg] resolves a bundled anatomical
 * SVG from the app assets when a video is absent or the network is down,
 * matching the "video available online" offline fallback described in the
 * workout-player-media spec and Decision 4 of the design doc.
 *
 * All public APIs are main-safe: file I/O runs on [ioDispatcher] and the
 * in-memory index is guarded by a single [Mutex] so concurrent prefetch and
 * playback reads cannot corrupt the cache directory.
 *
 * @param cacheRoot directory that will contain the `leanhome_video_cache` folder.
 * @param maxCacheBytes storage ceiling in bytes; default 500 MB (524_288_000).
 * @param json configured [Json] instance; defaults to lenient parsing matching
 *   `core-domain`'s serializer configuration (lenient, ignoreUnknownKeys,
 *   explicitNulls=false).
 */
class VideoCacheManager(
    private val cacheRoot: File,
    private val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    init {
        require(maxCacheBytes > 0) { "maxCacheBytes must be positive, was $maxCacheBytes" }
    }

    /** Canonical cache directory holding all video files and sidecar metadata. */
    val cacheDir: File = File(cacheRoot, CACHE_DIR_NAME).apply { mkdirs() }

    /** Bundled SVG provider — injects the offline fallback SVG asset lookup. */
    var fallbackSvgProvider: FallbackSvgProvider = NoopFallbackSvgProvider

    /** In-memory guard ensuring only one structural mutation runs at a time. */
    private val mutex = Mutex()

    // ------------------------------------------------------------------ Public API

    /**
     * Returns the cached video file for [url] if present, updating its
     * last-access timestamp and refreshing LRU ordering. Returns `null` when
     * the cache does not contain a complete, non-zero-byte entry for [url].
     */
    suspend fun getCachedVideo(url: String): File? = withContext(ioDispatcher) {
        val key = cacheKey(url)
        val videoFile = videoFile(key)
        val metaFile = metaFile(key)
        if (!videoFile.exists() || !metaFile.exists()) return@withContext null
        if (videoFile.length() == 0L) {
            // Stale/partial entry: evict it and report a miss.
            runCatching {
                videoFile.delete()
                metaFile.delete()
            }
            return@withContext null
        }
        mutex.withLock {
            readMeta(metaFile)?.let { existing ->
                writeMeta(metaFile, existing.copy(lastAccessedAt = nowMillis()))
            }
        }
        videoFile
    }

    /**
     * Atomically writes [bytes] to the cache under the SHA-256 key for [url]
     * and refreshes the LRU sidecar. If caching the new entry would exceed
     * the [maxCacheBytes] ceiling, the least-recently-used entries are
     * evicted until the new bytes fit. Returns the absolute [File] on success.
     *
     * Re-caching an existing URL atomically replaces the prior bytes and
     * bumps both first-cached and last-access timestamps.
     */
    suspend fun cacheVideo(url: String, bytes: ByteArray): File = withContext(ioDispatcher) {
        require(bytes.isNotEmpty()) { "cacheVideo: bytes must not be empty" }
        val key = cacheKey(url)
        val videoFile = videoFile(key)
        val metaFile = metaFile(key)
        mutex.withLock {
            val tempFile = File(cacheDir, "$key.mp4.part")
            try {
                tempFile.writeBytes(bytes)
                // Replace any prior entry atomically.
                if (videoFile.exists()) videoFile.delete()
                if (!tempFile.renameTo(videoFile)) {
                    // renameTo can fail across filesystem boundaries on some OEMs;
                    // fall back to copy+delete.
                    tempFile.copyTo(videoFile, overwrite = true)
                    tempFile.delete()
                }
                writeMeta(
                    metaFile,
                    CacheMeta(
                        url = url,
                        cacheKey = key,
                        firstCachedAt = nowMillis(),
                        lastAccessedAt = nowMillis(),
                        fileSizeBytes = videoFile.length(),
                    ),
                )
                enforceCeiling()
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
        videoFile
    }

    /**
     * Downloads [url] into the cache when absent, returning the cached file.
     * Throws the original [Exception] if the network fetch fails; on success
     * behaves like [cacheVideo]. The download is delegated to [downloader]
     * (defaults to [DefaultAssetDownloader]) so tests can inject fakes.
     */
    suspend fun prefetch(
        url: String,
        downloader: AssetDownloader = DefaultAssetDownloader,
    ): File = withContext(ioDispatcher) {
        getCachedVideo(url)?.let { return@withContext it }
        val bytes = downloader.download(url)
        cacheVideo(url, bytes)
    }

    /**
     * Returns the SVG fallback asset for [exerciseId] when its video is not
     * cached or the network is unreachable. The fallback is sourced from the
     * app bundle via [fallbackSvgProvider], satisfying the offline-fallback
     * requirement: "if a video is missing and the device is offline, the UI
     * shows a high-contrast anatomical SVG illustration".
     *
     * Returns `null` only when no bundled SVG exists for the exercise — the
     * player treats this as a text-only fallback.
     */
    suspend fun getFallbackSvg(exerciseId: String): ByteArray? = withContext(ioDispatcher) {
        fallbackSvgProvider.loadSvg(exerciseId)
    }

    /**
     * Total byte size of currently cached video files (metadata sidecars are
     * excluded from the budget and the returned total). Main-safe.
     */
    suspend fun getCacheSize(): Long = withContext(ioDispatcher) {
        mutex.withLock { sumCachedVideoBytes() }
    }

    /**
     * Removes every cached file (videos + metadata sidecars) and returns the
     * number of bytes reclaimed. Main-safe.
     */
    suspend fun clearCache(): Long = withContext(ioDispatcher) {
        mutex.withLock {
            var reclaimed = 0L
            cacheDir.listFiles()?.forEach { f ->
                if (f.isFile && (f.name.endsWith(EXT_VIDEO) || f.name.endsWith(EXT_META))) {
                    reclaimed += f.length()
                    f.delete()
                }
            }
            reclaimed
        }
    }

    // ------------------------------------------------------------------ LRU internals

    /**
     * Evicts least-recently-accessed entries until the cache fits within
     * [maxCacheBytes]. Caller must already hold [mutex].
     */
    private fun enforceCeiling() {
        var total = sumCachedVideoBytes()
        if (total <= maxCacheBytes) return
        // Sort candidates by lastAccessedAt ascending; evict until under ceiling.
        val candidates = cacheDir.listFiles { f ->
            f.isFile && f.name.endsWith(EXT_META)
        }?.mapNotNull { m -> readMeta(m)?.let { it to m } }
            ?.sortedBy { it.first.lastAccessedAt } ?: return
        for ((meta, mFile) in candidates) {
            if (total <= maxCacheBytes) break
            val vFile = videoFile(meta.cacheKey)
            val videoLen = if (vFile.exists()) vFile.length() else 0L
            runCatching {
                vFile.delete()
                mFile.delete()
            }
            total -= videoLen
        }
    }

    private fun sumCachedVideoBytes(): Long {
        return cacheDir.listFiles { f -> f.isFile && f.name.endsWith(EXT_VIDEO) }
            ?.sumOf { it.length() }
            ?: 0L
    }

    // ------------------------------------------------------------------ Sidecar I/O

    @Serializable
    internal data class CacheMeta(
        val url: String,
        val cacheKey: String,
        val firstCachedAt: Long,
        val lastAccessedAt: Long,
        val fileSizeBytes: Long,
    )

    private fun readMeta(file: File): CacheMeta? = runCatching {
        if (!file.exists()) return null
        json.decodeFromString(CacheMeta.serializer(), file.readText())
    }.getOrNull()

    private fun writeMeta(file: File, meta: CacheMeta) {
        file.writeText(json.encodeToString(CacheMeta.serializer(), meta))
    }

    private fun videoFile(key: String): File = File(cacheDir, "$key$EXT_VIDEO")
    private fun metaFile(key: String): File = File(cacheDir, "$key$EXT_META")

    private fun nowMillis(): Long = System.currentTimeMillis()

    companion object {
        /** Default cache ceiling: 500 MB = 500 * 1024 * 1024 bytes (per design doc Decision 4). */
        const val DEFAULT_MAX_CACHE_BYTES: Long = 500L * 1024L * 1024L

        const val CACHE_DIR_NAME: String = "leanhome_video_cache"
        private const val EXT_VIDEO: String = ".mp4"
        private const val EXT_META: String = ".meta"

        /**
         * Computes the canonical on-device cache key for [url]: the SHA-256
         * hex-lowercase digest of the absolute CDN URL string (UTF-8, no
         * trailing slash, exactly as fetched). This matches the
         * `cache_key_algorithm` declared in `shared/media/cdn_asset_schema.json`
         * so both Android and iOS derive identical file names for the same
         * asset and so manifests can precompute keys once.
         */
        fun cacheKey(url: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val normalized = url.trimEnd('/')
            val digest = md.digest(normalized.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Downloads raw asset bytes for a CDN [url]. Implementations may stream to
 * file for very large assets; the default reads the full response body into
 * memory, which is correct for the 1.5–3 MB 720p looping clips in the catalog.
 */
fun interface AssetDownloader {
    suspend fun download(url: String): ByteArray
}

/**
 * Default [AssetDownloader] backed by `HttpURLConnection`. Follows redirects,
 * enforces a 2xx status, and surfaces the original [java.io.IOException] on
 * failure so the prefetch worker can retry with WorkManager backoff.
 */
object DefaultAssetDownloader : AssetDownloader {
    override suspend fun download(url: String): ByteArray {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "video/mp4,image/svg+xml")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw java.io.IOException("HTTP $code downloading $url")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
}

/**
 * Resolves the bundled anatomical SVG fallback for an exercise when the
 * video is missing. Implementations read from the platform app bundle/assets.
 */
fun interface FallbackSvgProvider {
    suspend fun loadSvg(exerciseId: String): ByteArray?
}

/** No-op provider used by default in non-UI/test contexts. */
object NoopFallbackSvgProvider : FallbackSvgProvider {
    override suspend fun loadSvg(exerciseId: String): ByteArray? = null
}
