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

### Requirement: Weekly Weigh-in Prompt UI
The system SHALL display a weekly weigh-in prompt on the home screen every 7 days requesting the user to enter their morning fasting weight. The prompt SHALL be dismissible but SHALL resurface if the weigh-in is not recorded within the 7-day window.

#### Scenario: Weekly weigh-in prompt surfaces
- **WHEN** 7 days have elapsed since the user's last recorded weigh-in
- **THEN** the home screen displays a weigh-in prompt card requesting the user to enter their morning fasting weight

### Requirement: Progress Dashboard
The system SHALL provide a progress dashboard screen showing: a weight trend line chart from baseline to current weight, a projected goal achievement date computed from the current rate of loss, an adherence rate expressed as the percentage of planned sessions completed, and an adherence badge displayed when compliance reaches >= 85%.

#### Scenario: Dashboard renders trend and projection
- **WHEN** user opens the progress dashboard after 4 weeks of logged weigh-ins
- **THEN** the dashboard displays the weight trend line chart from baseline to current, the projected goal date computed from the 4-week rate of loss, the adherence rate, and an adherence badge because compliance is >= 85%

### Requirement: Adherence Tracking
The system SHALL compute adherence as (completed sessions / planned sessions) over the trailing 7-day window. When adherence >= 85%, the system SHALL display an adherence badge. When weight loss stalls for 2 consecutive weeks while adherence >= 85%, the system SHALL show a plateau warning suggesting a caloric or activity recalculation.

#### Scenario: Adherence badge awarded at 85% compliance
- **WHEN** the user completes 6 of 7 planned sessions in the trailing 7-day window (adherence = ~86%)
- **THEN** the system displays the adherence badge on the progress dashboard

#### Scenario: Plateau warning shown under high adherence
- **WHEN** the user's weight loss stalls for 2 consecutive weeks while adherence remains >= 85%
- **THEN** the system shows a plateau warning suggesting a caloric or activity recalculation

### Requirement: Health Platform Integration (Read-Only)

The system SHALL integrate with platform health data stores to import step count, active energy burned, and body mass readings:
- iOS: Apple HealthKit (HKHealthStore)
- Android: Google Health Connect (HealthConnectClient)

Integration SHALL be read-only — the app SHALL only read data, never write. The system SHALL request user permission on first access and gracefully handle denial.

#### Scenario: User grants Health access and imports steps
- **WHEN** user taps 'Import from Health' on the Progress Dashboard and grants permission
- **THEN** system retrieves last 7 days of step count data, displays daily average, and incorporates into TDEE activity level estimation

#### Scenario: User denies Health access
- **WHEN** user denies Health permission
- **THEN** system shows 'Health data unavailable' message with option to retry, no crash

#### Scenario: Health data unavailable on device
- **WHEN** Health platform is not available (HealthKit on iPad / Health Connect not installed)
- **THEN** system hides the Health import option and shows no error
