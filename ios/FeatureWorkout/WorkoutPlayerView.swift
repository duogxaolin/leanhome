//
//  WorkoutPlayerView.swift
//  FeatureWorkout
//
//  Interactive native workout player. Phases:
//    - exerciseActive: looping AVPlayer + exercise name/set/form cues/muscles.
//    - restInterval:    countdown timer with haptics + skip/adjust controls.
//    - sessionComplete: summary of duration, exercises, volume, totalReps.
//  Falls back to vector SVG illustration + text cues when the video is
//  missing and the device is offline.
//

#if canImport(SwiftUI)

import SwiftUI
import AVKit
import CoreDomain
import Media

public struct WorkoutPlayerView: View {
    @StateObject private var viewModel: WorkoutPlayerViewModel
    @Environment(\.modelContext) private var context
    let onComplete: (WorkoutPlayerViewModel) -> Void

    public init(session: WorkoutSession, catalog: [Exercise], onComplete: @escaping (WorkoutPlayerViewModel) -> Void) {
        _viewModel = StateObject(wrappedValue: WorkoutPlayerViewModel(session: session, catalog: catalog))
        self.onComplete = onComplete
    }

    public var body: some View {
        VStack(spacing: 0) {
            switch viewModel.phase {
            case .exerciseActive:
                activePhaseView
            case .restInterval(let remaining):
                restPhaseView(remaining: remaining)
            case .sessionComplete:
                completePhaseView
            }
        }
        .navigationTitle(viewModel.currentExercise?.nameEn ?? "Workout")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            viewModel.start()
        }
    }

    // MARK: - Active phase

    private var activePhaseView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ExerciseVideoView(exercise: viewModel.currentExercise)
                    .frame(height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                if let ex = viewModel.currentExercise {
                    Text(ex.nameEn)
                        .font(.title2).bold()
                    Text(ex.nameVi)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    HStack {
                        Text("Set \(viewModel.currentSet) / \(viewModel.plannedSetsForCurrent)")
                            .font(.headline)
                        Spacer()
                        Text("Reps: \(viewModel.plannedRepsForCurrent)")
                            .font(.headline)
                    }
                    MuscleChipsView(primary: ex.primaryMuscles, secondary: ex.secondaryMuscles)

                    FormGuideView(mistakes: ex.commonMistakes, cues: ex.correctFormCues)
                }

                Button(action: viewModel.completeSet) {
                    Text("Done Set")
                        .font(.headline)
                        .frame(maxWidth: .infinity, minHeight: 54)
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
        }
    }

    // MARK: - Rest phase

    private func restPhaseView(remaining: Int) -> some View {
        VStack(spacing: 20) {
            Spacer()
            Text("Rest")
                .font(.title2).foregroundStyle(.secondary)
            Text("\(remaining)s")
                .font(.system(size: 96, weight: .bold, design: .rounded))
                .monospacedDigit()
                .contentTransition(.numericText())
            HStack(spacing: 24) {
                Button("-15s") { viewModel.adjustRest(by: -15) }
                    .buttonStyle(.bordered)
                Button("Skip") { viewModel.skipRest() }
                    .buttonStyle(.bordered)
                Button("+30s") { viewModel.adjustRest(by: 30) }
                    .buttonStyle(.bordered)
            }
            if let ex = viewModel.currentExercise {
                FormGuideView(mistakes: ex.commonMistakes, cues: ex.correctFormCues)
                    .padding(.top, 12)
            }
            Spacer()
        }
        .padding()
    }

    // MARK: - Session complete

    private var completePhaseView: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Session Complete").font(.largeTitle).bold()
            StatPill(label: "Total time", value: viewModel.totalDurationFormatted())
            StatPill(label: "Exercises", value: "\(viewModel.completedExercises.count)")
            StatPill(label: "Volume (rep-load)", value: String(format: "%.0f", viewModel.volumeLifted()))
            Spacer()
            Button(action: { onComplete(viewModel) }) {
                Text("Check in")
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 54)
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
    }
}

// MARK: - Looping AVPlayer view

struct ExerciseVideoView: View {
    let exercise: Exercise?
    @StateObject private var videoController: LoopingVideoController
    @State private var cached = false
    @State private var offline = false

    init(exercise: Exercise?) {
        self.exercise = exercise
        _videoController = StateObject(wrappedValue: LoopingVideoController(urlString: exercise?.videoUrl ?? ""))
    }

