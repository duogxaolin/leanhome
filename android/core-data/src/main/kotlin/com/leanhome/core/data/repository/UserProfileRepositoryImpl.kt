package com.leanhome.core.data.repository

import com.leanhome.core.data.dao.UserProfileDao
import com.leanhome.core.data.mapper.EntityMappers
import com.leanhome.core.domain.model.UserProfile
import com.leanhome.core.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserProfileRepositoryImpl(
    private val userProfileDao: com.leanhome.core.data.dao.UserProfileDao,
) : UserProfileRepository {

    override fun getCurrentProfile(): Flow<UserProfile?> =
        userProfileDao.getCurrentProfile().map { entity ->
            entity?.let { EntityMappers.profileEntityToDomain(it) }
        }

    override suspend fun getCurrentProfileOnce(): UserProfile? =
        userProfileDao.getCurrentProfileOnce()?.let { EntityMappers.profileEntityToDomain(it) }

    override suspend fun insert(profile: UserProfile) =
        userProfileDao.insert(EntityMappers.profileDomainToEntity(profile))

    override suspend fun update(profile: UserProfile) =
        userProfileDao.update(EntityMappers.profileDomainToEntity(profile))

    override suspend fun getById(id: String): UserProfile? =
        userProfileDao.getById(id)?.let { EntityMappers.profileEntityToDomain(it) }

    override suspend fun count(): Int = userProfileDao.count()
}
