package com.sushant.inknote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class, PageEntity::class, FolderEntity::class, TagEntity::class,
        NoteTagEntity::class, RecordingEntity::class, SearchFts::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun pages(): PageDao
    abstract fun folders(): FolderDao
    abstract fun tags(): TagDao
    abstract fun recordings(): RecordingDao
    abstract fun search(): SearchDao

    companion object {
        /** Version 1 is the first release. Every later schema change adds an explicit Migration here (never destructive). */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

        fun build(context: Context, inMemory: Boolean = false): AppDatabase {
            val b = if (inMemory) Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            else Room.databaseBuilder(context, AppDatabase::class.java, "inknote.db")
            return b.addMigrations(*MIGRATIONS).build()
        }
    }
}
