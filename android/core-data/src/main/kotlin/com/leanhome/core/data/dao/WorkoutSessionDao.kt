package com.leanhome.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.leanhome.core.data.entities.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: WorkoutSessionEntity)

    @Update
    suspend fun update(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE user_id = :userId ORDER BY scheduled_date DESC")
    fun getSessionsByUser(userId: String): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE user_id = :userId ORDER BY scheduled_date DESC")
    suspend fun getSessionsByUserOnce(userId: String): List<WorkoutSessionEntity>

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE user_id = :userId
        AND scheduled_date >= :startDate
        AND scheduled_date <= :endDate
        ORDER BY scheduled_date ASC
        """
    )
    suspend fun getSessionsByDateRange(
        userId: String,
        startDate: String,
        endDate: String,
    ): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: String): WorkoutSessionEntity?

    /**
     * Update RPE and status of a session.
     */
    @Query(
        """
        UPDATE workout_sessions
        SET rpe_score = :rpeScore,
            status = :status,
            completed_duration_seconds = :completedDurationSeconds,
            feedback_tags = :feedbackTags,
            feedback_notes = :feedbackNotes
        WHERE id = :sessionId
        """
    )
    suspend fun updateRpeAndStatus(
        sessionId: String,
        rpeScore: Int,
        status: String,
        completedDurationSeconds: Int?,
        feedbackTags: String?,
        feedbackNotes: String?,
    )

    /**
     * Get recent N sessions for fatigue auto-regulation.
     */
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE user_id = :userId
        AND status = 'COMPLETED'
        AND rpe_score IS NOT NULL
        ORDER BY scheduled_date DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentCompletedSessions(userId: String, limit: Int): List<WorkoutSessionEntity>
}
