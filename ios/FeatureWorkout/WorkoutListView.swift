//
//  WorkoutListView.swift
//  FeatureWorkout
//
//  Lists upcoming + completed workout sessions and a "generate now" entry.
//

#if canImport(SwiftUI) && canImport(SwiftData)

import SwiftUI
import SwiftData
import CoreDomain
import CoreData

public struct WorkoutListView: View {
    @Environment(\.modelContext) private var context
    @Query private var profiles: [UserProfileModel]
    @Query private var exercises: [ExerciseModel]
    @Query(sort: \WorkoutSessionModel.scheduledDate, order: .reverse) private var sessions: [WorkoutSessionModel]

    @State private var showingGenerated = false
    @State private var generatedPlan: WeeklyWorkoutPlan?
    @State private var generatedSessionsToPersist: [WorkoutSession] = []

    public init() {}

    public var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        generatePlan()
                    } label: {
                        Label("Generate this week's plan", systemImage: "waveform.path.ecg")
                    }
                }
                if !generatedSessionsToPersist.isEmpty {
                    Section("Upcoming") {
                        ForEach(generatedSessionsToPersist) { session in
                            NavigationLink {
                                WorkoutDetailView(session: session)
                            } label: {
                                SessionRow(session: session,
                                           exerciseNames: sessionExerciseNames(session))
                            }
                        }
                    }
                }
                Section("History") {
                    let domainSessions = sessions.map { $0.toDomainBuilder() }
                    ForEach(domainSessions) { session in
                        SessionRow(session: session, exerciseNames: [])
                    }
                }
            }
            .navigationTitle("Workouts")
            .sheet(isPresented: $showingGenerated) {
                if let plan = generatedPlan {
                    GeneratedPlanSummaryView(plan: plan)
                }
            }
        }
    }

    private func generatePlan() {
        guard let profileModel = profiles.first else { return }
        let profile = profileModel.toDomain()
        let catalog = exercises.compactMap { $0.toDomain() }
        let plan = WorkoutGenerator.generateWeeklyPlan(catalog: catalog, profile: profile)
        generatedPlan = plan
        // Build domain sessions for persistence/preview.
        let today = Date()
        var built: [WorkoutSession] = []
        for (i, planned) in plan.sessions.enumerated() {
            let date = Calendar.current.date(byAdding: .day, value: i, to: today) ?? today
            let iso = ISO8601DateFormatter.calendarFormatter.string(from: date)
            built.append(WorkoutSession(
                userId: profile.id,
                scheduledDate: iso,
                splitName: planned.splitSession,
                status: .pending,
                exercises: planned.exercises
            ))
        }
        generatedSessionsToPersist = built
        let repo = WorkoutSessionRepository(context: context)
        for s in built {
            _ = try? repo.save(s)
        }
        showingGenerated = true
    }

    /// Resolve exercise display names for a session's planned exercises.
    private func sessionExerciseNames(_ session: WorkoutSession) -> [String] {
        let byID = Dictionary(uniqueKeysWithValues: exercises.compactMap { m -> (String, String)?? in
            (m.id, m.nameEn)
        }.compactMap { $0 }, uniquingKeysWith: { a, _ in a })
        return session.exercises.compactMap { byID[$0.exerciseId] }
    }
}

struct SessionRow: View {
    let session: WorkoutSession
    let exerciseNames: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(session.splitName.displayName)
                    .font(.headline)
                Spacer()
                statusBadge(session.status)
            }
            Text(session.scheduledDate)
                .font(.caption)
                .foregroundStyle(.secondary)
            if !exerciseNames.isEmpty {
                Text(exerciseNames.prefix(4).joined(separator: " · "))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
    }

    @ViewBuilder
    private func statusBadge(_ status: SessionStatus) -> some View {
        switch status {
        case .pending:   Text("Pending").foregroundStyle(.orange)
        case .completed: Text("Done").foregroundStyle(.green)
        case .skipped:   Text("Skipped").foregroundStyle(.red)
        }
    }
}

struct GeneratedPlanSummaryView: View {
    let plan: WeeklyWorkoutPlan
    var body: some View {
        NavigationStack {
            List {
                Section("Split") { Text(plan.splitType.description) }
                Section("Sessions") {
                    ForEach(plan.sessions) { session in
                        VStack(alignment: .leading) {
                            Text(session.splitSession.displayName).font(.headline)
                            Text("\(session.exercises.count) exercises").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("This Week")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

extension WorkoutSessionModel {
    func toDomainBuilder() -> WorkoutSession { toDomain() }
}

// ISO8601 date helper (reuse a single configured formatter).
extension ISO8601DateFormatter {
    static let calendarFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withFullDate]
        return f
    }()
}

#endif // canImport(SwiftUI) && canImport(SwiftData)
