package com.leanhome.core.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the weight_logs table.
 *
 * Tracks weekly weigh-in measurements independently of workout sessions so
 * the dashboard can render weight trends and projection even when the user
 * has not completed a workout. Per the spec, weigh-ins are recorded in the
 * morning, fasting, under consistent conditions.
 */
@Entity(
    tableName = "weight_logs",
    indices = [
        Index(value = ["user_id"], name = "idx_weight_logs_user"),
        Index(value = ["recorded_at"], name = "idx_weight_logs_recorded_at"),
    ]
)
data class WeightLogEntity(
    @PrimaryKey val id: String,
    val user_id: String,
    val weight_kg: Double,
    val recorded_at: Long,
)
