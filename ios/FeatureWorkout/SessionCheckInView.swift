//
//  SessionCheckInView.swift
//  FeatureWorkout
//
//  Post-workout check-in: RPE 1–10, optional feeling tags, actual sets/reps
//  vs. plan, free-form notes. Persists the result to SwiftData.
//

#if canImport(SwiftUI) && canImport(SwiftData)

import SwiftUI
import SwiftData
import CoreDomain
import CoreData
import Media

public struct SessionCheckInView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let session: WorkoutSession
    let viewModel: WorkoutPlayerViewModel

    @State private var rpe: Int = 7
    @State private var selectedTags: Set<FeelingTag> = []
    @State private var actualSetsByIndex: [Int] = []
    @State private var notes: String = ""
    @State private var saving = false

    public init(session: WorkoutSession, viewModel: WorkoutPlayerViewModel) {
        self.session = session
        self.viewModel = viewModel
        var initial: [Int] = []
        for ex in session.exercises { initial.append(ex.plannedSets) }
        self._actualSetsByIndex = State(initialValue: initial)
    }

    public var body: some View {
        NavigationStack {
            Form {
                Section("Rate of Perceived Exertion") {
                    VStack(alignment: .leading) {
                        HStack {
                            Text("\(rpe)")
                                .font(.system(size: 56, weight: .bold, design: .rounded))
                                .monospacedDigit()
                            Text(rpeLabel)
                                .font(.callout)
                                .foregroundStyle(.secondary)
                        }
                        Slider(value: Binding(
                            get: { Double(rpe) },
                            set: { rpe = Int($0) }
                        ), in: 1...10, step: 1)
                        HStack {
                            Text("Very light").font(.caption).foregroundStyle(.secondary)
                            Spacer()
                            Text("Max effort").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                Section("How did it feel?") {
                    feelingTagsRow
                }
                Section("Actual vs planned") {
                    ForEach(Array(session.exercises.enumerated()), id: \.offset) { idx, exercise in
                        HStack {
                            Text(exercise.exerciseId.replacingOccurrences(of: "_", with: " "))
                                .font(.caption)
                                .lineLimit(1)
                            Spacer()
                            Stepper("\(actualSetsByIndex[idx]) / \(exercise.plannedSets)",
                                    value: Binding(
                                        get: { actualSetsByIndex[idx] },
                                        set: { actualSetsByIndex[idx] = $0 }
                                    ),
                                    in: 0...exercise.plannedSets)
                                .labelsHidden()
                            Text("\(actualSetsByIndex[idx]) / \(exercise.plannedSets)")
                                .monospacedDigit()
                                .font(.callout)
                        }
                    }
                }
                Section("Notes") {
                    TextEditor(text: $notes)
                        .frame(minHeight: 80)
                }
                Section {
                    Button(action: saveCheckIn) {
                        HStack {
                            if saving { ProgressView() } else {
                                Text("Save check-in")
                                    .font(.headline)
                            }
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .disabled(saving)
                }
            }
            .navigationTitle("Session Check-in")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private var rpeLabel: String {
        switch rpe {
        case 1...2: return "Very light"
        case 3...4: return "Easy"
        case 5:     return "Moderate"
        case 6:     return "Somewhat hard"
        case 7:     return "Challenging but controlled"
        case 8:     return "Hard"
        case 9:     return "Very hard"
        case 10:    return "Maximum effort / failure"
        default:    return ""
        }
    }

    private var feelingTagsRow: some View {
        FlowChips(selectedTags: $selectedTags)
    }

    private func saveCheckIn() {
        saving = true
        Task { @MainActor in
            do {
                let repo = WorkoutSessionRepository(context: context)
                var actualSessions: [SessionExercise] = []
                for (idx, ex) in session.exercises.enumerated() {
                    actualSessions.append(SessionExercise(
                        exerciseId: ex.exerciseId,
                        plannedSets: ex.plannedSets,
                        plannedReps: ex.plannedReps,
                        plannedRestSeconds: ex.plannedRestSeconds,
                        actualSetsCompleted: idx < actualSetsByIndex.count ? actualSetsByIndex[idx] : ex.plannedSets,
                        actualRepsCompleted: nil
                    ))
                }
                _ = try repo.update(
                    session: session.id,
                    rpe: rpe,
                    feelingTags: Array(selectedTags),
                    notes: notes.isEmpty ? nil : notes,
                    actualSets: actualSessions,
                    completedDurationSeconds: viewModel.elapsedSeconds
                )
                saving = false
                dismiss()
            } catch {
                saving = false
            }
        }
    }
}

// MARK: - Multi-select feeling-tag chips

struct FlowChips: View {
    @Binding var selectedTags: Set<FeelingTag>
    var body: some View {
        FlexibleChipSelection(items: FeelingTag.allCases, selected: $selectedTags)
    }
}

struct FlexibleChipSelection<Item: Hashable & Identifiable & Equatable>: View {
    let items: [Item]
    @Binding var selected: Set<Item>
    var body: some View {
        let rows = rowsOfItems(items, maxPerRow: 3)
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack {
                    ForEach(row, id: \.id) { item in
                        let isOn = selected.contains(item)
                        Button(action: {
                            if isOn { selected.remove(item) } else { selected.insert(item) }
                        }) {
                            Text(label(for: item))
                                .font(.caption)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(isOn ? Color.accentColor.opacity(0.85) : Color.secondary.opacity(0.12),
                                            in: Capsule())
                                .foregroundStyle(isOn ? .white : .primary)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func rowsOfItems(_ src: [Item], maxPerRow: Int) -> [[Item]] {
        var rows: [[Item]] = []
        for chunk in stride(from: 0, to: src.count, by: maxPerRow) {
            rows.append(Array(src[chunk..<min(chunk + maxPerRow, src.count)]))
        }
        return rows
    }

    private func label(for item: Item) -> String {
        if let tag = item as? FeelingTag { return tag.emoji + " " + tag.displayName }
        return String(describing: item)
    }
}

#endif // canImport(SwiftUI) && canImport(SwiftData)
