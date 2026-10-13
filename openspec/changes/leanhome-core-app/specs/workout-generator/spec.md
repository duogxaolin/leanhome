# Spec Delta: Workout Routine Generator

## Purpose
An offline-first constraint solver that creates personalized workout splits and exercise sessions based on user equipment availability, joint/injury contraindications, training frequency, and muscle group focus.

## ADDED Requirements

### Requirement: Equipment Constraint Filtering
The generator SHALL filter the universe of catalog exercises such that an exercise is selectable if and only if all of its `required_equipment` tags are contained in the user's selected equipment set (`user_available_equipment`).

#### Scenario: User has only bodyweight
- **WHEN** user selects only `BODYWEIGHT_ONLY`
- **THEN** all exercises requiring `RESISTANCE_BANDS` or `DUMBBELLS` are excluded from the candidate pool

#### Scenario: User has dumbbells and bands
- **WHEN** user has `DUMBBELLS` and `RESISTANCE_BANDS`
- **THEN** candidate pool includes bodyweight, dumbbell, and band exercises

### Requirement: Injury and Joint Contraindication Exclusion
The generator SHALL exclude any exercise whose `contraindicated_joints` contains any joint flagged by the user as injured or painful (e.g., `LOWER_BACK`, `KNEE`, `WRIST`, `SHOULDER`, `ANKLE`).

#### Scenario: User reports lower back pain
- **WHEN** user flags `LOWER_BACK` pain
- **THEN** high spinal compression exercises (e.g. Conventional Deadlift, Romanian Deadlift without support, Good Mornings) are omitted, and spine-sparing alternatives (e.g. Chest-Supported DB Row, Glute Bridge) are chosen

### Requirement: Weekly Training Split Selection
Based on the user's committed training frequency ($F$ days/week), the generator SHALL assign a physiologically balanced split:
- $F = 2 \text{ or } 3$: Full Body Split (alternating Full Body A / Full Body B with rest days in between)
- $F = 4$: Upper / Lower Split (Upper A, Lower A, Rest, Upper B, Lower B, Rest, Rest)
- $F = 5 \text{ or } 6$: Push / Pull / Legs or Focus Split

#### Scenario: 3 days per week chosen
- **WHEN** user commits to 3 days/week
- **THEN** routine generates Full Body workout on non-consecutive days with balanced compound movements

### Requirement: Session Volume Allocation
Each session SHALL allocate between 4 and 7 exercises comprising 1 primary compound movement, 2 secondary compound movements, and 2-3 isolation / accessory movements targeted to the user's focus goals, totaling 15-25 working sets per session within the user's allocated daily time limit.

#### Scenario: Time constraint is 30 minutes
- **WHEN** user specifies 30 minutes session duration
- **THEN** generator selects 4 exercises (3 sets each, 60s rest intervals) fitting within 30 minutes
