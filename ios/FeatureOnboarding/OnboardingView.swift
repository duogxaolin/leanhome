//
//  OnboardingView.swift
//  FeatureOnboarding
//
//  Multi-step onboarding flow:
//    1 Biometric → 2 BMI gauge → 3 Archetype → 4 Goal →
//    5 Equipment & injuries → 5b Exercise selection → 6 Schedule → 7 Timeline.
//

import SwiftUI
import SwiftData
import CoreData
import CoreDomain

public struct OnboardingView: View {
    @StateObject private var viewModel = OnboardingViewModel()
    @Query private var exerciseModels: [ExerciseModel]
    @State private var showingCameraScan = false
    private let onComplete: (UserProfile, Set<String>) -> Void

    /// Inferred energy result, recomputed when relevant fields change.
    private var energyResult: EnergyBalanceResult { viewModel.assessment() }

    public init(onComplete: @escaping (UserProfile, Set<String>) -> Void) {
        self.onComplete = onComplete
    }

    public var body: some View {
        TabView {
            biometricStep
            bmiGaugeStep
            archetypeStep
            goalStep
            equipmentStep
            exerciseSelectionStep
            scheduleStep
            timelineStep
        }
        .tabViewStyle(.page(indexDisplayMode: .always))
        .indexViewStyle(.page(backgroundDisplayMode: .always))
        .padding(.top, 12)
        .sheet(isPresented: $showingCameraScan) {
            // V2 on-device Camera Body Scan. Dismisses silently on cancel,
            // permission-denied or insufficient landmarks; the manual grid
            // below remains usable as the fallback per the spec.
            CameraScanView(
                gender: viewModel.gender,
                onScanned: { shape in
                    viewModel.bodyShape = shape
                    showingCameraScan = false
                },
                onCancel: { showingCameraScan = false },
            )
        }
    }

    // MARK: 1 — Biometric

    private var biometricStep: some View {
        OnboardingStepCard(title: "Let's start", subtitle: "Your biometric profile") {
            TextField("Name (1–50 chars)", text: $viewModel.name)
            Stepper("Age: \(viewModel.age)", value: $viewModel.age, in: 13...99)
            Picker("Gender", selection: $viewModel.gender) {
                ForEach(Gender.allCases) { g in Text(g.displayName).tag(g) }
            }
            .pickerStyle(.segmented)
            HStack {
                Text("Height")
                Slider(value: $viewModel.heightCm, in: 100...250, step: 0.5)
                Text("\(Int(viewModel.heightCm)) cm")
            }
            HStack {
                Text("Weight")
                Slider(value: $viewModel.weightKg, in: 30...300, step: 0.5)
                Text(String(format: "%.1f kg", viewModel.weightKg))
            }
            TextField("Country (ISO 2)", text: $viewModel.countryCode)
                .textInputAutocapitalization(.characters)
            Picker("Activity level", selection: $viewModel.activityLevel) {
                ForEach(ActivityLevel.allCases) { a in Text(a.displayName).tag(a) }
            }
            ValidationErrorList(viewModel.validateBiometric())
        }
    }

    // MARK: 2 — BMI gauge

