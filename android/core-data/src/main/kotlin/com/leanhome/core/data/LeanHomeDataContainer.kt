package com.leanhome.core.data

import android.content.Context
import com.leanhome.core.data.database.DatabaseProvider
import com.leanhome.core.data.db.LeanHomeDatabase
import com.leanhome.core.data.repository.ExerciseRepositoryImpl
import com.leanhome.core.data.repository.UserProfileRepositoryImpl
import com.leanhome.core.data.repository.WorkoutSessionRepositoryImpl
import com.leanhome.core.domain.repository.ExerciseRepository
import com.leanhome.core.domain.repository.UserProfileRepository
import com.leanhome.core.domain.repository.WorkoutSessionRepository

/**
 * Lightweight dependency container — wires up Room database and repositories.
 * Pattern: singletons scoped to application context.
 */
class LeanHomeDataContainer(private val context: Context) {

    val database: LeanHomeDatabase by lazy { DatabaseProvider.get(context) }

    val exerciseRepository: ExerciseRepository by lazy {
        ExerciseRepositoryImpl(database.exerciseDao())
    }

    val userProfileRepository: UserProfileRepository by lazy {
        UserProfileRepositoryImpl(database.userProfileDao())
    }

    val workoutSessionRepository: WorkoutSessionRepository by lazy {
        WorkoutSessionRepositoryImpl(database.workoutSessionDao())
    }
}
