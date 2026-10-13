package com.leanhome.core.data.repository

import com.leanhome.core.data.dao.ExerciseDao
import com.leanhome.core.data.mapper.EntityMappers
import com.leanhome.core.domain.model.Equipment
import com.leanhome.core.domain.model.Exercise
import com.leanhome.core.domain.model.Joint
import com.leanhome.core.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExerciseRepositoryImpl(
    private val exerciseDao: ExerciseDao,
) : ExerciseRepository {

    override fun getAll(): Flow<List<Exercise>> =
        exerciseDao.getAll().map { entities ->
            entities.map { EntityMappers.exerciseEntityToDomain(it) }
        }

    override suspend fun getAllOnce(): List<Exercise> =
        exerciseDao.getAllOnce().map { EntityMappers.exerciseEntityToDomain(it) }

    override suspend fun getById(id: String): Exercise? =
        exerciseDao.getById(id)?.let { EntityMappers.exerciseEntityToDomain(it) }

    override suspend fun filterExercises(
        userEquipment: Set<Equipment>,
        injuryFlags: Set<Joint>,
        maxDifficulty: com.leanhome.core.domain.model.DifficultyLevel,
        targetMuscles: Set<com.leanhome.core.domain.model.Muscle>,
    ): List<Exercise> {
        // Fetch all exercises from DB and filter at the repository layer
        // for exact equipment containment and joint exclusion (SQLite LIKE
        // cannot express "all of array A contained in array B").
        val all = exerciseDao.getAllForFilterting().map { EntityMappers.exerciseEntityToDomain(it) }
        val order = listOf(
            com.leanhome.core.domain.model.DifficultyLevel.BEGINNER,
            com.leanhome.core.domain.model.DifficultyLevel.INTERMEDIATE,
            com.leanhome.core.domain.model.DifficultyLevel.ADVANCED,
        )
        val maxIdx = order.indexOf(maxDifficulty)

        return all.filter { ex ->
            ex.isAvailableWith(userEquipment) &&
            !ex.isContraindicatedFor(injuryFlags) &&
            order.indexOf(ex.difficultyLevel) <= maxIdx &&
            (targetMuscles.isEmpty() || ex.primaryMuscles.any { it in targetMuscles } || ex.secondaryMuscles.any { it in targetMuscles })
        }
    }

    override suspend fun insertAll(exercises: List<Exercise>) =
        exerciseDao.insertAll(exercises.map { EntityMappers.exerciseDomainToEntity(it) })
}
