package com.leanhome.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.leanhome.core.data.entities.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises")
    fun getAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAllOnce(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    /**
     * Filter by equipment using JSON LIKE. An exercise is selectable if
     * ALL of its required_equipment is contained in the user's set.
     * We filter at the application layer for exact containment, but this
     * DAO method supports SQL LIKE for simple one-equipment queries.
     */
    @Query("SELECT * FROM exercises WHERE required_equipment LIKE '%' || :equipmentTag || '%'")
    suspend fun filterByEquipmentContains(equipmentTag: String): List<ExerciseEntity>

    /**
     * Filter by difficulty level.
     */
    @Query("SELECT * FROM exercises WHERE difficulty_level = :difficulty")
    suspend fun filterByDifficulty(difficulty: String): List<ExerciseEntity>

    /**
     * Filter by primary muscles using JSON LIKE.
     */
    @Query("SELECT * FROM exercises WHERE primary_muscles LIKE '%' || :muscleTag || '%'")
    suspend fun filterByPrimaryMuscleContains(muscleTag: String): List<ExerciseEntity>

    /**
     * Exclude exercises whose contraindicated_joints contains the given joint.
     */
    @Query("SELECT * FROM exercises WHERE contraindicated_joints NOT LIKE '%' || :jointTag || '%'")
    suspend fun excludeByContraindicatedJoint(jointTag: String): List<ExerciseEntity>

    /**
     * Combined filter: equipment LIKE, exclude joint, difficulty.
     */
    @Query(
        """
        SELECT * FROM exercises
        WHERE required_equipment LIKE '%' || :equipmentTag || '%'
        AND contraindicated_joints NOT LIKE '%' || :excludedJoint || '%'
        AND difficulty_level = :difficulty
        """
    )
    suspend fun filterEquipmentDifficultyExcludeJoint(
        equipmentTag: String,
        excludedJoint: String,
        difficulty: String,
    ): List<ExerciseEntity>

    /**
     * Get exercises where contraindicated_joints does NOT contain ANY of the excluded joints.
     * Since SQLite JSON LIKE is limited, we fetch all and filter in-repo for exact matching.
     * This method returns all exercises for application-layer filtering.
     */
    @Query("SELECT * FROM exercises")
    suspend fun getAllForFiltering(): List<ExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)
}
