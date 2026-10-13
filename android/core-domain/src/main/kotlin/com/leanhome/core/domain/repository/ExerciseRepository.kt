package com.leanhome.core.domain.repository

import com.leanhome.core.domain.model.Equipment
import com.leanhome.core.domain.model.DifficultyLevel
import com.leanhome.core.domain.model.Exercise
import com.leanhome.core.domain.model.Joint
import com.leanhome.core.domain.model.Muscle
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun getAll(): Flow<List<Exercise>>
    suspend fun getAllOnce(): List<Exercise>
    suspend fun getById(id: String): Exercise?
    suspend fun filterExercises(
        userEquipment: Set<Equipment>,
        injuryFlags: Set<Joint>,
        maxDifficulty: DifficultyLevel,
        targetMuscles: Set<Muscle> = emptySet(),
    ): List<Exercise>
    suspend fun insertAll(exercises: List<Exercise>)
}
