# LeanHome 🏋️‍♂️

> **Full Native Home Fitness & Fat Loss Platform**  
> Built for Android (Kotlin / Jetpack Compose) and iOS (Swift 6 / SwiftUI) with an offline-first hybrid engine.

---

## 📌 Overview

**LeanHome** is a privacy-first, scientifically grounded mobile fitness platform designed for home-based fat loss, body recomposition, and conditioning. 

Unlike generic fitness apps that force cookie-cutter templates, LeanHome solves the fundamental pain points of home workouts:
- **Offline-First Hybrid Engine**: Workout routines, exercise catalogs, and energy balance calculations execute 100% locally on-device. Zero network required during sessions.
- **Scientifically Grounded Energy Balance**: BMR and TDEE calculations (Mifflin-St Jeor) coupled with a dynamic timeline slider with hard physiological safety ceilings ($\le 25\%$ TDEE deficit, $\le 1\%$ body weight loss/week) to prevent overtraining and metabolic slowdown.
- **Equipment & Injury Constraint Solver**: Generates routines matching exactly what equipment you own (`BODYWEIGHT`, `RESISTANCE_BANDS`, `DUMBBELLS`) and excludes exercises that compromise vulnerable joints (`LOWER_BACK`, `KNEE`, `WRIST`, `SHOULDER`).
- **Regional Health Benchmarks**: Automatically maps BMI and health classifications using regional standards (WHO Asian/WPRO vs International WHO).
- **100% Full Native Experience**: Zero cross-platform abstractions—built with Jetpack Compose on Android and SwiftUI on iOS for 120Hz smooth animations, low-latency video looping, and deep OS integration.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      LeanHome System                        │
├──────────────────────────────┬──────────────────────────────┤
│       Android Native         │          iOS Native          │
│   (Kotlin / Jetpack Compose) │       (Swift 6 / SwiftUI)    │
├──────────────────────────────┼──────────────────────────────┤
│  Presentation: Compose MVI   │  Presentation: SwiftUI MVI   │
│  Domain: WorkoutEngine, BMR  │  Domain: WorkoutEngine, BMR  │
│  Data: Room DB (Pre-seeded)  │  Data: SwiftData (Bundled)   │
│  Media: Media3 (ExoPlayer)   │  Media: AVPlayer + LRU Cache │
└──────────────┬───────────────┴──────────────┬───────────────┘
               │                              │
               └──────────────┬───────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Backend & Content CDN                     │
│  - REST API: Catalog delta updates & profile backup         │
│  - Cloudflare R2 / S3: Compressed exercise MP4s & SVG maps  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🌟 Key Features

1. **Biometric Profiling & WHO Standards**:
   - Collects age, gender, height, weight, and country.
   - Evaluates health metrics against regional benchmarks (e.g., Asian BMI $\ge 23$ overweight, $\ge 25$ obese).
   - 2D Somatotype / Body Archetype Picker (V1) providing instant physical archetype categorization without privacy risks.
2. **Energy Balance & Dynamic Timeline Engine**:
   - Computes Mifflin-St Jeor BMR and activity-adjusted TDEE.
   - Dynamic timeline adjustment ($1\text{ kg fat} \approx 7,700\text{ kcal}$).
   - Strict clinical safety clamps with warning triggers for extreme cuts.
3. **Offline Workout Generator**:
   - Matches equipment tags (`BODYWEIGHT_ONLY`, `RESISTANCE_BANDS`, `ADJUSTABLE_DUMBBELLS`, etc.).
   - Excludes exercises by contraindicated joint tags (`LOWER_BACK`, `KNEE`, `WRIST`, `SHOULDER`).
   - Generates physiologically balanced routines: 2–3 days (Full Body), 4 days (Upper/Lower), 5–6 days (Push/Pull/Legs).
4. **Interactive Exercise Player & Media Caching**:
   - Exercise state machine: active timer/reps, rest countdown with audio/haptic cues.
   - Form correction cues, common mistakes, and primary/secondary muscle heatmaps.
   - 500MB LRU disk cache with automatic fallback to vector illustrations when offline.
5. **Session Check-in & Fatigue Auto-Regulation**:
   - Post-workout RPE (Rate of Perceived Exertion) logging (1–10).
   - Auto-regulation: Rolling average RPE $\ge 9.0$ automatically reduces volume by 10% (deload set removal).
   - Weekly weigh-in tracking and plateau detection.

---

## 📂 Project Structure

```
leanhome/
├── openspec/                         # OpenSpec change definitions and specs
│   ├── config.yaml
│   └── changes/leanhome-core-app/
│       ├── proposal.md               # Change proposal
│       ├── design.md                 # System architecture & DB schemas
│       ├── tasks.md                  # Implementation checklist
│       └── specs/                    # BDD specifications (RFC 2119)
├── android/                          # Android Native (Jetpack Compose) [WIP]
├── ios/                              # iOS Native (SwiftUI / SwiftData) [WIP]
├── shared/                           # Shared schemas & Canonical test vectors [WIP]
├── README.md                         # Project documentation
├── CLAUDE.md                         # Claude AI assistant guidelines
└── AGENTS.md                         # Multi-agent collaboration protocols
```

---

## 📋 OpenSpec Compliance

This repository uses [OpenSpec](https://github.com/Fission-AI/OpenSpec) for specification-driven development:

```bash
# Validate all changes and specifications
openspec validate --all --strict

# Check planning status
openspec status --change leanhome-core-app
```

---

## 🔒 Privacy & Compliance

- **No Remote Body Imagery**: In V1, body shape selection is 100% 2D illustration based. Zero body photos are required or uploaded.
- **Local Biometrics**: All user metrics remain on the local device, protected by platform sandboxing and keystores.
- **App Store Ready**: Conforms to Apple App Store Guidelines 5.1.1 (Data Privacy) and 1.4 (Physical Harm / Medical Disclaimer).
