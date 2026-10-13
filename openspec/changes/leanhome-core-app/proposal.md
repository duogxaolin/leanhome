# Proposal: LeanHome Full Native Mobile Architecture & Core Engine

## Why

Users who want to lose weight and cut fat at home often struggle with generic workout plans that ignore their available equipment, physical limitations/injuries, and regional body standards. Furthermore, existing apps frequently enforce unrealistic or dangerous weight loss timelines without energy balance safety constraints, or fail when network connectivity is lost.

LeanHome solves this by providing a 100% full native (iOS SwiftUI and Android Jetpack Compose) home fitness system with an offline-first hybrid workout generation engine, scientifically grounded energy balance calculations, safe dynamic timeline scaling, and structured exercise guidance with form correction cues.

## What Changes

This change establishes the foundational architecture and complete specification for the LeanHome platform across iOS, Android, and shared domain models:

- **100% Full Native Mobile Client Architecture**: Separate native applications for iOS (SwiftUI, Swift 6, SwiftData, AVFoundation) and Android (Jetpack Compose, Kotlin, Room, Media3 ExoPlayer) maximizing hardware acceleration and platform responsiveness.
- **Offline-First Hybrid Workout Engine**: Local embedded database (bundled SQLite/Room/SwiftData seed) allowing full onboarding, TDEE calculation, and personalized routine generation without requiring internet connectivity, with background sync when online.
- **Scientifically Grounded Energy Balance & Dynamic Timeline**: Mifflin-St Jeor BMR/TDEE calculation with safe daily caloric deficit ceilings ($\le 25\%$ TDEE, $\le 1\%$ body weight loss/week) and physiological threshold warnings when users adjust target timelines (e.g. 60 days vs 30 days).
- **2D Body Shape Profiling & Regional Health Benchmarks**: WHO regional BMI classification (including Western Pacific / Asian specific thresholds) and 2D vector body archetype picker for V1 (deferring camera AI scan to V2).
- **Constraint-Based Workout Routine Generator**: Split generation (Full Body, Upper/Lower, Push/Pull/Legs) filtered by equipment tags (`BODYWEIGHT`, `RESISTANCE_BANDS`, `DUMBBELLS`) and joint/injury contraindications (`LOWER_BACK`, `KNEE`, `WRIST`, `SHOULDER`).
- **Interactive Exercise Player & Media Caching Engine**: Video playback with form correction cues, common mistakes, muscle heatmaps, and LRU disk caching with offline fallback.
- **Workout Check-in & Adaptive Regulation**: Post-workout RPE (Rate of Perceived Exertion) logging and weekly body measurement tracking with fatigue-based volume auto-regulation.

## Capabilities

### New Capabilities
- `user-onboarding-profiling`: Personal metric collection, regional health standards (WHO Asian vs International BMI), and 2D body shape archetype selection.
- `energy-balance-engine`: BMR, TDEE, safe caloric deficit modeling, and dynamic timeline adjustment with safety warnings.
- `workout-generator`: Local offline constraint solver matching equipment, injuries, training frequency, and split routines.
- `workout-player-media`: Native exercise session player, timer, rep counter, form tips, common mistakes, and progressive video caching.
- `session-checkin-progress`: Workout completion logging, RPE fatigue tracking, weekly weigh-in analytics, and volume adjustment.

### Modified Capabilities
None (initial greenfield project).

## Impact

- **Platforms**: iOS 17+ (SwiftUI), Android 14+ (Jetpack Compose, API 26+ minSdk).
- **Architecture**: Clean Architecture / MVI (Model-View-Intent) on both platforms with unified domain entity definitions.
- **Storage**: Local SQLite/Room (Android) and SwiftData (iOS) bundled with pre-seeded exercises and routines.
- **Network & CDN**: Cloudflare R2 / S3 CDN for progressive video streaming and catalog delta updates.
- **Security & Privacy**: Zero body photo uploads in V1; all biometric data encrypted locally using platform keystores (iOS Keychain / Android EncryptedSharedPreferences).
