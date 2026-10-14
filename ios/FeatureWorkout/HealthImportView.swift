//
//  HealthImportView.swift
//  FeatureWorkout
//
//  Read-only HealthKit import section surfaced on the Progress Dashboard.
//  Requests read authorization for step count, active energy, and body mass,
//  then displays the trailing 7-day daily-step average plus the estimated
//  activity level. Handles denial with a retry button and gracefully hides
//  on devices where HealthKit is unavailable (e.g. iPad).
//

#if canImport(SwiftUI) && canImport(HealthKit)

import SwiftUI
import HealthKit
import CoreDomain
import CoreData

/// UI state machine for the HealthKit import row on the dashboard.
enum HealthImportState: Equatable {
    case idle
    case loading
    case success(dailyAverageSteps: Int, totalSteps: Int, totalActiveEnergyKcal: Double, activityLevel: ActivityLevel, windowDays: Int)
    case denied
    case unavailable

    static func == (lhs: HealthImportState, rhs: HealthImportState) -> Bool {
        switch (lhs, rhs) {
        case (.idle, .idle),
             (.loading, .loading),
             (.denied, .denied),
             (.unavailable, .unavailable):
            return true
        case let (.success(la, lt, le, ll, ld), .success(ra, rt, re, rl, rd)):
            return la == ra && lt == rt && le == re && ll == rl && ld == rd
        default:
            return false
        }
    }
}

/// SwiftUI section embedded into `ProgressDashboardView`. Performs the
/// HealthKit authorization and read flow in a `@MainActor` task.
public struct HealthImportSection: View {

    @State private var state: HealthImportState = HealthKitManager.isAvailable ? .idle : .unavailable
    private let windowDays: Int = 7
    private let manager: HealthKitManager?

    public init() {
        if HealthKitManager.isAvailable {
            // `try?` swallows the rare `HKHealthStore()` allocation failure and
            // degrades to `.unavailable` — UI stays hidden, no crash.
            self.manager = try? HealthKitManager()
        } else {
            self.manager = nil
        }
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Health import")
                .font(.headline)

            switch state {
            case .idle:
                Text("Import step count and active energy from Apple Health to fine-tune your TDEE.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button("Import from Health") {
                    Task { await startImport() }
                }
                .buttonStyle(.borderedProminent)
            case .loading:
                HStack {
                    ProgressView()
                    Text("Reading Health data…")
                        .font(.callout)
                }
            case .success(let daily, let total, let energy, let level, let days):
                Text("Last \(days) days · daily avg: \(daily) steps")
                    .font(.callout)
                Text("Total \(total) steps · active energy \(Int(energy)) kcal")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Estimated activity level: \(level.rawValue)")
                    .font(.caption)
                    .fontWeight(.medium)
                Button("Refresh Health import") {
                    Task { await startImport() }
                }
                .buttonStyle(.bordered)
            case .denied:
                Text("Health data unavailable — permission was denied.")
                    .font(.callout)
                Button("Retry") {
                    Task { await startImport() }
                }
                .buttonStyle(.bordered)
            case .unavailable:
                EmptyView()
            }
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.secondary.opacity(0.10)))
        .animation(.easeInOut, value: state == .loading)
        .opacity(state == .unavailable ? 0 : 1)
        .frame(maxHeight: state == .unavailable ? 0 : nil)
    }

    // MARK: - Flow

    /// Authorization + read-only query flow. The empty share set enforced
    /// inside [HealthKitManager.requestAuthorization] prevents any writes.
    @MainActor
    private func startImport() async {
        guard let manager = manager else {
            state = .unavailable
            return
        }
        state = .loading
        do {
            try await manager.requestAuthorization()
            let authorized = await manager.hasAuthorization()
            guard authorized else {
                state = .denied
                return
            }
            let totalSteps = try await manager.readStepCount(days: windowDays)
            let energyKcal = try await manager.readActiveEnergy(days: windowDays)
            let dailyAverage = windowDays > 0 ? totalSteps / windowDays : 0
            state = .success(
                dailyAverageSteps: dailyAverage,
                totalSteps: totalSteps,
                totalActiveEnergyKcal: energyKcal,
                activityLevel: HealthImportSection.estimateActivityLevel(dailyAverage),
                windowDays: windowDays
            )
        } catch {
            // Permission denial or query-failure surface as the denied state
            // so the user sees the retry button.
            state = .denied
        }
    }

    /// Coarse trailing-step average → activity level mapping, used as a
    /// TDEE recalibration hint.
    private static func estimateActivityLevel(_ dailyAverageSteps: Int) -> ActivityLevel {
        if dailyAverageSteps < 5_000 { return .sedentary }
        if dailyAverageSteps < 7_500 { return .lightlyActive }
        if dailyAverageSteps < 10_000 { return .moderatelyActive }
        return .veryActive
    }
}

#endif // canImport(SwiftUI) && canImport(HealthKit)
