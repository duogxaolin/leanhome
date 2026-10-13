package com.leanhome.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.leanhome.core.data.entities.WeightLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: WeightLogEntity)

    @Query("SELECT * FROM weight_logs WHERE user_id = :userId ORDER BY recorded_at ASC")
    fun getLogsByUser(userId: String): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_logs WHERE user_id = :userId ORDER BY recorded_at ASC")
    suspend fun getLogsByUserOnce(userId: String): List<WeightLogEntity>

    @Query("SELECT * FROM weight_logs WHERE user_id = :userId ORDER BY recorded_at DESC LIMIT 1")
    suspend fun getLatestLog(userId: String): WeightLogEntity?

    @Query("SELECT COUNT(*) FROM weight_logs WHERE user_id = :userId")
    suspend fun count(userId: String): Int
}