    private var bmiGaugeStep: some View {
        let cls = viewModel.bmiClassification
        return OnboardingStepCard(title: "Your BMI", subtitle: "\(cls.0.rawValue)") {
            BMICompassView(bmi: viewModel.bmiValue)
            Text("\(cls.2)")
                .font(.headline)
            Text("Weight \(Int(viewModel.weightKg)) kg · Height \(Int(viewModel.heightCm)) cm")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: 3 — Archetype

    private var archetypeStep: some View {
        OnboardingStepCard(title: "Body shape", subtitle: "Choose your archetype") {
            // V2 — optional on-device camera body scan. All processing
            // happens locally; nothing is uploaded. Falls back to the
            // grid below on cancel / permission-denied / insufficient
            // landmarks per spec.
            Button(action: { showingCameraScan = true }) {
                Label("Scan My Body", systemImage: "camera.viewfinder")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.accentColor.opacity(0.18)))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.accentColor.opacity(0.55), lineWidth: 1.5))
            }
            .buttonStyle(.plain)
            .padding(.bottom, 8)
            let options = BodyShape.options(for: viewModel.gender)
            LazyVGrid(columns: [.init(.adaptive(minimum: 140), spacing: 16)], spacing: 16) {
                ForEach(options, id: \.self) { shape in
                    let isOn = viewModel.bodyShape == shape
                    Button(action: { viewModel.bodyShape = shape }) {
                        VStack(spacing: 8) {
                            Image(systemName: archetypeIcon(shape))
                                .font(.system(size: 42))
                                .foregroundStyle(isOn ? .white : .primary)
                            Text(shape.displayName)
                                .font(.callout)
                        }
                        .frame(maxWidth: .infinity, minHeight: 110)
                        .background(RoundedRectangle(cornerRadius: 16).fill(isOn ? Color.accentColor.opacity(0.85) : Color.secondary.opacity(0.10)))
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(isOn ? Color.accentColor : .clear, lineWidth: 2))
                    }
                    .buttonStyle(.plain)
                }
            }
            ValidationErrorList(viewModel.validateArchetype())
        }
    }

    // MARK: 4 — Goal

    private var goalStep: some View {
        OnboardingStepCard(title: "Your goal", subtitle: "What are you training for?") {
            ForEach(FitnessGoal.allCases) { goal in
                let isOn = viewModel.fitnessGoal == goal
                Toggle(isOn: Binding(get: { isOn }, set: { _ in viewModel.fitnessGoal = goal })) {
                    Text(goal.displayName)
                }
                .toggleStyle(.button)
                .padding(.vertical, 2)
            }
            if viewModel.fitnessGoal == .targetZoneToning {
                Picker("Target muscle", selection: Binding(get: {
                    viewModel.targetMuscleGroup ?? .chest
                }, set: { viewModel.targetMuscleGroup = $0 })) {
                    ForEach(TargetMuscleGroup.allCases) { m in Text(m.displayName).tag(m) }
                }
            }
            ValidationErrorList(viewModel.validateGoal())
        }
    }

    // MARK: 5 — Equipment & injuries

    private var equipmentStep: some View {
        OnboardingStepCard(title: "Equipment & injuries", subtitle: "Filter your workouts") {
            Text("Available equipment").font(.headline)
            ForEach(Equipment.allCases) { e in
                Toggle(e.displayName, isOn: Binding(
                    get: { viewModel.availableEquipment.contains(e) },
                    set: { isOn in
                        viewModel.availableEquipment = isOn
                            ? viewModel.availableEquipment.union([e])
                            : viewModel.availableEquipment.subtracting([e])
                    }
                ))
            }
            Divider().padding(.vertical, 6)
            Text("Injuries / painful joints").font(.headline)
            ForEach(JointFlag.allCases) { j in
                Toggle(j.displayName, isOn: Binding(
                    get: { viewModel.injuryFlags.contains(j) },
                    set: { isOn in
                        viewModel.injuryFlags = isOn
                            ? viewModel.injuryFlags.union([j])
                            : viewModel.injuryFlags.subtracting([j])
                    }
                ))
            }
            ValidationErrorList(viewModel.validateEquipment())
        }
    }

    // MARK: 5b — Exercise selection

    private var exerciseSelectionStep: some View {
        OnboardingStepCard(title: "Exercise preferences", subtitle: "Tap to exclude any exercise you don't want") {
            let catalog = exerciseModels.compactMap { $0.toDomain() }
            let filtered = WorkoutGenerator.selectableExercises(
                catalog: catalog,
                availableEquipment: Array(viewModel.availableEquipment),
                injuries: Array(viewModel.injuryFlags)
            )
            if filtered.isEmpty {
                Text("No exercises match your equipment and injury selections. Adjust on the previous step.")
                    .foregroundStyle(.orange)
                    .font(.callout)
            } else {
                ForEach(filtered, id: \.id) { exercise in
                    let isExcluded = viewModel.excludedExerciseIds.contains(exercise.id)
                    Button(action: {
                        if isExcluded {
                            viewModel.excludedExerciseIds.remove(exercise.id)
                        } else {
                            viewModel.excludedExerciseIds.insert(exercise.id)
                        }
                    }) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(exercise.nameEn)
                                    .font(.body)
                                Text(exercise.difficultyLevel.displayName)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                if !exercise.requiredEquipment.isEmpty {
                                    Text(exercise.requiredEquipment.map(\.displayName).joined(separator: ", "))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Image(systemName: isExcluded ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(isExcluded ? .red : .secondary)
                        }
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: 6 — Schedule

    private var scheduleStep: some View {
        OnboardingStepCard(title: "Your week", subtitle: "Frequency + per-session time") {
            Stepper("Days per week: \(viewModel.daysPerWeek)", value: $viewModel.daysPerWeek, in: 2...6)
            HStack {
                Text("Time / session")
                Slider(value: Binding(
                    get: { Double(viewModel.dailyTimeMinutes) },
                    set: { viewModel.dailyTimeMinutes = Int($0) }
                ), in: 15...120, step: 5)
                Text("\(viewModel.dailyTimeMinutes) min")
            }
            HintRow(
                WorkoutGenerator.splitType(forDaysPerWeek: viewModel.daysPerWeek).description
            )
        }
    }

    // MARK: 7 — Timeline

    private var timelineStep: some View {
        OnboardingStepCard(title: "Timeline", subtitle: viewModel.fitnessGoal == .healthMaintenance
            ? "Maintenance — no timeline required" : "Target weight + duration") {
            if viewModel.fitnessGoal == .healthMaintenance {
                Text("Your daily caloric target = TDEE (\(Int(energyResult.tdee)) kcal), zero deficit.")
                    .foregroundStyle(.secondary)
            } else {
                HStack {
                    Text("Timeline")
                    Slider(value: Binding(
                        get: { Double(viewModel.targetDays ?? 60) },
                        set: { viewModel.targetDays = Int($0) }
                    ), in: 14...365, step: 1)
                    Text("\(viewModel.targetDays ?? 60) days")
                        .monospacedDigit()
                        .frame(width: 70, alignment: .trailing)
                }
                TextField("Target weight (kg)", value: $viewModel.targetWeightKg, format: .number)
                Stepper("Days: \(viewModel.targetDays ?? 60)", value: Binding(
                    get: { viewModel.targetDays ?? 60 },
                    set: { viewModel.targetDays = $0 }
                ), in: 14...365)
                if energyResult.warningLevel == .extreme {
                    TimelineWarning(
                        title: "High risk — extreme deficit",
                        message: "Your deficit exceeds safe limits. Workouts alone cannot achieve it; dietary intervention is required, and muscle loss is a risk."
                    )
                } else if energyResult.warningLevel == .atCeiling {
                    TimelineWarning(
                        title: "At safe ceiling",
                        message: "Daily deficit is at the 25% TDEE ceiling. Proceed with caution; monitor for fatigue."
                    )
                }
                Text(String(format: "Target: %d kcal/day · deficit %.0f kcal", Int(energyResult.caloricTarget), energyResult.clampedDeficit))
                    .font(.callout)
                    .monospacedDigit()
            }
            Button(action: {
                let profile = viewModel.buildProfile()
                onComplete(profile, viewModel.excludedExerciseIds)
            }) {
                Text("Finish onboarding")
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 50)
            }
            .buttonStyle(.borderedProminent)
            .padding(.top, 12)
            .disabled(viewModel.validateAll() != nil)
        }
    }

    // Helpers

    private func archetypeIcon(_ shape: BodyShape) -> String {
        switch shape {
        case .invertedTriangle: return "figure.martial.arts"
        case .rectangle:        return "figure.stand.smooth"
        case .oval:             return "figure.strengthtraining.traditional.kettlebell.swing"
        case .triangle:         return "figure.dance"
        case .hourglass:        return "figure.rower"
        case .pear:              return "figure.dress.line.vertical.figure"
        case .apple:            return "figure.outdoor_cycle"
        }
    }
}

extension SplitType: CustomStringConvertible {
    public var description: String {
        switch self {
        case .fullBody:   return "Plan: Full-Body A/B split"
        case .upperLower: return "Plan: Upper/Lower split"
        case .ppl:        return "Plan: Push/Pull/Legs split"
        case .focus:      return "Plan: Focus split"
        }
    }
}

// MARK: - Reusable onboarding components

struct OnboardingStepCard<Content: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(title).font(.largeTitle).bold()
                Text(subtitle).font(.title3).foregroundStyle(.secondary)
                content
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 40)
        }
    }
}

struct ValidationErrorList: View {
    let errors: [OnboardingViewModel.ValidationError]
    var body: some View {
        if errors.isEmpty { EmptyView() } else {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(errors, id: \.self) { err in
                    Label(err.errorDescription ?? "", systemImage: "exclamationmark.circle.fill")
                        .foregroundStyle(.red)
                        .font(.caption)
                }
            }
        }
    }
}

struct HintRow: View {
    let text: String
    var body: some View {
        Label(text, systemImage: "list.bullet.rectangle.portrait")
            .font(.callout)
            .foregroundStyle(.secondary)
    }
}

struct TimelineWarning: View {
    let title: String
    let message: String
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(title, systemImage: "exclamationmark.triangle.fill")
                .font(.headline)
                .foregroundStyle(.orange)
            Text(message).font(.callout).foregroundStyle(.primary)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.orange.opacity(0.12)))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.orange.opacity(0.4), lineWidth: 1))
    }
}
