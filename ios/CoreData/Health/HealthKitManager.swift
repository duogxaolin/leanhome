//
//  HealthKitManager.swift
//  CoreData
//
//  Read-only bridge to Apple HealthKit. Exposes step count, active energy
//  burned, and body mass reads over a trailing window. All methods are
//  async and actor-isolated per the Swift 6 strict-concurrency contract.
//  The actor is read-only — no write/insert/save methods are exposed.
//

#if canImport(HealthKit)

import Foundation
import HealthKit

/// Read-only bridge to the user's HealthKit store.
///
/// Surfaces three read types requested by the integration spec:
/// * `HKQuantityType.stepCount`
/// * `HKQuantityType.activeEnergyBurned`
/// * `HKQuantityType.bodyMass`
///
/// All interactions are funneled through a single `HealthKitManager` actor
/// guarded by `HKHealthStore.isHealthDataAvailable()`. Permission requests
/// use the standard `requestAuthorization(toShare:read:)` flow with an empty
/// share set, enforcing the read-only contract at the API surface.
public actor HealthKitManager {

    /// Underlying HealthKit store used for every query.
    private let store: HKHealthStore

    /// The read-only types the manager requests access to. Stored as
    /// `HKQuantityType` instances for predicate construction.
    private let stepCountType: HKQuantityType
    private let activeEnergyType: HKQuantityType
    private let bodyMassType: HKQuantityType

    /// `true` only when HealthKit is supported on this device (iPhone, Apple
    /// Watch) — false on iPad / unsupported devices. The dashboard UI uses
    /// this to hide the import row rather than surface an error.
    public static var isAvailable: Bool {
        HKHealthStore.isHealthDataAvailable()
    }

    public init() throws {
        guard HKHealthStore.isHealthDataAvailable() else {
            throw HealthKitError.unavailableOnDevice
        }
        let store = HKHealthStore()
        self.store = store
        guard let stepsType = HKQuantityType.quantityType(forIdentifier: .stepCount),
              let energyType = HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned),
              let massType = HKQuantityType.quantityType(forIdentifier: .bodyMass) else {
            throw HealthKitError.typeResolutionFailure
        }
        self.stepCountType = stepsType
        self.activeEnergyType = energyType
        self.bodyMassType = massType
    }

    // MARK: - Permission flow

    /// Requests read authorization for the three required types. Writes an
    /// empty share set so the app cannot write any samples back to HealthKit.
    ///
    /// Returns `true` if at least one read type was authorized, `false` if
    /// the user denied. The system may show no prompt at all on subsequent
    /// calls once a decision has been recorded — callers should rely on
    /// [hasAuthorization] afterward.
    @discardableResult
    public func requestAuthorization() async throws -> Bool {
        let readTypes: Set<HKObjectType> = [
            stepCountType,
            activeEnergyType,
            bodyMassType,
        ]
        // Empty share set enforces read-only at the HealthKit surface.
        let shareTypes: Set<HKSampleType> = []
        return try await withCheckedThrowingContinuation { continuation in
            store.requestAuthorization(toShare: shareTypes, read: readTypes) { _, error in
                if let error = error {
                    continuation.resume(throwing: HealthKitError.authorizationFailed(error))
                    return
                }
                continuation.resume(returning: true)
            }
        }
    }

    /// Best-effort permission probe. HealthKit does not expose a fine-grained
    /// per-type "is authorized" API; this issues a zero-window statistics
    /// query for each type and infers permission from the absence of an
    /// authorization-denied error. Designed for the UX fallback path only.
    public func hasAuthorization() async -> Bool {
        let readTypes: [HKQuantityType] = [stepCountType, activeEnergyType, bodyMassType]
        for type in readTypes {
            let granted = await probeAuthorization(for: type)
            if !granted { return false }
        }
        return true
    }

    // MARK: - Read-only queries

    /// Returns the cumulative step count over the trailing `days` calendar
    /// days (local-tz day boundaries), or `0` when the user has no data in
    /// the window.
    public func readStepCount(days: Int) async throws -> Int {
        let predicate = predicate(forDays: days)
        let options: HKStatisticsOptions = [.cumulativeSum]
        let statistics = try await statisticsQuery(
            quantityType: stepCountType,
            options: options,
            predicate: predicate,
        )
        let sum = statistics?.sumQuantity()?.doubleValue(for: .count())
        return Int(sum ?? 0.0)
    }

    /// Returns the cumulative active-energy burned in kilocalories over the
    /// trailing `days` calendar days, or `0` when the user has no data.
    public func readActiveEnergy(days: Int) async throws -> Double {
        let predicate = predicate(forDays: days)
        let options: HKStatisticsOptions = [.cumulativeSum]
        let statistics = try await statisticsQuery(
            quantityType: activeEnergyType,
            options: options,
            predicate: predicate,
        )
        let sum = statistics?.sumQuantity()?.doubleValue(for: .kilocalorie())
        return sum ?? 0.0
    }

    /// Returns the most recent body-mass sample in kilograms, or `nil` when
    /// the user has no body-mass samples in their store.
    public func readLatestWeight() async throws -> Double? {
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)
        let limit = 1
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Double?, Error>) in
            let query = HKSampleQuery(
                sampleType: bodyMassType,
                predicate: nil,
                sortDescriptors: [sort],
                limit: limit,
            ) { _, samples, error in
                if let error = error {
                    continuation.resume(throwing: HealthKitError.queryFailed(error))
                    return
                }
                let sample = samples?.first as? HKQuantitySample
                let mass = sample?.quantity.doubleValue(for: .gramUnit(with: .kilo))
                continuation.resume(returning: mass)
            }
            store.execute(query)
        }
    }

    // MARK: - Internals

    /// Builds an `HKQuery` predicate covering the trailing `days` day
    /// boundaries in the user's local time zone.
    private func predicate(forDays days: Int) -> NSPredicate {
        let calendar = Calendar.current
        let now = Date()
        guard let startDate = calendar.date(byAdding: .day, value: -(days - 1), to: calendar.startOfDay(for: now)) else {
            return HKQuery.predicateForSamples(withStart: nil, end: nil, options: [])
        }
        return HKQuery.predicateForSamples(withStart: startDate, end: now, options: [.strictStartDate])
    }

    /// Async wrapper over `HKStatisticsQuery` so callers can `await` the
    /// result without explicit callback management.
    private func statisticsQuery(
        quantityType: HKQuantityType,
        options: HKStatisticsOptions,
        predicate: NSPredicate
    ) async throws -> HKStatistics? {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<HKStatistics?, Error>) in
            let query = HKStatisticsQuery(
                quantityType: quantityType,
                quantitySamplePredicate: predicate,
                options: options,
            ) { _, statistics, error in
                if let error = error {
                    continuation.resume(throwing: HealthKitError.queryFailed(error))
                    return
                }
                continuation.resume(returning: statistics)
            }
            store.execute(query)
        }
    }

    /// Quick authorization probe using a zero-width sample query for `type`.
    /// HealthKit returns `errorAuthorizationNotDetermined` until the user
    /// has decided; once decided, an absence of `errorAuthorizationDenied`
    /// implies permission was granted for the type.
    private func probeAuthorization(for type: HKQuantityType) async -> Bool {
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(
                sampleType: type,
                predicate: nil,
                sortDescriptors: [sort],
                limit: 1,
            ) { _, samples, error in
                if error != nil {
                    continuation.resume(returning: false)
                    return
                }
                _ = samples
                continuation.resume(returning: true)
            }
            store.execute(query)
        }
    }
}

/// Errors surfaced by [HealthKitManager].
public enum HealthKitError: Error {
    /// HealthKit is not available on this device.
    case unavailableOnDevice
    /// One of the required `HKQuantityType` identifiers could not be resolved.
    case typeResolutionFailure
    /// Authorization request failed with the given underlying error.
    case authorizationFailed(any Error)
    /// A query failed with the given underlying error.
    case queryFailed(any Error)
}

#endif // canImport(HealthKit)
