package com.leanhome.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.leanhome.core.data.entities.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfileEntity)

    @Update
    suspend fun update(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profiles ORDER BY created_at DESC LIMIT 1")
    fun getCurrentProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles ORDER BY created_at DESC LIMIT 1")
    suspend fun getCurrentProfileOnce(): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getById(id: String): UserProfileEntity?

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun count(): Int
}
