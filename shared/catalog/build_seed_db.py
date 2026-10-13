#!/usr/bin/env python3
"""
LeanHome Exercise Catalog Seed Database Builder

Reads exercises_seed.json and outputs a pre-packaged SQLite database
(exercises_seed.db) compatible with Room (Android) and SwiftData (iOS).

Usage:
    python3 build_seed_db.py

Output:
    shared/catalog/exercises_seed.db
"""

import json
import sqlite3
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_JSON = os.path.join(SCRIPT_DIR, "exercises_seed.json")
OUTPUT_DB = os.path.join(SCRIPT_DIR, "exercises_seed.db")

SCHEMA_SQL = """
-- Catalog Exercises Table
CREATE TABLE exercises (
    id TEXT PRIMARY KEY,
    name_en TEXT NOT NULL,
    name_vi TEXT NOT NULL,
    primary_muscles TEXT NOT NULL,        -- JSON array: ["CHEST_MID", "TRICEPS"]
    secondary_muscles TEXT NOT NULL,      -- JSON array: ["SHOULDERS", "CORE"]
    required_equipment TEXT NOT NULL,    -- JSON array: ["DUMBBELLS"]
    contraindicated_joints TEXT NOT NULL, -- JSON array: ["LOWER_BACK"]
    difficulty_level TEXT NOT NULL,       -- "BEGINNER", "INTERMEDIATE", "ADVANCED"
    video_url TEXT NOT NULL,
    fallback_thumbnail_url TEXT NOT NULL,
    common_mistakes TEXT NOT NULL,        -- JSON array of strings
    correct_form_cues TEXT NOT NULL      -- JSON array of strings
);

-- User Profiles Table
CREATE TABLE user_profiles (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    age INTEGER NOT NULL,
    gender TEXT NOT NULL,
    height_cm REAL NOT NULL,
    weight_kg REAL NOT NULL,
    country_code TEXT NOT NULL,
    body_shape TEXT NOT NULL,
    fitness_goal TEXT NOT NULL,
    target_muscle_group TEXT,
    available_equipment TEXT NOT NULL,
    injury_flags TEXT NOT NULL,
    activity_level TEXT NOT NULL DEFAULT 'SEDENTARY',
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
    status TEXT NOT NULL,
    exercises TEXT NOT NULL DEFAULT '[]',  -- JSON array of session_exercise objects
    completed_duration_seconds INTEGER,
    rpe_score INTEGER,
    feedback_tags TEXT,                    -- JSON array
    feedback_notes TEXT,
    created_at INTEGER NOT NULL
);

-- Indexes for common queries
CREATE INDEX idx_exercises_equipment ON exercises(required_equipment);
CREATE INDEX idx_exercises_difficulty ON exercises(difficulty_level);
CREATE INDEX idx_sessions_user ON workout_sessions(user_id);
CREATE INDEX idx_sessions_status ON workout_sessions(status);
"""


def build_database():
    # Remove existing DB
    if os.path.exists(OUTPUT_DB):
        os.remove(OUTPUT_DB)

    conn = sqlite3.connect(OUTPUT_DB)
    cursor = conn.cursor()

    # Create schema
    cursor.executescript(SCHEMA_SQL)

    # Load and insert exercises
    with open(INPUT_JSON, 'r', encoding='utf-8') as f:
        exercises = json.load(f)

    for ex in exercises:
        cursor.execute(
            """INSERT INTO exercises (
                id, name_en, name_vi, primary_muscles, secondary_muscles,
                required_equipment, contraindicated_joints, difficulty_level,
                video_url, fallback_thumbnail_url, common_mistakes, correct_form_cues
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                ex["id"],
                ex["name_en"],
                ex["name_vi"],
                json.dumps(ex["primary_muscles"], ensure_ascii=False),
                json.dumps(ex["secondary_muscles"], ensure_ascii=False),
                json.dumps(ex["required_equipment"], ensure_ascii=False),
                json.dumps(ex["contraindicated_joints"], ensure_ascii=False),
                ex["difficulty_level"],
                ex["video_url"],
                ex["fallback_thumbnail_url"],
                json.dumps(ex["common_mistakes"], ensure_ascii=False),
                json.dumps(ex["correct_form_cues"], ensure_ascii=False),
            )
        )

    conn.commit()

    # Verify counts
    cursor.execute("SELECT COUNT(*) FROM exercises")
    count = cursor.fetchone()[0]
    print(f"✅ Inserted {count} exercises into {OUTPUT_DB}")

    # Test constraint queries
    # 1. Bodyweight only exercises (no equipment tags other than BODYWEIGHT_ONLY)
    cursor.execute(
        "SELECT COUNT(*) FROM exercises WHERE required_equipment = '[\"BODYWEIGHT_ONLY\"]'"
    )
    bw_count = cursor.fetchone()[0]
    print(f"   Bodyweight-only exercises: {bw_count}")

    # 2. Exercises with LOWER_BACK contraindication
    cursor.execute(
        "SELECT COUNT(*) FROM exercises WHERE contraindicated_joints LIKE '%LOWER_BACK%'"
    )
    lb_count = cursor.fetchone()[0]
    print(f"   Exercises contraindicated for LOWER_BACK: {lb_count}")

    # 3. Dumbbell exercises
    cursor.execute(
        "SELECT COUNT(*) FROM exercises WHERE required_equipment LIKE '%DUMBBELLS%'"
    )
    db_count = cursor.fetchone()[0]
    print(f"   Dumbbell exercises: {db_count}")

    # 4. Band exercises
    cursor.execute(
        "SELECT COUNT(*) FROM exercises WHERE required_equipment LIKE '%RESISTANCE_BANDS%'"
    )
    band_count = cursor.fetchone()[0]
    print(f"   Resistance band exercises: {band_count}")

    # 5. Beginner exercises
    cursor.execute(
        "SELECT COUNT(*) FROM exercises WHERE difficulty_level = 'BEGINNER'"
    )
    beginner_count = cursor.fetchone()[0]
    print(f"   Beginner exercises: {beginner_count}")

    conn.close()
    print(f"\n✅ Database built: {OUTPUT_DB}")


if __name__ == "__main__":
    build_database()
