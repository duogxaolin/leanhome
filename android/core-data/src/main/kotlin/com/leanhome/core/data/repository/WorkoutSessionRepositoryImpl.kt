package com.leanhome.core.data.repository

import com.leanhome.core.data.dao.WorkoutSessionDao
import com.leanhome.core.data.mapper.EntityMappers
import com.leanhome.core.domain.model.FeelingTag
import com.leanhome.core.domain.model.SessionStatus
import com.leanhome.core.domain.model.WorkoutSession
import com.leanhome.core.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkoutSessionRepositoryImpl(
    private val workoutSessionDao: WorkoutSessionDao,
) : WorkoutSessionRepository {

    override fun getSessionsByUser(userId: String): Flow<List<WorkoutSession>> =
        workoutSessionDao.getSessionsByUser(userId).map { entities ->
            entities.map { EntityMappers.sessionEntityToDomain(it) }
        }

    override suspend fun getSessionsByUserOnce(userId: String): List<WorkoutSession> =
        workoutSessionDao.getSessionsByUserOnce(userId).map { EntityMappers.sessionEntityToDomain(it) }

    override suspend fun getSessionsByDateRange(
        userId: String,
        startDate: String,
        endDate: String,
    ): List<WorkoutSession> =
        workoutSessionDao.getSessionsByDateRange(userId, startDate, endDate).map {
            EntityMappers.sessionEntityToDomain(it)
        }

    override suspend fun getById(id: String): WorkoutSession? =
        workoutSessionDao.getById(id)?.let { EntityMappers.sessionEntityToDomain(it) }

    override suspend fun insert(session: WorkoutSession) =
        workoutSessionDao.insert(EntityMappers.sessionDomainToEntity(session))

    override suspend fun update(session: WorkoutSession) =
        workoutSessionDao.update(EntityMappers.sessionDomainToEntity(session))

    override suspend fun updateRpeAndStatus(
        sessionId: String,
        rpeScore: Int,
        status: SessionStatus,
        completedDurationSeconds: Int?,
        feedbackTags: List<FeelingTag>,
        feedbackNotes: String?,
    ) {
        val tagsJson = if (feedbackTags.isEmpty()) null
            else kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer<String>()),
                feedbackTags.map { it.name }
            )
        workoutSessionDao.updateRpeAndStatus(
            sessionId = sessionId,
            rpeScore = rpeScore,
            status = status.name,
            completedDurationSeconds = completedDurationSeconds,
            feedbackTags = tagsJson,
            feedbackNotes = feedbackNotes,
        )
    }

    override suspend fun getRecentCompletedSessions(
        userId: String,
        limit: Int,
    ): List<WorkoutSession> =
        workoutSessionDao.getRecentCompletedSessions(userId, limit).map {
            EntityMappers.sessionEntityToDomain(it)
        }
}
