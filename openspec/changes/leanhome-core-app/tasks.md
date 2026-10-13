# Tasks: LeanHome Full Native Mobile Architecture & Core Engine

## 1. Domain Entities & Mathematical Test Vectors

- [ ] 1.1 Define shared JSON schemas for Exercise, UserProfile, WorkoutSession, and BodyArchetype
- [ ] 1.2 Write canonical mathematical calculation specifications (Mifflin-St Jeor BMR, TDEE, Dynamic Deficit, Safety Ceilings, Asian vs International BMI)
- [ ] 1.3 Create canonical test vectors (`math_engine_test_vectors.json`) covering edge cases (boundary ages, BMI thresholds, 30 vs 60-day cuts, metabolic deficit clamps) ← (verify: test vectors validate all edge cases and safety limits without floating point discrepancies)

## 2. Exercise Catalog Seed Data & Offline Database

- [ ] 2.1 Assemble comprehensive seed catalog JSON of 60+ home exercises with tags (equipment, joints contraindicated, primary/secondary muscles, common mistakes, form cues)
- [ ] 2.2 Create SQLite database build script / pre-packaged `exercises_seed.db` schema
- [ ] 2.3 Verify seed database queries for equipment subsets and injury filtering ← (verify: constraint queries return valid non-empty routines for all equipment combinations without SQL syntax errors)

## 3. Android Native Core (Kotlin, Jetpack Compose, Room)

- [ ] 3.1 Initialize Android project architecture with Compose, Material3, and Clean Architecture modules (core-domain, core-data, core-ui, feature-onboarding, feature-workout)
- [ ] 3.2 Implement Room database pre-populated from `exercises_seed.db` with DAO queries and offline fallback
- [ ] 3.3 Implement `EnergyBalanceEngine` and `WorkoutGenerator` in Kotlin with unit tests passing 100% of canonical test vectors
- [ ] 3.4 Build Onboarding Compose UI (Metrics input, WHO country BMI gauge, 2D body archetype picker, dynamic timeline slider with physiological warnings)
- [ ] 3.5 Build Workout Player & Session Check-in Compose UI with Media3 ExoPlayer, LRU disk cache, and post-workout RPE rating ← (verify: Android app executes onboarding, generates personalized split offline, and plays workout session smoothly)

## 4. iOS Native Core (Swift 6, SwiftUI, SwiftData)

- [ ] 4.1 Initialize iOS Xcode project structure with SwiftUI, SwiftData, and Modular Architecture (CoreDomain, CoreData, FeatureOnboarding, FeatureWorkout)
- [ ] 4.2 Implement SwiftData schema and seed importer from bundled catalog JSON
- [ ] 4.3 Implement `EnergyBalanceEngine` and `WorkoutGenerator` in Swift with XCTest passing 100% of canonical test vectors
- [ ] 4.4 Build Onboarding SwiftUI views (Metrics input, WHO BMI gauge, 2D body archetype picker, dynamic timeline slider with physiological warnings)
- [ ] 4.5 Build Workout Player & Session Check-in SwiftUI views with AVPlayer, LRU media cache, and post-session RPE logger ← (verify: iOS app executes onboarding, generates personalized split offline, and plays workout session smoothly)

## 5. Media Pipeline & Asset Delivery

- [ ] 5.1 Define CDN storage structure (Cloudflare R2 / S3) and asset metadata schema for H.264 video clips and anatomical SVG muscle heatmaps
- [ ] 5.2 Implement LRU video cache manager with 500MB storage ceiling and offline SVG fallback on both platforms
- [ ] 5.3 Implement background pre-caching worker for upcoming workout session assets ← (verify: workout plays smoothly in Airplane mode with pre-cached media and falls back to vector illustration if un-cached)
