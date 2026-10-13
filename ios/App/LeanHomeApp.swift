//
//  LeanHomeApp.swift
//  App
//
//  Composition root: SwiftData container, seed importer run on first launch,
//  and root navigation between onboarding and the workout dashboard.
//

#if canImport(SwiftUI) && canImport(SwiftData)

import SwiftUI
import SwiftData
import CoreDomain
import CoreData
import FeatureOnboarding
import FeatureWorkout
import Media

@main
struct LeanHomeApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .modelContainer(LeanHomeApp.container)
    }

    /// Shared SwiftData container with the three model types + on-first-launch
    /// seeding of the bundled exercise catalog.
    static let container: ModelContainer = {
        StringArrayTransformer.register()
        do {
            let container = try ModelContainer(
                for: ExerciseModel.self,
                UserProfileModel.self,
                WorkoutSessionModel.self,
                WeightLogModel.self,
                configurations: ModelConfiguration(url: LeanHomeApp.storeURL)
            )
            try SeedImporter.seedIfNeeded(in: container.mainContext)
            return container
        } catch {
            // Fallback to an in-memory container in case the store cannot open.
            let inMemory = ModelConfiguration(
                url: URL(string: "memory://leanhome")!,
                isStoredInMemoryOnly: true
            )
            do {
                let c = try ModelContainer(
                    for: ExerciseModel.self,
                    UserProfileModel.self,
                    WorkoutSessionModel.self,
                    WeightLogModel.self,
                    configurations: inMemory
                )
                try SeedImporter.seedIfNeeded(in: c.mainContext)
                return c
            } catch {
                fatalError("LeanHome could not create a SwiftData container: \(error)")
            }
        }
    }()

    /// Default on-disk store URL in the app's Application Support directory.
    static var storeURL: URL {
        let fm = FileManager.default
        let support = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        try? fm.createDirectory(at: support, withIntermediateDirectories: true)
        return support.appendingPathComponent("LeanHome.store")
    }
}

/// Root navigation: onboarding the first time, then the workout dashboard.
struct RootView: View {
    @Environment(\.modelContext) private var context
    @Query private var profiles: [UserProfileModel]

    var hasProfile: Bool { !profiles.isEmpty }

    var body: some View {
        Group {
            if hasProfile {
                ContentView()
            } else {
                OnboardingView { profile, excludedExerciseIds in
                    persistOnboardingProfile(profile: profile, excludedExerciseIds: excludedExerciseIds)
                }
                .environment(\.modelContext, context)
            }
        }
    }

    /// Persist the completed onboarding profile to SwiftData, generate the
    /// first weekly workout plan, insert all session models, then save.
    /// The `@Query` for `profiles` will observe the insertion and flip
    /// `hasProfile` to `true`, automatically transitioning to `ContentView`.
    private func persistOnboardingProfile(profile: UserProfile, excludedExerciseIds: Set<String>) {
        Task { @MainActor in
            do {
                let profileRepo = UserProfileRepository(context: context)
                _ = try profileRepo.save(profile)

                let exerciseRepo = ExerciseRepository(context: context)
                let catalog = try exerciseRepo.all()

                let plan = WorkoutGenerator.generateWeeklyPlan(
                    catalog: catalog,
                    profile: profile,
                    excludedExerciseIds: excludedExerciseIds
                )

                let sessionRepo = WorkoutSessionRepository(context: context)
                let today = Date()
                let isoFmt = ISO8601DateFormatter()
                isoFmt.formatOptions = [.withFullDate]
                for (i, planned) in plan.sessions.enumerated() {
                    let date = Calendar.current.date(byAdding: .day, value: i, to: today) ?? today
                    let iso = isoFmt.string(from: date)
                    let session = WorkoutSession(
                        userId: profile.id,
                        scheduledDate: iso,
                        splitName: planned.splitSession,
                        status: .pending,
                        exercises: planned.exercises
                    )
                    _ = try sessionRepo.save(session)
                }
            } catch {
                // Persistence failure is non-fatal; the profile may still be
                // present from a prior attempt. The @Query refresh will
                // transition to ContentView if the profile was saved.
            }
        }
    }
}

/// Tabbed main interface: Workouts, Sessions, Profile.
struct ContentView: View {
    @Environment(\.modelContext) private var context
    @Query private var exercises: [ExerciseModel]
    @Query private var profiles: [UserProfileModel]

    var body: some View {
        TabView {
            WorkoutListView()
                .tabItem { Label("Workouts", systemImage: "figure.strengthtraining.traditional") }
            SessionHistoryView()
                .tabItem { Label("History", systemImage: "clock.arrow.circlepath") }
            ProgressDashboardView()
                .tabItem { Label("Progress", systemImage: "chart.line.uptrend.xyaxis") }
            ProfileSummaryView()
                .tabItem { Label("Profile", systemImage: "person.crop.circle") }
        }
    }
}

/// Compact profile summary.
struct ProfileSummaryView: View {
    @Query private var profiles: [UserProfileModel]
    var body: some View {
        NavigationStack {
            if let p = profiles.first {
                List {
                    Section("Profile") {
                        Text("Name: \(p.name)")
                        Text("Goal: \(p.fitnessGoal)")
                        Text("BMR: \(Int(p.bmr)) kcal · TDEE: \(Int(p.tdee)) kcal")
                    }
                    Section("Catalog") {
                        EmptyView()
                    }
                }
                .navigationTitle("Profile")
            } else {
                Text("No profile. Complete onboarding.")
            }
        }
    }
}

#endif // canImport(SwiftUI) && canImport(SwiftData)
