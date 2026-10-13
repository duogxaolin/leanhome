//
//  VideoCacheManager.swift
//  Media
//
//  Actor-isolated LRU disk cache for exercise videos (Task 5.2).
//
//  Maps a CDN asset URL (see `shared/media/cdn_asset_schema.json`) to a local
//  file named by the SHA-256 hex digest of the URL. Enforces a configurable
//  storage ceiling (default 500 MB per design.md Decision 4) and evicts the
//  least-recently-accessed entries first when the ceiling is exceeded.
//
//  On-disk layout under <cachesDirectory>/LeanHomeVideoCache/:
//    <sha256>.mp4    raw cached video bytes
//    <sha256>.meta   JSON sidecar { url, cacheKey, firstCachedAt, lastAccessedAt, fileSizeBytes }
//
//  The sidecar metadata feeds the durable LRU eviction order (ascending
//  lastAccessedAt) — eviction survives process relaunches, unlike a purely
//  in-memory access log. All file I/O is confined to the actor so concurrent
//  prefetch + playback reads cannot corrupt the cache directory.
//
//  Offline fallback: getFallbackSVG(for:) resolves a bundled anatomical SVG
//  from this module's `Resources/diagrams` resource bundle when the network
//  is unreachable, matching the workout-player-media spec and Decision 4.
//
//  Public API parity with the Android VideoCacheManager: getCachedVideo,
//  cacheVideo, prefetch, getFallbackSVG, getCacheSize, clearCache. Convenience
//  aliases are provided for callers (FeatureWorkout) using the older
//  cachedFileURL/store/totalSizeBytes/clear/fallbackDiagramURL names.
//

import Foundation
#if canImport(CryptoKit)
import CryptoKit
#endif

/// Resolves a bundled anatomical SVG fallback for an exercise when the
/// network is unreachable and the video is not cached.
public protocol FallbackSVGProvider: Sendable {
    func loadSVG(for exerciseID: String) async -> Data?
}

/// Default provider that reads `<exerciseID>.svg` from the Media target's
/// `Resources/diagrams` resource bundle. Returns `nil` when no diagram is
/// bundled so the player renders a text-only fallback without crashing.
public struct BundleDiagramSVGProvider: FallbackSVGProvider {
    /// Bundle to search. Defaults to the Media SPM module bundle.
    public let bundle: Bundle

    public init(bundle: Bundle = .module) {
        self.bundle = bundle
    }

    public func loadSVG(for exerciseID: String) async -> Data? {
        let url = bundle.url(forResource: exerciseID, withExtension: "svg", subdirectory: "diagrams")
            ?? bundle.url(forResource: exerciseID, withExtension: "svg")
        guard let url else { return nil }
        return try? Data(contentsOf: url)
    }
}

/// A no-op provider used by tests or non-UI contexts that have no bundled
/// diagrams yet. Keeps the cache manager usable without a resource bundle.
public struct EmptyFallbackSVGProvider: FallbackSVGProvider {
    public init() {}
    public func loadSVG(for exerciseID: String) async -> Data? { nil }
}

/// Downloads raw asset bytes for a CDN URL. The default implementation uses
/// `URLSession`; tests inject fakes to avoid network coupling.
public protocol AssetDownloader: Sendable {
    func download(from url: URL) async throws -> Data
}

/// URLSession-backed default downloader. Works on all platforms (iOS/macOS).
public struct URLSessionAssetDownloader: AssetDownloader {
    public let connectTimeout: TimeInterval
    public let readTimeout: TimeInterval

    public init(connectTimeout: TimeInterval = 15, readTimeout: TimeInterval = 30) {
        self.connectTimeout = connectTimeout
        self.readTimeout = readTimeout
    }

    public func download(from url: URL) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = readTimeout
        request.setValue("video/mp4,image/svg+xml", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw URLError(.badServerResponse, userInfo: [NSURLErrorFailingURLErrorKey: url])
        }
        guard !data.isEmpty else { throw VideoCacheError.emptyResponse(url.absoluteString) }
        return data
    }
}

/// Errors thrown by the video cache.
public enum VideoCacheError: Error, Equatable, Sendable {
    /// The downloader returned no bytes (treated as a corrupt/empty asset).
    case emptyResponse(String)
}

