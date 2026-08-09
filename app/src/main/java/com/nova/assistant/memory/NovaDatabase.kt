package com.nova.assistant.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MemoryEntity::class, RoutineEntity::class], version = 1)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun routineDao(): RoutineDao

    companion object {
        @Volatile private var INSTANCE: NovaDatabase? = null

        fun get(context: Context): NovaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NovaDatabase::class.java,
                    "nova.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
