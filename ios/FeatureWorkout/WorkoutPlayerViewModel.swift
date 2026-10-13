//
//  WorkoutPlayerViewModel.swift
//  FeatureWorkout
//
//  State machine for a workout session lifecycle:
//  EXERCISE_ACTIVE → REST_INTERVAL → (next exercise / SESSION_COMPLETE).
//  Drives an AVPlayer for looping demonstration videos and triggers
//  haptics on the rest countdown.
//

#if canImport(SwiftUI)

import SwiftUI
import Combine
import CoreDomain
import Media

/// Interactive state of the player.
public enum PlayerPhase: Equatable, Sendable {
    case exerciseActive
    case restInterval(remaining: Int)
    case sessionComplete
}

@MainActor
public final class WorkoutPlayerViewModel: ObservableObject {

    @Published public var phase: PlayerPhase = .exerciseActive
    @Published public var currentExerciseIndex: Int = 0
    @Published public var currentSet: Int = 1
    @Published public var elapsedSeconds: Int = 0
    @Published public var mediaStatus: MediaStatus = .checking
    @Published public var completedExercises: [CompletedExercise] = []

    public let session: WorkoutSession
    public let catalog: [Exercise]
    private var restTimer: Timer?
    private var elapsedTimer: Timer?

    public init(session: WorkoutSession, catalog: [Exercise]) {
        self.session = session
        self.catalog = catalog
    }

    public var currentExercise: Exercise? {
        guard currentExerciseIndex < session.exercises.count else { return nil }
        let id = session.exercises[currentExerciseIndex].exerciseId
        return catalog.first { $0.id == id }
    }

    public var currentSessionExercise: SessionExercise? {
        guard currentExerciseIndex < session.exercises.count else { return nil }
        return session.exercises[currentExerciseIndex]
    }

    public var totalExercises: Int { session.exercises.count }
    public var plannedSetsForCurrent: Int { currentSessionExercise?.plannedSets ?? 0 }
    public var plannedRepsForCurrent: Int { currentSessionExercise?.plannedReps ?? 0 }

    // MARK: - Lifecycle

    public func start() {
        phase = .exerciseActive
        currentExerciseIndex = 0
        currentSet = 1
        elapsedSeconds = 0
        completedExercises = []
        startElapsedTimer()
    }

    /// Record the just-completed set, then either rest or move on.
    public func completeSet() {
        let plannedSets = plannedSetsForCurrent
        if currentSet < plannedSets {
            // Enter rest, advance set after.
            beginRest(seconds: currentSessionExercise?.plannedRestSeconds ?? 60)
            currentSet += 1
        } else {
            // Done with this exercise → record, advance.
            recordCurrentExercise()
            if currentExerciseIndex + 1 < totalExercises {
                currentExerciseIndex += 1
                currentSet = 1
                beginRest(seconds: currentSessionExercise?.plannedRestSeconds ?? 60)
            } else {
                finishSession()
            }
        }
    }

    /// Skip the rest interval.
    public func skipRest() {
        restTimer?.invalidate()
        phase = .exerciseActive
    }

    /// Adjust rest by ± seconds (rest must be ≥ 0).
    public func adjustRest(by seconds: Int) {
        guard case .restInterval(let remaining) = phase else { return }
        let newRemaining = max(0, remaining + seconds)
        phase = .restInterval(remaining: newRemaining)
    }

    // MARK: - Internal

    private func beginRest(seconds: Int) {
        restTimer?.invalidate()
        phase = .restInterval(remaining: seconds)
        restTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] timer in
            Task { @MainActor in
                guard let self else { timer.invalidate(); return }
                guard case .restInterval(let remaining) = self.phase else {
                    timer.invalidate()
                    return
                }
                if remaining <= 1 {
                    timer.invalidate()
                    self.phase = .exerciseActive
                    self.fireFinalHapticIfNeeded(wasLast: remaining == 0)
                } else {
                    self.phase = .restInterval(remaining: remaining - 1)
                    if remaining <= 4 {
                        self.fireTickHaptic()
                    }
                }
            }
        }
    }

    private func fireTickHaptic() {
        #if canImport(UIKit)
        let g = UIImpactFeedbackGenerator(style: .light)
        g.impactOccurred()
        #endif
    }

    private func fireFinalHapticIfNeeded(wasLast: Bool) {
        #if canImport(UIKit)
        let g = UINotificationFeedbackGenerator()
        g.notificationOccurred(.success)
        #endif
    }

    private func recordCurrentExercise() {
        if let ex = currentExercise, let sx = currentSessionExercise {
            completedExercises.append(CompletedExercise(
                exerciseId: sx.exerciseId,
                exerciseName: ex.nameEn,
                setsCompleted: sx.plannedSets,
                repsPerSet: sx.plannedReps,
                restSeconds: sx.plannedRestSeconds
            ))
        }
    }

    private func finishSession() {
        elapsedTimer?.invalidate()
        restTimer?.invalidate()
        phase = .sessionComplete
    }

    private func startElapsedTimer() {
        elapsedTimer?.invalidate()
        elapsedTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.elapsedSeconds += 1
            }
        }
    }

    public func totalDurationFormatted() -> String {
        let m = elapsedSeconds / 60
        let s = elapsedSeconds % 60
        return String(format: "%d:%02d", m, s)
    }

    public func volumeLifted() -> Double {
        // Estimation: not all exercises have explicit load. Sum rep-count for
        // progress; real load tracking requires weight per exercise.
        Double(completedExercises.reduce(0) { $0 + $1.setsCompleted * $1.repsPerSet })
    }
}

// MARK: - Support types

public struct CompletedExercise: Hashable, Sendable {
    public let exerciseId: String
    public let exerciseName: String
    public let setsCompleted: Int
    public let repsPerSet: Int
    public let restSeconds: Int
}

public enum MediaStatus: Equatable, Sendable {
    case checking
    case cached
    case streaming
    case offlineFallback
}

#endif // canImport(SwiftUI)
