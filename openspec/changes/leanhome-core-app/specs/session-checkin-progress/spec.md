# Spec Delta: Session Check-in and Progress Tracking

## Purpose
Records post-workout subjective exertion ratings (RPE), tracks weekly body metrics (weight, waist circumference), monitors plateau and fatigue indicators, and automatically regulates subsequent workout volume.

## ADDED Requirements

### Requirement: Post-Workout Session Check-in
Immediately following workout completion, the system SHALL prompt the user to rate their session:
1. Rate of Perceived Exertion (RPE) on a standardized 1 to 10 scale (1 = Very light, 10 = Maximum effort / failure).
2. Optional subjective feeling tags (e.g. `GREAT`, `TIRED`, `JOINT_DISCOMFORT`, `ENERGIZED`).
3. Actual sets and reps completed compared to plan.

#### Scenario: Submitting RPE rating
- **WHEN** user finishes workout and selects RPE = 7 ("Challenging but controlled")
- **THEN** session record is saved locally in SQLite/Room/SwiftData and queued for cloud sync

### Requirement: Fatigue Auto-Regulation (Volume Adjustment)
The engine SHALL evaluate rolling 3-session fatigue:
- If average RPE >= 9.0 over 3 consecutive sessions, the engine shall automatically reduce the volume of the next week's sessions by 10% (deload set removal) to prevent overtraining.
- If average RPE <= 5.0 over 3 consecutive sessions, the engine shall propose a progressive overload increase (+1 rep or +1 set).

#### Scenario: Overtraining threshold reached
- **WHEN** user logs RPE 9, 10, 9 across 3 sessions
- **THEN** system flags high fatigue and schedules an auto-regulated lighter session with a clear notification

### Requirement: Weekly Metric Progress & Plateau Detection
The system SHALL prompt weekly weigh-ins under consistent conditions (morning fasting).
- If weight loss stalls for 2 consecutive weeks while compliance >= 85%, the engine suggests a caloric/activity recalculation.

#### Scenario: Consistent weigh-in recorded
- **WHEN** user records weekly weight 77.2 kg (-0.8 kg from baseline)
- **THEN** progress chart updates slope, projected goal date refreshes, and adherence badge is awarded
