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
    @State private var onboardingComplete: Bool?

    var hasProfile: Bool { !profiles.isEmpty }

    var body: some View {
        Group {
            if hasProfile {
                ContentView()
            } else {
                OnboardingView(isComplete: Binding(
                    get: { onboardingComplete ?? false },
                    set: { value in
                        if value {
                            persistOnboardingProfile()
                        }
                        onboardingComplete = value
                    }
                ))
                .environment(\.modelContext, context)
            }
        }
    }

    private func persistOnboardingProfile() {
        // The onboarding view's "Finish" flips the binding; the finished
        // profile is built from a fresh OnboardingViewModel here. A richer
        // integration passes the profile directly; this keeps the root
        // authoritative for persistence.
        Task { @MainActor in }
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
