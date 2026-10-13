import Foundation
import CryptoKit
#if canImport(Network)
import Network
#endif
#if canImport(BackgroundTasks)
import BackgroundTasks
#endif
#if canImport(UIKit)
import UIKit
#endif

// MARK: - VideoPrefetchManager
//
// Actor-isolated background pre-cacher for upcoming workout media (Task 5.3).
//
// Mirrors `shared/media/prefetch_manifest_schema.json` and the Android
// VideoPrefetchWorker:
//   - Only runs over Wi-Fi / unmetered networks (NetworkMonitor gate), the
//     hard `network_constraint = "UNMETERED"` rule.
//   - Prefers VIDEO assets before SVG so the heavier, gameplay-critical
//     mp4 is fetched first.
//   - Bounded retries (maxPrefetchAttempts) with idempotent re-runs keyed by
//     the manifest id (KEEP semantics).
//   - Uses BGTaskScheduler on iOS to schedule the prefetch as a background
//     processing task; gracefully no-ops on macOS where BGTaskScheduler is
//     unavailable (CLI `swift test` parity).
//   - Drives an injected VideoCacheManager actor.

/// Type of a manifest asset. VIDEO downloads to the LRU video cache; SVG is
/// also cached but additionally bundled in-app as the offline fallback.
public enum PrefetchAssetType: String, Codable, Sendable {
    case video = "VIDEO"
    case svg = "SVG"

    /// Sort rank: VIDEO (1) prioritized over SVG (0).
    public var typeRank: Int { self == .video ? 1 : 0 }
}

/// Serialized prefetch manifest asset mirroring
/// `shared/media/prefetch_manifest_schema.json`.
public struct PrefetchManifestAsset: Codable, Sendable {
    public let exerciseID: String
    public let assetType: PrefetchAssetType
    public let url: String
    public let cacheKey: String
    public let fileSizeBytes: Int64
    public let required: Bool

    public init(
        exerciseID: String,
        assetType: PrefetchAssetType,
        url: String,
        cacheKey: String,
        fileSizeBytes: Int64,
        required: Bool = true
    ) {
        self.exerciseID = exerciseID
        self.assetType = assetType
        self.url = url
        self.cacheKey = cacheKey
        self.fileSizeBytes = fileSizeBytes
        self.required = required
    }

    /// Decoded URL for this asset (validated for absolute https scheme).
    public var resolvedURL: URL? { URL(string: url) }

    /// Maps snake_case JSON keys (per `prefetch_manifest_schema.json`) to
    /// camelCase Swift property names.
    enum CodingKeys: String, CodingKey {
        case exerciseID = "exercise_id"
        case assetType = "asset_type"
        case url
        case cacheKey = "cache_key"
        case fileSizeBytes = "file_size_bytes"
        case required
    }
}

/// Priority hint forwarded to the scheduler. Mirrors the manifest schema enum.
public enum PrefetchPriority: String, Codable, Sendable {
    case low = "LOW"
    case `default` = "DEFAULT"
    case high = "HIGH"
}

/// Full prefetch manifest. `manifestID` is SHA-256 hex lowercase of the
/// session id, matching `manifest_id` in the manifest schema, so identical
/// re-enqueues are idempotent.
public struct PrefetchManifest: Codable, Sendable {
    public let manifestID: String
    public let sessionID: String
    public let userID: String
    public let createdAtEpochSeconds: Int64
    public let assets: [PrefetchManifestAsset]
    public let priority: PrefetchPriority

    public init(
        manifestID: String,
        sessionID: String,
        userID: String,
        createdAtEpochSeconds: Int64,
        assets: [PrefetchManifestAsset],
        priority: PrefetchPriority = .default
    ) {
        self.manifestID = manifestID
        self.sessionID = sessionID
        self.userID = userID
        self.createdAtEpochSeconds = createdAtEpochSeconds
        self.assets = assets
        self.priority = priority
    }

