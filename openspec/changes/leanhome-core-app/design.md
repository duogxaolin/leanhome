# Design: LeanHome Architecture, Data Engine, and Native Platforms

## Context

See `proposal.md` for user motivation and problem definition.
The system requires 100% full native responsiveness on both Android and iOS, strict offline-first operational capability, adherence to App Store privacy policies regarding user body imagery, and scientifically accurate training and energy calculation logic.

## Goals / Non-Goals

**Goals:**
- Architect a clean, scalable project structure for both native Android (Kotlin, Jetpack Compose, Room) and native iOS (Swift, SwiftUI, SwiftData).
- Define a unified Domain Entity Schema and mathematical calculation contract (BMR, TDEE, Deficit, MEV volume, Contraindications) validated by identical test suites on both platforms.
- Establish an offline-first storage and synchronization mechanism: pre-bundled exercise catalog in SQLite/Room and SwiftData, operable with zero network connection.
- Design an efficient video media caching layer (LRU disk cache up to 500MB) with graceful fallback to vector graphics and text cues when offline.

**Non-Goals:**
- Camera-based real-time AI computer vision scanning in V1 (explicitly decoupled and targeted for V2).
- Web application or cross-platform webview abstractions.
- Live social networking feeds or user-to-user messaging in V1.

## Architecture & System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      LeanHome System                        │
├──────────────────────────────┬──────────────────────────────┤
│       Android Native         │          iOS Native          │
│   (Kotlin / Jetpack Compose) │       (Swift / SwiftUI)      │
├──────────────────────────────┼──────────────────────────────┤
│  Presentation: Compose MVI   │  Presentation: SwiftUI MVI   │
│  Domain: WorkoutEngine, BMR  │  Domain: WorkoutEngine, BMR  │
│  Data: Room DB + Seed Asset  │  Data: SwiftData + Seed JSON │
│  Media: Media3 (ExoPlayer)   │  Media: AVPlayer + Cache     │
└──────────────┬───────────────┴──────────────┬───────────────┘
               │                              │
               └──────────────┬───────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Backend & Content CDN                     │
│  - REST/JSON API: Catalog Delta Sync, User Profile Backup   │
│  - Cloudflare R2 / AWS S3: Compressed Exercise MP4s (H.264) │
└─────────────────────────────────────────────────────────────┘
```

## Key Decisions

### Decision 1: Pure 100% Native Architecture
- **Rationale**: Best possible rendering performance (120Hz ProMotion on iOS, LTPO on Android), direct hardware integration (Apple HealthKit / Google Health Connect, AVFoundation / Media3 low-latency playback), and seamless support for future platform-specific features (Dynamic Island, Live Activities, WearOS, Apple Watch).
- **Risk Mitigation**: Business logic parity is strictly guarded by publishing a canonical JSON test vector suite (`math_engine_test_vectors.json`) that both Swift and Kotlin unit test suites run to guarantee identical calculations down to floating point precision.

### Decision 2: Hybrid Offline-First Local Data Engine
- **Android**: Room ORM backed by SQLite with bundled asset database pre-population (`Room.databaseBuilder(...).createFromAsset("exercises_seed.db")`).
- **iOS**: SwiftData / SQLite pre-populated on first launch from bundled compressed JSON catalog.
- **Sync Protocol**: The client sends a `GET /api/v1/catalog/version` with its current schema version. If a newer catalog version exists on the server, a delta payload is downloaded in the background and applied in a local transaction.

### Decision 3: 2D Archetype Selector in V1, Camera AI in V2
- **Rationale**: Eliminates Apple Store Guideline 5.1.1 (Privacy and sensitive body photo scrutiny) and avoids onboarding dropout caused by requiring users to undress or pose in front of a camera.
- **Implementation**: 5 vector illustrations per gender representing standard Somatotypes (Ectomorph, Mesomorph, Endomorph) and WHR ratios.

### Decision 4: Progressive Media Delivery & LRU Cache
- **Storage**: Videos are encoded in H.264 / AAC at 720p 30fps (average file size: 1.5 - 3 MB for a 10s looping set).
- **Caching**: Local directory cache with a maximum capacity of 500 MB. When storage is exceeded, least recently viewed exercises are purged.
- **Pre-fetch**: Upon routine generation or daily check-in, videos for the upcoming session's exercises are queued for download if Wi-Fi is available.
- **Offline Fallback**: If a video is missing and the device is offline, the UI shows a high-contrast anatomical SVG illustration of starting and ending positions with primary muscle group coloring.

## Data Models & Core Schema

```sql
-- Catalog Exercises Table
CREATE TABLE exercises (
    id TEXT PRIMARY KEY,
    name_en TEXT NOT NULL,
    name_vi TEXT NOT NULL,
    primary_muscles TEXT NOT NULL, -- JSON array: ["CHEST", "TRICEPS"]
    secondary_muscles TEXT NOT NULL, -- JSON array: ["SHOULDERS", "CORE"]
    required_equipment TEXT NOT NULL, -- JSON array: ["DUMBBELLS"]
    contraindicated_joints TEXT NOT NULL, -- JSON array: ["LOWER_BACK"]
    difficulty_level TEXT NOT NULL, -- "BEGINNER", "INTERMEDIATE", "ADVANCED"
    video_url TEXT NOT NULL,
    fallback_thumbnail_url TEXT NOT NULL,
    common_mistakes TEXT NOT NULL, -- JSON array of strings
    correct_form_cues TEXT NOT NULL -- JSON array of strings
);