/// Actor-isolated LRU video cache manager.
public actor VideoCacheManager {

    /// Default cache ceiling: 500 MB = 500 * 1024 * 1024 bytes
    /// (per design.md Decision 4 and cdn_asset_schema.json).
    public static let defaultMaxCacheBytes: Int64 = 500 * 1024 * 1024

    /// Canonical cache directory name under the platform caches directory.
    public static let cacheDirectoryName = "LeanHomeVideoCache"

    /// Default crossorigin singleton, useful for app-wide access from
    /// FeatureWorkout. Constructed with the default caches directory and a
    /// BundleDiagramSVGProvider backed by the Media resource bundle.
    public static let shared = VideoCacheManager()

    private let fileManager = FileManager.default
    private let maxCacheBytes: Int64
    /// The resolved cache directory (created on init).
    public let cacheDirectory: URL
    private var fallbackSVGProvider: FallbackSVGProvider
    private let downloader: AssetDownloader

    public init(
        cacheDirectory: URL? = nil,
        maxCacheBytes: Int64 = VideoCacheManager.defaultMaxCacheBytes,
        fallbackSVGProvider: FallbackSVGProvider = BundleDiagramSVGProvider(),
        downloader: AssetDownloader = URLSessionAssetDownloader()
    ) {
        precondition(maxCacheBytes > 0, "maxCacheBytes must be positive")
        self.maxCacheBytes = maxCacheBytes
        self.fallbackSVGProvider = fallbackSVGProvider
        self.downloader = downloader
        if let cacheDirectory {
            self.cacheDirectory = cacheDirectory
            try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
        } else {
            let caches = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first
                ?? URL(fileURLWithPath: NSTemporaryDirectory())
            let dir = caches.appendingPathComponent(Self.cacheDirectoryName, isDirectory: true)
            try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
            self.cacheDirectory = dir
        }
    }

    // MARK: - Public API (Task 5.2 mandated names)

    /// Returns the cached video file for [urlString] (an absolute CDN URL) if
    /// present, updating its last-access timestamp and refreshing LRU
    /// ordering via the sidecar. `nil` when the cache does not contain a
    /// complete, non-zero-byte entry for [urlString].
    public func getCachedVideo(urlString: String) -> URL? {
        let key = Self.cacheKey(urlString)
        let videoFile = videoFileURL(key)
        let metaFile = metaFileURL(key)
        guard fileManager.fileExists(atPath: videoFile.path),
              fileManager.fileExists(atPath: metaFile.path) else {
            return nil
        }
        let size = (try? fileManager.attributesOfItem(atPath: videoFile.path)[.size] as? NSNumber)?.int64Value ?? 0
        if size == 0 {
            try? fileManager.removeItem(at: videoFile)
            try? fileManager.removeItem(at: metaFile)
            return nil
        }
        if var meta = readMeta(at: metaFile) {
            meta.lastAccessedAt = nowMillis()
            writeMeta(meta, at: metaFile)
        }
        return videoFile
    }

    /// Overload accepting a `URL` directly (convenience for callers that already
    /// decode URLs).
    public func getCachedVideo(url: URL) -> URL? {
        getCachedVideo(urlString: url.absoluteString)
    }

    /// Atomically writes [data] to the cache under the SHA-256 key for
    /// [urlString] and refreshes the LRU sidecar. If caching the new entry
    /// would exceed the configured ceiling, least-recently-used entries are
    /// evicted until the new bytes fit. Returns the absolute file URL.
    public func cacheVideo(urlString: String, data: Data) -> URL {
        precondition(!data.isEmpty, "cacheVideo: data must not be empty")
        let key = Self.cacheKey(urlString)
        let videoFile = videoFileURL(key)
        let metaFile = metaFileURL(key)
        let tempFile = cacheDirectory.appendingPathComponent("\(key).mp4.part")

        fileManager.createFile(atPath: tempFile.path, contents: data, attributes: nil)
        if fileManager.fileExists(atPath: videoFile.path) {
            try? fileManager.removeItem(at: videoFile)
        }
        do {
            try fileManager.moveItem(at: tempFile, to: videoFile)
        } catch {
            try? fileManager.copyItem(at: tempFile, to: videoFile)
            try? fileManager.removeItem(at: tempFile)
        }
        let size = (try? fileManager.attributesOfItem(atPath: videoFile.path)[.size] as? NSNumber)?.int64Value
            ?? Int64(data.count)
        let now = nowMillis()
        writeMeta(
            CacheMeta(url: urlString, cacheKey: key, firstCachedAt: now, lastAccessedAt: now, fileSizeBytes: size),
            at: metaFile
        )
        enforceCeiling()
        return videoFile
    }

    /// Overload accepting a `URL`.
    public func cacheVideo(url: URL, data: Data) -> URL {
        cacheVideo(urlString: url.absoluteString, data: data)
    }

    /// Downloads [url] into the cache when absent, returning the cached file.
    /// Throws the original error from the downloader on network failure so the
    /// prefetch manager can retry with BGTaskScheduler.
    public func prefetch(url: URL) async throws -> URL {
        if let cached = getCachedVideo(url: url) {
            return cached
        }
        let data = try await downloader.download(from: url)
        return cacheVideo(url: url, data: data)
    }

    /// Returns the bundled SVG fallback asset bytes for [exerciseID] when the
    /// video is not cached or the network is unreachable. `nil` means no
    /// bundled diagram exists and the player renders a text-only fallback.
    public func getFallbackSvg(for exerciseID: String) async -> Data? {
        await fallbackSVGProvider.loadSVG(for: exerciseID)
    }

    /// Total byte size of currently cached video files (sidecar metadata is
    /// excluded from the budget and the returned total).
    public func getCacheSize() -> Int64 {
        sumCachedVideoBytes()
    }

    /// Removes every cached file (videos + sidecars) and returns the number
    /// of bytes reclaimed.
    @discardableResult
    public func clearCache() -> Int64 {
        var reclaimed: Int64 = 0
        guard let items = try? fileManager.contentsOfDirectory(at: cacheDirectory, includingPropertiesForKeys: nil) else {
            return 0
        }
        for url in items {
            let last = url.lastPathComponent
            guard last.hasSuffix(".mp4") || last.hasSuffix(".meta") else { continue }
            let size = (try? fileManager.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.int64Value ?? 0
            try? fileManager.removeItem(at: url)
            reclaimed += size
        }
        return reclaimed
    }

    /// Replaces the active fallback SVG provider. Useful at app startup when
    /// the resource bundle becomes available after the actor is constructed.
    public func setFallbackSVGProvider(_ provider: FallbackSVGProvider) {
        fallbackSVGProvider = provider
    }

    // MARK: - Aliases (legacy/FeatureWorkout compatibility)

    /// Alias for [getCachedVideo(urlString:)].
    public func cachedFileURL(for urlString: String) -> URL? { getCachedVideo(urlString: urlString) }
    /// Alias for [cacheVideo(urlString:data:)].
    public func store(_ data: Data, for urlString: String) throws {
        _ = cacheVideo(urlString: urlString, data: data)
    }
    /// Alias for [getCacheSize()].
    public func totalSizeBytes() -> Int64 { getCacheSize() }
    /// Alias for [clearCache()].
    public func clear() throws { _ = clearCache() }
    /// Returns the fallback SVG diagram URL (not bytes) for [exerciseID].
    public func fallbackDiagramURL(for exerciseID: String) -> URL? {
        Bundle.module.url(forResource: exerciseID, withExtension: "svg", subdirectory: "diagrams")
            ?? Bundle.module.url(forResource: exerciseID, withExtension: "svg")
    }

    // MARK: - LRU internals

    /// Evicts least-recently-accessed entries until the cache fits within
    /// `maxCacheBytes`. Must run inside the actor.
    private func enforceCeiling() {
        var total = sumCachedVideoBytes()
        guard total > maxCacheBytes else { return }
        guard let items = try? fileManager.contentsOfDirectory(at: cacheDirectory, includingPropertiesForKeys: nil) else {
            return
        }
        var candidates: [CacheMeta] = []
        for url in items where url.pathExtension == "meta" {
            if let meta = readMeta(at: url) { candidates.append(meta) }
        }
        candidates.sort { $0.lastAccessedAt < $1.lastAccessedAt }
        for meta in candidates {
            guard total > maxCacheBytes else { break }
            let vFile = videoFileURL(meta.cacheKey)
            let mFile = metaFileURL(meta.cacheKey)
            let videoLen = (try? fileManager.attributesOfItem(atPath: vFile.path)[.size] as? NSNumber)?.int64Value ?? 0
            try? fileManager.removeItem(at: vFile)
            try? fileManager.removeItem(at: mFile)
            total -= videoLen
        }
    }

    private func sumCachedVideoBytes() -> Int64 {
        guard let items = try? fileManager.contentsOfDirectory(at: cacheDirectory, includingPropertiesForKeys: nil) else {
            return 0
        }
        var total: Int64 = 0
        for url in items where url.pathExtension == "mp4" {
            let size = (try? fileManager.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.int64Value ?? 0
            total += size
        }
        return total
    }

    // MARK: - Sidecar I/O

    /// LRU sidecar persisted alongside each cached video.
    fileprivate struct CacheMeta: Codable {
        let url: String
        let cacheKey: String
        let firstCachedAt: Int64
        var lastAccessedAt: Int64
        let fileSizeBytes: Int64
    }

    private func readMeta(at url: URL) -> CacheMeta? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(CacheMeta.self, from: data)
    }

    private func writeMeta(_ meta: CacheMeta, at url: URL) {
        if let data = try? JSONEncoder().encode(meta) {
            try? data.write(to: url, options: .atomic)
        }
    }

    private func videoFileURL(_ key: String) -> URL {
        cacheDirectory.appendingPathComponent("\(key).mp4")
    }

    private func metaFileURL(_ key: String) -> URL {
        cacheDirectory.appendingPathComponent("\(key).meta")
    }

    private func nowMillis() -> Int64 {
        Int64((Date().timeIntervalSince1970 * 1000).rounded())
    }

    // MARK: - Static helpers

    /// Computes the canonical on-device cache key for [urlString]: the SHA-256
    /// hex-lowercase digest of the absolute CDN URL string (UTF-8, with any
    /// trailing slash trimmed). Matches `cache_key_algorithm` in
    /// `shared/media/cdn_asset_schema.json` and the Android implementation so
    /// manifests can precompute keys once for both platforms. Trimming the
    /// trailing slash guarantees `…/video.mp4` and `…/video.mp4/` map to the
    /// same cache entry.
    public static func cacheKey(_ urlString: String) -> String {
        let normalized = urlString.hasSuffix("/") ? String(urlString.dropLast()) : urlString
        #if canImport(CryptoKit)
        let digest = SHA256.hash(data: Data(normalized.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
        #else
        // CryptoKit-less environment: SHA-256 via CommonCrypto if available,
        // else a stable FNV-1a 64-bit fallback (sufficient for unit-test
        // parity on hosts without crypto; production paths always have
        // CryptoKit on iOS/macOS).
        return sha256HexFallback(normalized)
        #endif
    }

    /// Convenience alias for [cacheKey(_:)] (legacy name).
    public static func key(for urlString: String) -> String { cacheKey(urlString) }

    /// Convenience accepting a `URL`.
    public static func cacheKey(url: URL) -> String { cacheKey(url.absoluteString) }

    /// Canonical CDN video URL for an exercise id, matching
    /// `video_path_template` in cdn_asset_schema.json.
    public static func cdnVideoURL(for exerciseID: String) -> URL? {
        URL(string: "https://cdn.leanhome.app/exercises/\(exerciseID)/video.mp4")
    }

    /// Canonical CDN SVG fallback URL for an exercise id, matching
    /// `svg_path_template` in cdn_asset_schema.json.
    public static func cdnSVGURL(for exerciseID: String) -> URL? {
        URL(string: "https://cdn.leanhome.app/exercises/\(exerciseID)/diagram.svg")
    }
}

// MARK: - CryptoKit-less SHA-256 fallback
//
// Used only when `CryptoKit` is unavailable (not the case on iOS/macOS, but
// keeps the package building on stripped-down toolchains). Delegates to
// CommonCrypto where present; otherwise a deterministic FNV-1a 64-bit digest
// is used so cache keys remain stable within a process for unit tests.

#if !canImport(CryptoKit)
private func sha256HexFallback(_ input: String) -> String {
    #if canImport(CommonCrypto)
    // CommonCrypto SHA-256.
    let data = Array(input.utf8)
    var digest = [UInt8](repeating: 0, count: 32)
    data.withUnsafeBufferPointer { ptr in
        CC_SHA256(ptr.baseAddress, CC_LONG(data.count), &digest)
    }
    return digest.map { String(format: "%02x", $0) }.joined()
    #else
    // FNV-1a 64-bit (stable, non-cryptographic). 16 hex chars only — distinct
    // from the 64-char SHA-256 digest, so this branch is documented as a
    // test-only fallback and MUST NOT ship to iOS/macOS devices.
    var hash: UInt64 = 0xcbf29ce484222325
    for byte in input.utf8 {
        hash ^= UInt64(byte)
        hash &*= 0x100000001b3
    }
    return String(format: "%016llx", hash)
    #endif
}
#endif
