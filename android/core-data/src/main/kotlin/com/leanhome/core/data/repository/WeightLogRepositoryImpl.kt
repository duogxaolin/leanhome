package com.leanhome.core.data.repository

import com.leanhome.core.data.dao.WeightLogDao
import com.leanhome.core.data.mapper.EntityMappers
import com.leanhome.core.domain.model.WeightLog
import com.leanhome.core.domain.repository.WeightLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WeightLogRepositoryImpl(
    private val weightLogDao: WeightLogDao,
) : WeightLogRepository {

    override fun getLogsByUser(userId: String): Flow<List<WeightLog>> =
        weightLogDao.getLogsByUser(userId).map { entities ->
            entities.map { EntityMappers.weightLogEntityToDomain(it) }
        }

    override suspend fun getLogsByUserOnce(userId: String): List<WeightLog> =
        weightLogDao.getLogsByUserOnce(userId).map { EntityMappers.weightLogEntityToDomain(it) }

    override suspend fun getLatestLog(userId: String): WeightLog? =
        weightLogDao.getLatestLog(userId)?.let { EntityMappers.weightLogEntityToDomain(it) }

    override suspend fun insert(log: WeightLog) =
        weightLogDao.insert(EntityMappers.weightLogDomainToEntity(log))

    override suspend fun count(userId: String): Int = weightLogDao.count(userId)
}
