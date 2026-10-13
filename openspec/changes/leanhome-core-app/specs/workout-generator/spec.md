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

### Requirement: Target Zone Muscle Focus Allocation
When the user selects `TARGET_ZONE_TONING` as fitness goal with a specific muscle focus (e.g. CHEST, GLUTES, ARMS, CORE), the generator SHALL allocate at least 50% of isolation / accessory volume to the selected muscle group, while maintaining overall compound movement balance across the full body.

#### Scenario: User targets chest zone
- **WHEN** user selects `TARGET_ZONE_TONING` with focus muscle `CHEST`
- **THEN** generator allocates at least 50% of isolation exercises targeting CHEST (e.g. Push-Up variations, DB Fly, Cable/Band Crossover) while still including leg and back compound movements for balance

### Requirement: User Exercise Curation
During onboarding (and from profile settings thereafter), the system SHALL allow the user to browse the full seeded exercise catalog and mark individual exercises for exclusion based on personal preference, discomfort, or health reasons. The WorkoutGenerator SHALL respect the user's `excluded_exercises` list and SHALL omit every excluded exercise ID from all generated sessions. When the user selects the `full` onboarding mode (no exclusions specified), the generator SHALL use every exercise matching the equipment and injury constraints without applying any exclusion filter.

#### Scenario: User excludes push-ups due to wrist discomfort
- **WHEN** user marks `PUSH_UP` and its variants as excluded during onboarding due to wrist discomfort
- **THEN** the WorkoutGenerator omits `PUSH_UP`, `DIAMOND_PUSH_UP`, `WIDE_PUSH_UP`, and `KNEE_PUSH_UP` from every generated session and substitutes alternative chest pressing movements (e.g. `DB_BENCH_PRESS` if equipment permits)

#### Scenario: User selects full mode
- **WHEN** user selects the `full` onboarding mode without specifying any exclusions
- **THEN** no exclusion filter is applied and the generator selects from the full constrained candidate pool (equipment + injury filtered only)

### Requirement: Fatigue-Aware Session Generation
The WorkoutGenerator SHALL accept an optional `fatigue_adjustment` parameter from the `FatigueRegulation` engine and SHALL apply it to the next generated session's volume. When a deload adjustment is provided (rolling 3-session average RPE >= 9.0), the generator SHALL reduce the planned working-set count on each exercise by 10% (rounded down). When a progressive overload adjustment is provided (rolling 3-session average RPE <= 5.0), the generator SHALL add 1 rep to every set of primary compound movements, or add 1 working set to a primary compound movement when an additional rep cannot be applied.

#### Scenario: Deload triggered reduces working sets
- **WHEN** the FatigueRegulation engine emits a deload adjustment (RPE 9, 10, 9 across the trailing 3 sessions)
- **THEN** a session that would normally prescribe 5 exercises at 4 working sets each is generated with 5 exercises at 3 working sets each (10% reduction applied, rounded down)

#### Scenario: Progressive overload triggered adds a rep
- **WHEN** the FatigueRegulation engine emits a progressive overload adjustment (RPE 4, 5, 4 across the trailing 3 sessions)
- **THEN** the generator adds 1 rep to each set of the primary compound movement (e.g. 4 sets × 8 reps becomes 4 sets × 9 reps) in the next generated session
