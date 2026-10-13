package com.leanhome.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.leanhome.core.data.dao.ExerciseDao
import com.leanhome.core.data.dao.UserProfileDao
import com.leanhome.core.data.dao.WeightLogDao
import com.leanhome.core.data.dao.WorkoutSessionDao
import com.leanhome.core.data.entities.ExerciseEntity
import com.leanhome.core.data.entities.UserProfileEntity
import com.leanhome.core.data.entities.WeightLogEntity
import com.leanhome.core.data.entities.WorkoutSessionEntity

@Database(
    entities = [
        ExerciseEntity::class,
        UserProfileEntity::class,
        WeightLogEntity::class,
        WorkoutSessionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LeanHomeDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun weightLogDao(): WeightLogDao
    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        const val DATABASE_NAME = "exercises_seed.db"
        const val ASSET_PATH = "exercises_seed.db"
    }
}
