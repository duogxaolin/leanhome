package com.leanhome.core.domain.repository

import com.leanhome.core.domain.model.FeelingTag
import com.leanhome.core.domain.model.SessionStatus
import com.leanhome.core.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

interface WorkoutSessionRepository {
    fun getSessionsByUser(userId: String): Flow<List<WorkoutSession>>
    suspend fun getSessionsByUserOnce(userId: String): List<WorkoutSession>
    suspend fun getSessionsByDateRange(userId: String, startDate: String, endDate: String): List<WorkoutSession>
    suspend fun getById(id: String): WorkoutSession?
    suspend fun insert(session: WorkoutSession)
    suspend fun update(session: WorkoutSession)
    suspend fun updateRpeAndStatus(
        sessionId: String,
        rpeScore: Int,
        status: SessionStatus,
        completedDurationSeconds: Int?,
        feedbackTags: List<FeelingTag>,
        feedbackNotes: String?,
    )
    suspend fun getRecentCompletedSessions(userId: String, limit: Int): List<WorkoutSession>
}
