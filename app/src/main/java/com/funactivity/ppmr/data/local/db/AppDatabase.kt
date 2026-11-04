package com.funactivity.ppmr.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SyncedFileEntity::class, SyncEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun syncDao(): SyncDao
}