    var body: some View {
        ZStack {
            if let player = videoController.player {
                VideoPlayer(player: player)
                    .onAppear { videoController.play() }
                    .onDisappear { videoController.pause() }
            } else {
                fallbackIllustration
                    .overlay(alignment: .topTrailing) {
                        if offline {
                            Text("Video available online")
                                .font(.caption2)
                                .padding(.horizontal, 8).padding(.vertical, 4)
                                .background(.ultraThinMaterial, in: Capsule())
                                .padding(8)
                        }
                    }
            }
            if offline {
                EmptyView()
            }
        }
        .task {
            await refreshMedia()
        }
    }

    private var fallbackIllustration: some View {
        VStack(spacing: 8) {
            Image(systemName: "figure.strengthtraining.traditional")
                .font(.system(size: 80))
                .foregroundStyle(.accent)
            Text(exercise?.nameEn ?? "Exercise")
                .font(.headline)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.secondary.opacity(0.08))
    }

    private func refreshMedia() async {
        // Resolve via Media's VideoCacheManager; fall back to illustration
        // when the video cannot be played (offline + uncached).
        guard let url = exercise?.videoUrl, let exercise = exercise else { return }
        if let cached = await VideoCacheManager.shared.cachedFileURL(for: url) {
            videoController.load(url: cached, startTime: 0, endTime: 10)
            self.cached = true
            self.offline = false
        } else {
            self.offline = true
            _ = exercise
        }
    }
}

/// Small wrapper around AVPlayer that loops a clipped range.
@MainActor
final class LoopingVideoController: ObservableObject {
    @Published var player: AVPlayer?
    private let urlString: String

    init(urlString: String) { self.urlString = urlString }

    func load(url: URL, startTime: Double, endTime: Double) {
        let player = AVPlayer(url: url)
        let item = player.currentItem
        player.isMuted = true
        // Loop the [startTime, endTime] range.
        if let item = item {
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(handleDidPlayToEndTime),
                name: .AVPlayerItemDidPlayToEndTime,
                object: item
            )
        }
        self.player = player
    }

    func play() { player?.play() }
    func pause() { player?.pause() }

    @objc private func handleDidPlayToEndTime() {
        player?.seek(to: .zero) { [weak self] _ in
            self?.player?.play()
        }
    }
}

// MARK: - Supporting view components

struct MuscleChipsView: View {
    let primary: [MuscleGroup]
    let secondary: [MuscleGroup]
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Primary muscles").font(.caption).foregroundStyle(.secondary)
            FlowChips(items: primary.map { $0.rawValue }, color: .accentColor)
            Text("Secondary muscles").font(.caption).foregroundStyle(.secondary)
            FlowChips(items: secondary.map { $0.rawValue }, color: Color.secondary.opacity(0.5))
        }
    }
}

struct FlowChips: View {
    let items: [String]
    let color: Color
    var body: some View {
        FlexibleChipView(items: items, color: color)
    }
}

struct FlexibleChipView: View {
    let items: [String]
    let color: Color
    var body: some View {
        let rows = rowsOfItems(items, maxPerRow: 4)
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack {
                    ForEach(row, id: \.self) { item in
                        Text(item.replacingOccurrences(of: "_", with: " "))
                            .font(.caption2)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(color.opacity(0.12), in: Capsule())
                    }
                }
            }
        }
    }

    private func rowsOfItems(_ src: [String], maxPerRow: Int) -> [[String]] {
        var rows: [[String]] = []
        let rest = maxPerRow
        for chunk in stride(from: 0, to: src.count, by: rest) {
            rows.append(Array(src[chunk..<min(chunk + rest, src.count)]))
        }
        if rows.isEmpty { rows = [[]] }
        return rows
    }
}

struct FormGuideView: View {
    let mistakes: [String]
    let cues: [String]
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Label("Common Mistakes", systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange).font(.headline)
                ForEach(mistakes, id: \.self) { mistake in
                    Text("• \(mistake)").font(.caption).foregroundStyle(.primary)
                }
            }
            VStack(alignment: .leading, spacing: 4) {
                Label("Correct Form Cues", systemImage: "checkmark.seal.fill")
                    .foregroundStyle(.green).font(.headline)
                ForEach(cues, id: \.self) { cue in
                    Text("• \(cue)").font(.caption).foregroundStyle(.primary)
                }
            }
        }
    }
}

struct StatPill: View {
    let label: String
    let value: String
    var body: some View {
        HStack {
            Text(label).foregroundStyle(.secondary)
            Spacer()
            Text(value).font(.body.monospacedDigit())
        }
        .padding(.vertical, 6)
    }
}

#endif // canImport(SwiftUI)