-- User Profiles Table
CREATE TABLE user_profiles (
    id TEXT PRIMARY KEY,
    age INTEGER NOT NULL,
    gender TEXT NOT NULL,
    height_cm REAL NOT NULL,
    weight_kg REAL NOT NULL,
    country_code TEXT NOT NULL,
    body_shape TEXT NOT NULL,
    fitness_goal TEXT NOT NULL,
    available_equipment TEXT NOT NULL, -- JSON array
    injury_flags TEXT NOT NULL, -- JSON array
    days_per_week INTEGER NOT NULL,
    daily_time_minutes INTEGER NOT NULL,
    target_weight_kg REAL,
    target_days INTEGER,
    bmr REAL NOT NULL,
    tdee REAL NOT NULL,
    created_at INTEGER NOT NULL
);

-- Workout Sessions & History Table
CREATE TABLE workout_sessions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    scheduled_date TEXT NOT NULL,
    split_name TEXT NOT NULL,
    status TEXT NOT NULL, -- "PENDING", "COMPLETED", "SKIPPED"
    completed_duration_seconds INTEGER,
    rpe_score INTEGER,
    feedback_notes TEXT,
    created_at INTEGER NOT NULL
);
```

## Risks / Trade-offs

1. **Dual Codebase Maintenance Overhead**:
   - *Trade-off*: Writing separate UI and domain code in Swift and Kotlin requires double the initial development effort compared to React Native or Flutter.
   - *Mitigation*: The domain layer is strictly modularized into clean functions without external library entanglements, making porting straightforward.
2. **Video Asset Hosting & Bandwidth Costs**:
   - *Trade-off*: Streaming 150+ workout videos across thousands of users can incur significant bandwidth costs.
   - *Mitigation*: Aggressive client-side caching (500MB LRU), aggressive video compression (CRF 26, 720p, ~2MB/clip), and zero-egress Cloudflare R2 object storage.
3. **Safety of Accelerated Weight Cut Timelines**:
   - *Trade-off*: Users wanting quick 30-day results might blame the app for fatigue or burnout.
   - *Mitigation*: Hard mathematical clamp on workout caloric deficit, prominent UI medical disclaimer, and clear educational feedback explaining safe metabolic limits.
