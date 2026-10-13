package com.leanhome.core.data.database

import android.content.Context
import androidx.room.Room
import com.leanhome.core.data.db.LeanHomeDatabase

/**
 * Factory for creating/obtaining the Room [LeanHomeDatabase] instance.
 *
 * Uses createFromAsset to pre-populate from the bundled exercises_seed.db
 * catalog (60 exercises). The user_profiles and workout_sessions tables
 * are created empty (both present in the asset DB schema).
 */
object DatabaseProvider {

    @Volatile
    private var INSTANCE: LeanHomeDatabase? = null
    private val lock = Any()

    fun get(context: Context): LeanHomeDatabase {
        return INSTANCE ?: synchronized(lock) {
            INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
        }
    }

    private fun buildDatabase(context: Context): LeanHomeDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            LeanHomeDatabase::class.java,
            LeanHomeDatabase.DATABASE_NAME,
        )
            .createFromAsset(LeanHomeDatabase.ASSET_PATH)
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * Test-only helper — creates an in-memory database.
     */
    fun buildInMemory(context: Context): LeanHomeDatabase {
        return Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            LeanHomeDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    fun reset() {
        synchronized(lock) {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
