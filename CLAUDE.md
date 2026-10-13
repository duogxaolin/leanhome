# CLAUDE.md — LeanHome Development Guidelines

## Project Context
LeanHome is a full native home fitness application for Android (Kotlin / Jetpack Compose) and iOS (Swift 6 / SwiftUI) powered by an offline-first hybrid workout generation engine.

## Core Architectural Invariants
1. **100% Full Native**: Android code lives strictly under `android/` using Jetpack Compose. iOS code lives strictly under `ios/` using SwiftUI. Zero cross-platform webview/hybrid bridges.
2. **Offline-First Hybrid Engine**: All onboarding, TDEE calculations, routine generation, and session execution MUST execute locally without internet connectivity. Cloud is strictly for catalog sync and profile backups.
3. **Parity via Canonical Test Vectors**: Core mathematical logic (BMR, TDEE, Deficit, MEV, Split rules) is defined in canonical JSON test vectors (`shared/math_engine_test_vectors.json`). Both Swift and Kotlin test suites MUST pass 100% of these test vectors identically.
4. **Physiological Safety Floor**: Caloric deficits MUST be capped at $\le 25\%$ TDEE or $1000\text{ kcal/day}$. Weight loss rate MUST be capped at $\le 1\%$ body weight/week. Never bypass safety checks.
5. **Privacy First (V1)**: Zero body photo uploads. In V1, body shape selection uses 2D vector archetypes. All personal biometrics are encrypted locally on-device.

---

## Build, Test & Validation Commands

### OpenSpec Specification & Validation
```bash
# Validate all OpenSpec artifacts strictly (must pass with 0 errors/warnings)
openspec validate --all --strict

# Check current change progress
openspec status --change leanhome-core-app

# View delta specifications
openspec show leanhome-core-app --json --deltas-only
```

### Android (Gradle)
```bash
cd android
./gradlew test                   # Run unit tests across all modules
./gradlew connectedAndroidTest   # Run instrumented UI tests
./gradlew assembleDebug          # Build debug APK
```

### iOS (Xcodebuild / Swift Package Manager)
```bash
cd ios
swift test                       # Run domain and unit tests
xcodebuild test -scheme LeanHome -destination 'platform=iOS Simulator,name=iPhone 16'
```

---

## Code Style & Conventions

### Android (Kotlin & Jetpack Compose)
- **Language Level**: Kotlin 2.0+ with modern language idioms.
- **Architecture**: Clean Architecture + MVI / Unidirectional Data Flow (State, Intent/Event, Effect).
- **UI Toolkit**: Jetpack Compose with Material 3. Avoid legacy XML views.
- **Async & Reactive**: Kotlin Coroutines and StateFlow/SharedFlow.
- **Persistence**: Room Database with bundled asset database pre-population.
- **Media**: Media3 (ExoPlayer) with custom surface views and caching layers.

### iOS (Swift & SwiftUI)
- **Language Level**: Swift 6 with strict concurrency enabled (`Sendable`, Actor isolation).
- **Architecture**: Modular Architecture + MVI / Redux or MVVM with Observable models (`@Observable`).
- **UI Toolkit**: SwiftUI exclusively. Support iOS 17+.
- **Async & Concurrency**: Swift Concurrency (`async/await`, `TaskGroup`, `AsyncStream`).
- **Persistence**: SwiftData / SQLite pre-populated from bundled JSON seed.
- **Media**: AVFoundation (`AVPlayer`, `AVPlayerLooper`) with custom memory/disk caching.

---

## Contribution & Verification Checklist
- Before committing any changes to business rules, update or verify the corresponding spec in `openspec/changes/leanhome-core-app/specs/`.
- Ensure all requirements use RFC 2119 keywords (`SHALL`, `MUST`, `SHOULD`).
- Run `openspec validate --all --strict` before submitting code.