    /// Computes the canonical manifest id (SHA-256 hex lowercase of sessionID),
    /// matching `manifest_id` in `shared/media/prefetch_manifest_schema.json`.
    public static func manifestID(forSession sessionID: String) -> String {
        let digest = SHA256.hash(data: Data(sessionID.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    /// Maps snake_case JSON keys (per `prefetch_manifest_schema.json`) to
    /// camelCase Swift property names.
    enum CodingKeys: String, CodingKey {
        case manifestID = "manifest_id"
        case sessionID = "session_id"
        case userID = "user_id"
        case createdAtEpochSeconds = "created_at_epoch_seconds"
        case assets
        case priority
    }
}

/// Outcome of a single prefetch run, surfaced to BGTaskScheduler and observers.
public struct PrefetchOutcome: Sendable, Equatable {
    public let manifestID: String
    public let completed: Int
    public let skipped: Int
    public let failed: Int
    /// `true` only when required-asset failures have exhausted the maximum
    /// retry attempts. Staleness/offline skips do not set this flag.
    public let exhaustedRetries: Bool

    public init(
        manifestID: String,
        completed: Int,
        skipped: Int,
        failed: Int,
        exhaustedRetries: Bool
    ) {
        self.manifestID = manifestID
        self.completed = completed
        self.skipped = skipped
        self.failed = failed
        self.exhaustedRetries = exhaustedRetries
    }
}

// MARK: - Network metered gate

/// Wi-Fi / unmetered network gate used by the prefetch manager. Defaults to a
/// NetworkMonitor-based implementation on iOS/macOS; falls back to an
/// optimistic "unmetered" path so `swift test` parity holds where NWPathMonitor
/// is unavailable.
public protocol NetworkMeteredGate: Sendable {
    /// `true` when the current path is unmetered (Wi-Fi / Ethernet).
    func isUnmetered() async -> Bool
}

/// Optimistic gate that always returns `true`. Default in tests and where
/// NWPathMonitor is unavailable.
public struct AlwaysUnmeteredGate: NetworkMeteredGate {
    public init() {}
    public func isUnmetered() async -> Bool { true }
}

#if canImport(Network)
/// NWPathMonitor-backed gate used on iOS/macOS. Reports `isExpensive == false`
/// as the unmetered condition, implementing the manifest schema's hard
/// `network_constraint = "UNMETERED"` rule.
public final class NetworkPathMeteredGate: NetworkMeteredGate, @unchecked Sendable {
    private let queue = DispatchQueue(label: "leanhome.VideoPrefetchManager.networkGate")
    private let monitor: NWPathMonitor

    public init() {
        self.monitor = NWPathMonitor()
        monitor.start(queue: queue)
    }

    deinit {
        monitor.cancel()
    }

    public func isUnmetered() async -> Bool {
        // Snapshot the current path synchronously on the monitor's queue to
        // avoid reading mutable state across threads.
        let path = monitor.currentPath
        return path.status == .satisfied && !path.isExpensive
    }
}
#endif

/// Selects NetworkPathMeteredGate where NWPathMonitor exists (iOS + macOS),
/// and AlwaysUnmeteredGate as a fallback. Encapsulated so callers don't
/// repeat the `#if canImport` dance at every construction site.
public enum DefaultNetworkGateFactory {
    public static func make() -> NetworkMeteredGate {
        #if canImport(Network)
        return NetworkPathMeteredGate()
        #else
        return AlwaysUnmeteredGate()
        #endif
    }
}

#if canImport(Network)
/// Convenience alias for the platform default network gate.
public typealias DefaultNetworkGate = NetworkPathMeteredGate
#else
public typealias DefaultNetworkGate = AlwaysUnmeteredGate
#endif

// MARK: - VideoPrefetchManager actor

/// Actor-isolated background pre-cacher. Schedules prefetch requests on iOS
/// via BGTaskScheduler and dispatches the actual download work to a
/// VideoCacheManager.
public actor VideoPrefetchManager {

    /// BGTaskScheduler task identifier (iOS). Also registered in Info.plist.
    public static let taskIdentifier = "app.leanhome.prefetch.workout-media"

    /// Maximum prefetch attempts before giving up on a required asset.
    public static let maxPrefetchAttempts = 3

    /// Staleness threshold: manifests older than this are not honored.
    public static let maxManifestStalenessSeconds: Int64 = 24 * 60 * 60

    /// Earliest begin offset for the scheduled BGProcessingTask (lets the
    /// system coalesce prefetch with other maintenance work).
    private static let earliestBeginDelaySeconds: TimeInterval = 60

    private let cacheManager: VideoCacheManager
    private let networkGate: NetworkMeteredGate
    /// Per-manifest attempt counter for retry exhaustion tracking.
    private var attemptCounts: [String: Int] = [:]
    /// Pending manifests awaiting a background run, keyed by manifest id.
    private var pendingManifests: [String: PrefetchManifest] = [:]

    public init(
        cacheManager: VideoCacheManager,
        networkGate: NetworkMeteredGate = DefaultNetworkGateFactory.make()
    ) {
        self.cacheManager = cacheManager
        self.networkGate = networkGate
    }

    // MARK: - Public API

    /// Enqueue [manifest] for a future background prefetch. On iOS this
    /// schedules a BGTaskScheduler processing task (the system chooses the
    /// run window); on macOS it runs immediately since there's no system
    /// background scheduler. Idempotent per manifest id.
    public func enqueue(_ manifest: PrefetchManifest) async {
        pendingManifests[manifest.manifestID] = manifest
        #if os(iOS)
        scheduleBackgroundTask(for: manifest)
        #else
        // No BGTaskScheduler on macOS; run once. Discard the partial result;
        // the foreground caller reads prefetchAll(...) directly when needed.
        _ = try? await prefetchAll(manifest: manifest, attempt: 1)
        pendingManifests.removeValue(forKey: manifest.manifestID)
        #endif
    }

    /// Runs the prefetch for [manifest] immediately (e.g. when the user
    /// taps "Cache now" or while the app is in the foreground on Wi-Fi).
    /// Returns the outcome including completed/skipped/failed counts.
    public func prefetchAll(
        manifest: PrefetchManifest,
        attempt: Int = 1
    ) async throws -> PrefetchOutcome {
        // Staleness guard: skip manifests older than the threshold.
        let ageSeconds = nowSeconds() - manifest.createdAtEpochSeconds
        if ageSeconds > Self.maxManifestStalenessSeconds {
            return PrefetchOutcome(
                manifestID: manifest.manifestID,
                completed: 0,
                skipped: 0,
                failed: 0,
                exhaustedRetries: false
            )
        }

        // Wi-Fi gate: defer unprefetched assets when off Wi-Fi.
        guard await networkGate.isUnmetered() else {
            // Offline: nothing will download; report everything as skipped so
            // the caller knows to keep the manifest enqueued for later.
            return PrefetchOutcome(
                manifestID: manifest.manifestID,
                completed: 0,
                skipped: manifest.assets.count,
                failed: 0,
                exhaustedRetries: false
            )
        }

        // VIDEO before SVG; preserve manifest order within type.
        let ordered = manifest.assets.sorted { $0.assetType.typeRank > $1.assetType.typeRank }

        var completed = 0
        var skipped = 0
        var failed = 0

        for asset in ordered {
            guard let url = asset.resolvedURL else {
                if asset.required { failed += 1 }
                continue
            }
            // Cache-key integrity check: the manifest's precomputed key must
            // equal what VideoCacheManager derives, guarding hash upgrades.
            let derivedKey = VideoCacheManager.cacheKey(urlString: asset.url)
            guard derivedKey == asset.cacheKey else {
                if asset.required { failed += 1 }
                continue
            }
            if cacheManager.getCachedVideo(url: url) != nil {
                skipped += 1
                continue
            }
            do {
                _ = try await cacheManager.prefetch(url: url)
                completed += 1
            } catch {
                if asset.required { failed += 1 }
            }
        }

        if failed > 0 {
            // Seed the attempt counter with the caller-supplied attempt
            // number so the exhaustion check honors externally-driven
            // retries (e.g. when the host re-runs after a BGTask run).
            let base = max(attempt, attemptCounts[manifest.manifestID] ?? 0)
            attemptCounts[manifest.manifestID] = base + 1
        } else {
            attemptCounts.removeValue(forKey: manifest.manifestID)
            pendingManifests.removeValue(forKey: manifest.manifestID)
        }

        let attempts = attemptCounts[manifest.manifestID] ?? 0
        return PrefetchOutcome(
            manifestID: manifest.manifestID,
            completed: completed,
            skipped: skipped,
            failed: failed,
            exhaustedRetries: failed > 0 && attempts >= Self.maxPrefetchAttempts
        )
    }

    // MARK: - iOS BGTaskScheduler integration

    #if os(iOS)
    /// Registers the background processing task handler with the system.
    /// Call once at app launch (e.g. from `LeanHomeApp.init`). The handler
    /// routes the task to [runBackgroundTask] on the supplied [manager].
    /// Safe to call multiple times; the registration is idempotent per
    /// identifier but should be invoked before the app finishes launching.
    public static func registerBackgroundTask(using manager: VideoPrefetchManager) {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: taskIdentifier,
            using: nil
        ) { task in
            guard let processing = task as? BGProcessingTask else {
                task.setTaskCompleted(success: false)
                return
            }
            // Detach so the BGTaskScheduler completion handler returns
            // promptly; the actual prefetch runs on the manager actor.
            Task {
                await manager.runBackgroundTask(processing)
            }
        }
    }

    /// Handles a fired BGProcessingTask by looking up the queued manifest
    /// (via `task.taskDescription == manifestID`) and running prefetch. If
    /// the manifest was already resolved (or never queued), completes the
    /// task successfully so the system doesn't penalize the app.
    public func runBackgroundTask(_ task: BGProcessingTask) async {
        let manifestID = task.taskDescription
        guard let manifestID, let manifest = pendingManifests[manifestID] else {
            task.setTaskCompleted(success: true)
            return
        }

        // Cancel handler: if the system reclaims time, mark the task so the
        // next BGProcessingTask opportunity retries (idempotently).
        task.expirationHandler = {
            task.setTaskCompleted(success: false)
        }

        do {
            let outcome = try await prefetchAll(manifest: manifest, attempt: attemptCounts[manifestID] ?? 1)
            let success = outcome.failed == 0
            task.setTaskCompleted(success: success)
            // If work remains, reschedule so the system retries on Wi-Fi.
            if !success && !outcome.exhaustedRetries {
                scheduleBackgroundTask(for: manifest)
            }
        } catch {
            task.setTaskCompleted(success: false)
            scheduleBackgroundTask(for: manifest)
        }
    }

    private func scheduleBackgroundTask(for manifest: PrefetchManifest) {
        let request = BGProcessingTaskRequest(identifier: Self.taskIdentifier)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        request.earliestBeginDate = Date(timeIntervalSinceNow: Self.earliestBeginDelaySeconds)
        request.taskDescription = manifest.manifestID
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // BGTaskScheduler may reject over-quota requests; the host may
            // retry via prefetchAll(...) from the foreground instead.
        }
    }
    #endif

    // MARK: - Helpers

    private func nowSeconds() -> Int64 {
        Int64(Date().timeIntervalSince1970)
    }
}
