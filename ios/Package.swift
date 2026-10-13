// swift-tools-version: 6.0
//
// LeanHome iOS Native Core
// Swift 6 strict concurrency, SwiftUI, SwiftData, AVFoundation.
// 100% full native, offline-first.
//
// Modules:
//   CoreDomain      — EnergyBalanceEngine, WorkoutGenerator, entities, use cases
//   CoreData         — SwiftData models, seed importer, repositories
//   FeatureOnboarding — Onboarding flow views
//   FeatureWorkout   — Workout player, check-in, list/detail views
//   Media            — LRU 500MB video cache actor + offline SVG fallback (Task 5)
//   App              — LeanHomeApp, ContentView, navigation
//   LeanHomeTests    — XCTest suite passing all 31 canonical test vectors
//
// Build (CLI, Apple):  swift build
// Test  (CLI, any):     swift test          ← builds CoreDomain + LeanHomeTests everywhere;
//                                        Apple-only targets build on iOS/macOS only.
// Xcode:                open Package.swift

import PackageDescription

// On Linux (CI/headless) we only build the platform-neutral logic + tests so
// the canonical 31-vector suite can run without SwiftUI/SwiftData/AVFoundation.
// On iOS/macOS the full native target set is available.
#if os(Linux)
let packageTargets: [Target] = [
    .target(
        name: "CoreDomain",
        dependencies: [],
        path: "CoreDomain",
        exclude: ["Tests"]
    ),
    .testTarget(
        name: "LeanHomeTests",
        dependencies: ["CoreDomain"],
        path: "CoreDomain/Tests",
        exclude: ["Resources"],
        resources: [
            .copy("Resources/math_engine_test_vectors.json"),
        ]
    ),
]
let packageProducts: [Product] = []
#else
let packageTargets: [Target] = [
    // MARK: CoreDomain — pure logic, no UI/platform dependencies.
    .target(
        name: "CoreDomain",
        dependencies: [],
        path: "CoreDomain",
        exclude: ["Tests"]
    ),

    // MARK: CoreData — SwiftData models + seed importer + repositories.
    .target(
        name: "CoreData",
        dependencies: ["CoreDomain"],
        path: "CoreData",
        exclude: [],
        resources: [
            .copy("Resources/exercises_seed.json"),
        ]
    ),

    // MARK: FeatureOnboarding — SwiftUI onboarding flow.
    .target(
        name: "FeatureOnboarding",
        dependencies: ["CoreDomain", "CoreData"],
        path: "FeatureOnboarding"
    ),

    // MARK: FeatureWorkout — workout player, check-in, list/detail.
    .target(
        name: "FeatureWorkout",
        dependencies: ["CoreDomain", "CoreData", "Media"],
        path: "FeatureWorkout"
    ),

    // MARK: Media — LRU video cache actor + offline SVG fallback.
    .target(
        name: "Media",
        dependencies: [],
        path: "Media",
        resources: [
            .copy("Resources/diagrams"),
        ]
    ),

    // MARK: App — composition root, navigation, LeanHomeApp.
    .target(
        name: "App",
        dependencies: [
            "CoreDomain",
            "CoreData",
            "FeatureOnboarding",
            "FeatureWorkout",
            "Media",
        ],
        path: "App"
    ),

    // MARK: LeanHomeTests — canonical test-vector parity (31 vectors).
    .testTarget(
        name: "LeanHomeTests",
        dependencies: ["CoreDomain"],
        path: "CoreDomain/Tests",
        exclude: ["Resources"],
        resources: [
            .copy("Resources/math_engine_test_vectors.json"),
        ]
    ),
]
let packageProducts: [Product] = [
    .library(name: "LeanHome", targets: ["App"]),
]
#endif

let package = Package(
    name: "LeanHome",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: packageProducts,
    targets: packageTargets
)
