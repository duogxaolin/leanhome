//
//  WorkoutDetailView.swift
//  FeatureWorkout
//
//  Per-session detail: planned exercises, start session, link to player +
//  post-session check-in.
//

#if canImport(SwiftUI) && canImport(SwiftData)

import SwiftUI
import SwiftData
import CoreDomain
import CoreData
import Media

public struct WorkoutDetailView: View {
    @Environment(\.modelContext) private var context
    @Query private var exerciseModels: [ExerciseModel]

    let session: WorkoutSession

    @State private var showPlayer = false
    @State private var playerViewModel: WorkoutPlayerViewModel?
    @State private var showCheckIn = false

    public init(session: WorkoutSession) { self.session = session }

    public var body: some View {
        List {
            Section {
                HStack {
                    Text(session.splitName.displayName).font(.headline)
                    Spacer()
                    Text(session.scheduledDate).foregroundStyle(.secondary)
                }
            }
            Section("Planned exercises") {
                ForEach(Array(session.exercises.enumerated()), id: \.offset) { _, planned in
                    let exercise = exerciseModels.first { $0.id == planned.exerciseId }?.toDomain()
                    HStack {
                        VStack(alignment: .leading) {
                            Text(exercise?.nameEn ?? planned.exerciseId.replacingOccurrences(of: "_", with: " "))
                                .font(.body)
                            if let diff = exercise?.difficultyLevel {
                                Text(diff.displayName)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        Text("\(planned.plannedSets)×\(planned.plannedReps)")
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                }
            }
            Section {
                Button {
                    beginPlayer()
                } label: {
                    Label("Start session", systemImage: "play.fill")
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .navigationTitle("Session")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showPlayer) {
            if let vm = playerViewModel {
                WorkoutPlayerView(session: session, catalog: catalogDomain) { finishedVM in
                    self.playerViewModel = finishedVM
                    self.showPlayer = false
                    self.showCheckIn = true
                }
            } else {
                WorkoutPlayerView(session: session, catalog: catalogDomain) { finishedVM in
                    self.playerViewModel = finishedVM
                    self.showPlayer = false
                    self.showCheckIn = true
                }
            }
        }
        .sheet(isPresented: $showCheckIn) {
            if let vm = playerViewModel {
                SessionCheckInView(session: session, viewModel: vm)
            }
        }
    }

    private var catalogDomain: [Exercise] {
        exerciseModels.compactMap { $0.toDomain() }
    }

    private func beginPlayer() {
        showPlayer = true
    }
}

// MARK: - Session history list

public struct SessionHistoryView: View {
    @Query(sort: \WorkoutSessionModel.scheduledDate, order: .reverse) private var sessions: [WorkoutSessionModel]

    public init() {}

    public var body: some View {
        NavigationStack {
            List {
                let completed = sessions.filter { $0.status == SessionStatus.completed.rawValue }
                if completed.isEmpty {
                    Text("No completed sessions yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(completed) { model in
                        let session = model.toDomain()
                        NavigationLink {
                            WorkoutDetailView(session: session)
                        } label: {
                            VStack(alignment: .leading) {
                                Text(session.splitName.displayName).font(.headline)
                                Text(session.scheduledDate).font(.caption).foregroundStyle(.secondary)
                                if let rpe = session.rpeScore {
                                    Text("RPE \(rpe)").font(.caption2).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("History")
        }
    }
}

#endif // canImport(SwiftUI) && canImport(SwiftData)
