//
//  ProgressDashboardView.swift
//  FeatureWorkout
//
//  Progress dashboard: weight trend chart, projected goal date, adherence
//  rate with badge (>= 85%), and weekly weigh-in prompt. Reads weigh-in
//  records and sessions from SwiftData.
//

#if canImport(SwiftUI) && canImport(SwiftData)

import SwiftUI
import SwiftData
import CoreDomain
import CoreData

public struct ProgressDashboardView: View {
    @Environment(\.modelContext) private var context
    @Query private var profiles: [UserProfileModel]
    @Query(sort: \WeightLogModel.loggedDate, order: .forward) private var weightLogs: [WeightLogModel]
    @Query(sort: \WorkoutSessionModel.scheduledDate, order: .reverse) private var sessions: [WorkoutSessionModel]

    @State private var weighInInput: String = ""
    @State private var showingWeighInSaved = false

    public init() {}

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if let profile = profiles.first {
                        weighInSection(profile: profile)
                        weightTrendChart
                        projectedGoalSection(profile: profile)
                        adherenceSection
                    } else {
                        Text("Complete onboarding to see your progress.")
                            .foregroundStyle(.secondary)
                    }
                }
                .padding()
            }
            .navigationTitle("Progress")
        }
    }

    // MARK: - Weigh-in

    private func weighInSection(profile: UserProfileModel) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Weekly Weigh-in")
                .font(.headline)
            HStack {
                TextField("Enter weight (kg)", text: $weighInInput)
                    .keyboardType(.decimalPad)
                    .textFieldStyle(.roundedBorder)
                Button("Save") {
                    saveWeighIn(userId: profile.id)
                }
                .buttonStyle(.borderedProminent)
                .disabled(weighInInput.isEmpty)
            }
            if showingWeighInSaved {
                Text("Weigh-in saved!")
                    .font(.caption)
                    .foregroundStyle(.green)
            }
            if let latest = weightLogs.last {
                Text("Last weigh-in: \(String(format: "%.1f", latest.weightKg)) kg on \(latest.loggedDate)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.secondary.opacity(0.10)))
    }

    // MARK: - Weight trend chart

    private var weightTrendChart: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Weight Trend")
                .font(.headline)
            if weightLogs.count < 2 {
                Text("Log at least 2 weigh-ins to see the trend.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                WeightTrendChartView(entries: weightLogs)
                    .frame(height: 180)
            }
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.secondary.opacity(0.10)))
    }

    // MARK: - Projected goal

    private func projectedGoalSection(profile: UserProfileModel) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Goal Projection")
                .font(.headline)
            if let target = profile.targetWeightKg,
               let firstWeight = weightLogs.last?.weightKg,
               firstWeight != target {
                let diff = firstWeight - target
                let direction = diff > 0 ? "lose" : "gain"
                let absDiff = abs(diff)
                Text("Goal: \(String(format: "%.1f", target)) kg")
                    .font(.callout)
                if weightLogs.count >= 2,
                   let first = weightLogs.first,
                   let last = weightLogs.last {
                    let daysBetween = daysBetweenDates(first.loggedDate, last.loggedDate)
                    if daysBetween > 0 {
                        let ratePerDay = (first.weightKg - last.weightKg) / Double(daysBetween)
                        if ratePerDay != 0 {
                            let daysToGoal = Int(absDiff / abs(ratePerDay))
                            let projectedDate = Calendar.current.date(byAdding: .day, value: daysToGoal, to: Date()) ?? Date()
                            let fmt = DateFormatter()
                            fmt.dateStyle = .medium
                            let ratePerWeek = abs(ratePerDay) * 7
                            Text("Current rate: \(String(format: "%.2f", ratePerWeek)) kg/week")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text("Projected goal date: \(fmt.string(from: projectedDate))")
                                .font(.callout)
                        } else {
                            Text("No weight change trend yet — keep logging weekly.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    } else {
                        Text("Need weigh-ins on different days to project a date.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    Text("Need at least 2 weigh-ins to \(direction) \(String(format: "%.1f", absDiff)) kg.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } else if profile.targetWeightKg != nil {
                Text("Log a weigh-in to see your goal projection.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                Text("No specific weight goal set.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.secondary.opacity(0.10)))
    }

    // MARK: - Adherence

    private var adherenceSection: some View {
        let (rate, completed, scheduled) = adherenceRate()
        return VStack(alignment: .leading, spacing: 8) {
            Text("Adherence (trailing 7 days)")
                .font(.headline)
            HStack {
                Text("\(completed) / \(scheduled) sessions completed")
                    .font(.callout)
                Spacer()
                Text(String(format: "%.0f%%", rate * 100))
                    .font(.title2.bold())
            }
            if rate >= 0.85 {
                Label("Great adherence!", systemImage: "flame.fill")
                    .foregroundStyle(.orange)
                    .font(.headline)
            }
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.secondary.opacity(0.10)))
    }

    // MARK: - Helpers

    private func saveWeighIn(userId: UUID) {
        guard let weight = Double(weighInInput) else { return }
        let repo = WeightLogRepository(context: context)
        let fmt = ISO8601DateFormatter()
        fmt.formatOptions = [.withFullDate]
        let today = fmt.string(from: Date())
        _ = try? repo.save(userId: userId, weightKg: weight, loggedDate: today)
        weighInInput = ""
        showingWeighInSaved = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            showingWeighInSaved = false
        }
    }

    /// Adherence rate over the trailing 7 days: completed / scheduled.
    private func adherenceRate() -> (rate: Double, completed: Int, scheduled: Int) {
        let cal = Calendar.current
        let now = Date()
        guard let weekAgo = cal.date(byAdding: .day, value: -7, to: now) else {
            return (0, 0, 0)
        }
        let fmt = ISO8601DateFormatter()
        fmt.formatOptions = [.withFullDate]
        let weekAgoStr = fmt.string(from: weekAgo)
        let nowStr = fmt.string(from: now)
        let recent = sessions.filter { $0.scheduledDate >= weekAgoStr && $0.scheduledDate <= nowStr }
        let scheduled = recent.count
        let completed = recent.filter { $0.status == SessionStatus.completed.rawValue }.count
        let rate = scheduled > 0 ? Double(completed) / Double(scheduled) : 0
        return (rate, completed, scheduled)
    }

    private func daysBetweenDates(_ d1: String, _ d2: String) -> Int {
        let fmt = ISO8601DateFormatter()
        fmt.formatOptions = [.withFullDate]
        guard let date1 = fmt.date(from: d1), let date2 = fmt.date(from: d2) else { return 0 }
        return Calendar.current.dateComponents([.day], from: date1, to: date2).day ?? 0
    }
}

// MARK: - Weight trend chart view

struct WeightTrendChartView: View {
    let entries: [WeightLogModel]

    var body: some View {
        GeometryReader { geo in
            if entries.count < 2 {
                EmptyView()
            } else {
                let weights = entries.map { $0.weightKg }
                let minW = weights.min() ?? 0
                let maxW = weights.max() ?? 1
                let range = max(maxW - minW, 1)
                let w = geo.size.width
                let h = geo.size.height
                let stepX = w / CGFloat(entries.count - 1)

                ZStack {
                    // Stroke path
                    Path { p in
                        for (i, entry) in entries.enumerated() {
                            let x = CGFloat(i) * stepX
                            let y = h - CGFloat((entry.weightKg - minW) / range) * h
                            if i == 0 {
                                p.move(to: CGPoint(x: x, y: y))
                            } else {
                                p.addLine(to: CGPoint(x: x, y: y))
                            }
                        }
                    }
                    .stroke(Color.accentColor, lineWidth: 2)

                    // Fill path below line
                    Path { p in
                        for (i, entry) in entries.enumerated() {
                            let x = CGFloat(i) * stepX
                            let y = h - CGFloat((entry.weightKg - minW) / range) * h
                            if i == 0 {
                                p.move(to: CGPoint(x: x, y: y))
                            } else {
                                p.addLine(to: CGPoint(x: x, y: y))
                            }
                        }
                        p.addLine(to: CGPoint(x: w, y: h))
                        p.addLine(to: CGPoint(x: 0, y: h))
                        p.closeSubpath()
                    }
                    .fill(Color.accentColor.opacity(0.12))

                    // Data points
                    ForEach(Array(entries.enumerated()), id: \.offset) { i, entry in
                        let x = CGFloat(i) * stepX
                        let y = h - CGFloat((entry.weightKg - minW) / range) * h
                        Circle()
                            .fill(Color.accentColor)
                            .frame(width: 6, height: 6)
                            .position(x: x, y: y)
                    }
                }
            }
        }
    }
}

#endif // canImport(SwiftUI) && canImport(SwiftData)
