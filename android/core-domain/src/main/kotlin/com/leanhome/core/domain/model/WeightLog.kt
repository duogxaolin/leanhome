package com.leanhome.core.domain.model

import kotlinx.serialization.Serializable

/**
 * A single weight measurement recorded by the user. Stored on a dedicated
 * weight_logs table so weigh-ins can be tracked independently of workout
 * sessions (the spec requires weekly weigh-in prompts even when the user
 * has completed no sessions in the window).
 *
 * Recorded in the morning, fasting, under consistent conditions.
 */
@Serializable
data class WeightLog(
    val id: String,
    val userId: String,
    val weightKg: Double,
    val recordedAt: Long,
)
