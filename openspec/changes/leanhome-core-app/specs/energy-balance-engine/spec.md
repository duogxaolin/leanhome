# Spec Delta: Energy Balance Engine

## Purpose
Calculates BMR and TDEE using the Mifflin-St Jeor equation, maps weight loss goals to required daily caloric deficits, and regulates dynamic target timelines with physiological safety ceilings.

## ADDED Requirements

### Requirement: BMR and TDEE Calculation
The system SHALL calculate Basal Metabolic Rate (BMR) using Mifflin-St Jeor:
- Male: $10 \times \text{weight\_kg} + 6.25 \times \text{height\_cm} - 5 \times \text{age} + 5$
- Female: $10 \times \text{weight\_kg} + 6.25 \times \text{height\_cm} - 5 \times \text{age} - 161$
Total Daily Energy Expenditure (TDEE) SHALL be computed by multiplying BMR with the Physical Activity Level (PAL): Sedentary (1.2), Lightly Active (1.375), Moderately Active (1.55), Very Active (1.725).

#### Scenario: BMR and TDEE computation for 25yo male
- **WHEN** user is male, 25 years old, 75 kg, 175 cm, sedentary activity level (1.2)
- **THEN** calculated BMR is 1723.75 kcal and TDEE is 2068.5 kcal

### Requirement: Dynamic Timeline Deficit Calculation
The system SHALL calculate the required daily caloric deficit based on the target weight reduction ($\Delta W$ in kg, assuming $1\text{ kg fat} \approx 7700\text{ kcal}$) over a user-selected duration ($D$ in days):
$$\text{Daily Deficit} = \frac{\Delta W \times 7700}{D}$$

#### Scenario: Normal 60-day target timeline
- **WHEN** user targets losing 5 kg in 60 days
- **THEN** required daily deficit is approximately 641.67 kcal/day

### Requirement: Physiological Safety Thresholds & Warnings
The system SHALL enforce safety ceilings:
1. Maximum safe weight loss rate: $1\%$ of current body weight per week.
2. Maximum safe daily caloric deficit: $25\%$ of TDEE or $1000\text{ kcal/day}$ (whichever is lower).
3. Minimum caloric intake floor: $1200\text{ kcal/day}$ for females and $1500\text{ kcal/day}$ for males.

#### Scenario: User shortens timeline to 30 days (Extreme deficit trigger)
- **WHEN** 75 kg male with TDEE 2068 kcal targets 5 kg loss in 30 days (requiring 1283 kcal/day deficit > 25% TDEE limit)
- **THEN** system flags the selection as "High Risk / Extreme Deficit", clamps pure workout-based deficit to safe maximum, and displays a mandatory clinical disclaimer requiring dietary intervention and warning against muscle loss.

### Requirement: Maintenance Goal Handling
The system SHALL support a `HEALTH_MAINTENANCE` goal where no weight change is targeted. In this mode, the system SHALL skip deficit calculation and set the daily caloric target to TDEE (zero deficit), and the workout generator SHALL focus on cardiovascular and general conditioning rather than fat-loss volume.

#### Scenario: User selects health maintenance goal
- **WHEN** user selects `HEALTH_MAINTENANCE` as fitness goal
- **THEN** system sets daily caloric target equal to TDEE with zero deficit, skips timeline slider, and workout generator prioritizes general conditioning
