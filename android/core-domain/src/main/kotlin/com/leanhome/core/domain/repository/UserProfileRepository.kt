package com.leanhome.core.domain.repository

import com.leanhome.core.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    fun getCurrentProfile(): Flow<UserProfile?>
    suspend fun getCurrentProfileOnce(): UserProfile?
    suspend fun insert(profile: UserProfile)
    suspend fun update(profile: UserProfile)
    suspend fun getById(id: String): UserProfile?
    suspend fun count(): Int
}
