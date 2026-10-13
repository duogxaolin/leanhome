package com.leanhome.core.domain.repository

import com.leanhome.core.domain.model.WeightLog
import kotlinx.coroutines.flow.Flow

interface WeightLogRepository {
    fun getLogsByUser(userId: String): Flow<List<WeightLog>>
    suspend fun getLogsByUserOnce(userId: String): List<WeightLog>
    suspend fun getLatestLog(userId: String): WeightLog?
    suspend fun insert(log: WeightLog)
    suspend fun count(userId: String): Int
}
