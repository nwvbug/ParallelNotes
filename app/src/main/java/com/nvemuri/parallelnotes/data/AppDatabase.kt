package com.nvemuri.parallelnotes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nvemuri.parallelnotes.data.entities.CanvasItemEntity
import com.nvemuri.parallelnotes.data.entities.NoteEntity

// 1. The Annotation: Tell Room which entities belong to this database.
// If you ever add a new table or change columns later, you MUST increment the version number!
@Database(
    entities = [NoteEntity::class, CanvasItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // 2. The DAO Getters: Tell the database about your query interfaces
    abstract fun noteDao(): NoteDao

    // 3. The Singleton Companion Object
    companion object {
        // @Volatile means changes made by one thread are immediately visible to other threads
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // If the Instance is not null, return it.
            // Otherwise, step into the synchronized block to create it.
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parallelnotes_database" // The actual name of the file on the hard drive
                )
                    // If you change the version number without writing a migration,
                    // this cleanly wipes the DB instead of crashing the app.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}