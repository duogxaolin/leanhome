# Spec Delta: Workout Player and Media Caching

## Purpose
Provides an interactive, native exercise execution player with rest countdowns, rep counters, looping instructional videos, form correction cues, common mistake alerts, muscle heatmaps, and local media cache management.

## ADDED Requirements

### Requirement: Real-time Workout Player Lifecycle
The player SHALL manage the interactive state machine of a workout session:
1. `EXERCISE_ACTIVE`: Displays looping demonstration video, exercise name, target reps/time, current set number, form cues, and muscle highlight diagram.
2. `REST_INTERVAL`: Countdown timer with sound/haptic feedback on final 3 seconds and option to skip (+30s / -15s adjustments).
3. `SESSION_COMPLETE`: Summarizes total duration, completed exercises, volume lifted, and triggers post-workout check-in.

#### Scenario: User completes set
- **WHEN** user taps "Done Set" for set 2 of 3
- **THEN** set is recorded as completed and rest interval countdown begins immediately

### Requirement: Exercise Instruction and Form Error Guidance
For each exercise, the player view SHALL render:
1. Primary targeted muscle groups and secondary synergistic muscle groups.
2. At least two "Common Mistakes" with explicit visual or text correction indicators.
3. At least two "Correct Form Cues" (e.g. breathing cadence, joint angles).

#### Scenario: Viewing exercise details during rest
- **WHEN** user expands exercise guide during rest
- **THEN** common mistakes (e.g., "Arching lower back", "Flaring elbows") and primary muscles are clearly presented

### Requirement: Offline Media and LRU Caching
Instructional videos SHALL be delivered in compressed H.264 / H.265 format. The app SHALL maintain a local Least-Recently-Used (LRU) disk cache (default ceiling 500 MB).
- When network is connected, the app SHALL pre-fetch and cache video assets for the current scheduled workout.
- When network is disconnected, the player SHALL play cached videos if present, or fall back gracefully to animated vector/SVG illustration and step-by-step text instruction without crashing or blocking the session.

#### Scenario: Offline execution with un-cached video
- **WHEN** network is unreachable and a video is not in local cache
- **THEN** player displays fallback vector illustration and text instructions, showing a non-intrusive badge "Video available online"
