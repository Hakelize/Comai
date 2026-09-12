package com.comai.contextengine.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ComaiDatabase - Main Room Database for User Profile, Daily Logs, Routine Programs, and Personalization State.
 */
@Database(
    entities = [
        UserProfile::class,
        DailyLog::class,
        Program::class,
        PersonalizationState::class,
        Memory::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ComaiDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun programDao(): ProgramDao
    abstract fun personalizationStateDao(): PersonalizationStateDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        const val DATABASE_NAME = "comai_core_db"

        @Volatile
        private var INSTANCE: ComaiDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `key` TEXT NOT NULL,
                        `value` TEXT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `isUserDeletable` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): ComaiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ComaiDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val instance = getInstance(context)
                    instance.userProfileDao().insertOrUpdateProfile(UserProfile())
                    
                    val defaultPrograms = listOf(
                        Program(dayOfWeek = "MONDAY", isWorkDay = true),
                        Program(dayOfWeek = "TUESDAY", isWorkDay = true),
                        Program(dayOfWeek = "WEDNESDAY", isWorkDay = true),
                        Program(dayOfWeek = "THURSDAY", isWorkDay = true),
                        Program(dayOfWeek = "FRIDAY", isWorkDay = true),
                        Program(dayOfWeek = "SATURDAY", isWorkDay = false, expectedWakeTime = "08:30"),
                        Program(dayOfWeek = "SUNDAY", isWorkDay = false, expectedWakeTime = "08:30")
                    )
                    instance.programDao().insertPrograms(defaultPrograms)
                }
            }
        }
    }
}
