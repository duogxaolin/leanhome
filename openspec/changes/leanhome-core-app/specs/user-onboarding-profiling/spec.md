# Spec Delta: User Onboarding and Profiling

## Purpose
Collects user biometric parameters, evaluates regional body mass and composition benchmarks (WHO international vs WHO Asian standards), and records 2D body shape archetypes for personalized fitness guidance.

## ADDED Requirements

### Requirement: Biometric Data Collection
The system SHALL collect and validate basic user profile attributes: name (string between 1 and 50 characters), age (integer between 13 and 99), gender (Male, Female, Other), height in cm (positive float between 100.0 and 250.0), weight in kg (positive float between 30.0 and 300.0), and ISO country code.

#### Scenario: Valid inputs provided
- **WHEN** user submits name "Nguyen Van A", age 28, gender Male, height 175.0 cm, weight 78.0 kg, country "VN"
- **THEN** profile validation succeeds, computes BMI = 25.47 kg/m², and stores name as display identity

#### Scenario: Invalid height or weight provided
- **WHEN** user inputs height 0 or weight -5
- **THEN** validation fails with explicit field error and prevents onboarding progression

### Requirement: Regional Health Classification
The system SHALL classify BMI according to regional standards based on country code. For countries in the WHO Western Pacific Region (e.g. "VN", "JP", "KR", "SG", "TH"), the system SHALL use the Asian BMI threshold:
- Underweight: BMI < 18.5
- Normal: 18.5 <= BMI < 23.0
- Overweight / Pre-obese: 23.0 <= BMI < 25.0
- Obese: BMI >= 25.0
For all other countries, the international WHO standard SHALL be used:
- Underweight: BMI < 18.5
- Normal: 18.5 <= BMI < 25.0
- Overweight: 25.0 <= BMI < 30.0
- Obese: BMI >= 30.0

#### Scenario: Asian standard classification
- **WHEN** country is "VN" and BMI is 24.0
- **THEN** system classifies status as "Overweight / Pre-obese (Asian WHO Standard)" with context explanation

#### Scenario: International standard classification
- **WHEN** country is "US" and BMI is 24.0
- **THEN** system classifies status as "Normal weight (International WHO Standard)"

### Requirement: 2D Body Shape Archetype Selection (V1)
The system SHALL present 2D visual body shape archetypes for selection based on gender:
- Male: Inverted Triangle (Athletic/V-Taper), Rectangle (Straight/Ectomorph), Oval/Round (Endomorph), Triangle (Pear/Lower body heavy).
- Female: Hourglass, Pear, Inverted Triangle, Rectangle, Apple.

#### Scenario: User selects body archetype
- **WHEN** male user selects Inverted Triangle
- **THEN** system records archetype and updates baseline muscle distribution profile

### Requirement: On-Device Camera Body Scan (V2)
The system SHALL offer an optional camera-based body shape analysis mode using on-device pose estimation. This feature MUST process all data locally — zero body photos or biometric data SHALL be uploaded to any server.

On iOS, the system SHALL use Apple Vision Framework (VNDetectHumanBodyPoseRequest) to detect shoulder, hip, and waist landmarks. On Android, the system SHALL use Google MediaPipe Pose Landmarker to detect the same landmarks.

The system SHALL compute:
1. Shoulder-to-Waist Ratio (SWR): shoulder_width / waist_width
2. Waist-to-Hip Ratio (WHR): waist_width / hip_width

The system SHALL map ratios to existing body archetypes:
- SWR > 1.4 and WHR < 0.85 → INVERTED_TRIANGLE
- SWR 1.0-1.4 and WHR 0.85-1.0 → RECTANGLE
- WHR > 0.9 (male) or > 0.85 (female) → OVAL/TRIANGLE/APPLE
- WHR < 0.85 (female) → PEAR

#### Scenario: User opts for camera scan
- **WHEN** user selects 'Scan My Body' option during archetype selection step
- **THEN** system opens camera, displays real-time silhouette guide overlay, captures pose landmarks on-device, computes SWR + WHR, maps to archetype, and auto-selects it

#### Scenario: User declines camera permission or scan fails
- **WHEN** camera permission denied or pose detection returns insufficient landmarks
- **THEN** system falls back to manual 2D archetype selection with no error
