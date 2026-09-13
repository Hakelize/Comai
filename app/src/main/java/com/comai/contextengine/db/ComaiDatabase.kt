package com.comai.contextengine.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.comai.contextengine.database.ContextLogDao
import com.comai.contextengine.database.ContextLogEntity
import com.comai.contextengine.database.ProactiveEventDao
import com.comai.contextengine.database.ProactiveEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ComaiDatabase - Main Room Database for User Profile, Daily Logs, Routine Programs, Personalization State, Context Logs, and Proactive Events.
 */
@Database(
    entities = [
        UserProfile::class,
        DailyLog::class,
        Program::class,
        PersonalizationState::class,
        Memory::class,
        ContextLogEntity::class,
        ProactiveEventEntity::class,
        com.comai.digitalactivity.data.AppLimitEntity::class,
        com.comai.digitalactivity.data.DailyActivitySummaryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class ComaiDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun programDao(): ProgramDao
    abstract fun personalizationStateDao(): PersonalizationStateDao
    abstract fun memoryDao(): MemoryDao
    abstract fun contextLogDao(): ContextLogDao
    abstract fun proactiveEventDao(): ProactiveEventDao
    abstract fun appLimitDao(): com.comai.digitalactivity.data.AppLimitDao
    abstract fun dailyActivityDao(): com.comai.digitalactivity.data.DailyActivitySummaryDao

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

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `context_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `task` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `signalsSummary` TEXT NOT NULL,
                        `isEscalated` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `proactive_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        `targetTask` TEXT NOT NULL,
                        `targetConstraints` TEXT NOT NULL,
                        `contextSummary` TEXT NOT NULL,
                        `isAcknowledged` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `context_logs` ADD COLUMN `locationPlace` TEXT NOT NULL DEFAULT 'Home'")
                db.execSQL("ALTER TABLE `context_logs` ADD COLUMN `activityType` TEXT NOT NULL DEFAULT 'STILL'")
                db.execSQL("ALTER TABLE `context_logs` ADD COLUMN `routineState` TEXT NOT NULL DEFAULT 'WORK_DAY_ROUTINE'")
                db.execSQL("ALTER TABLE `context_logs` ADD COLUMN `isRoutineDeviation` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `context_logs` ADD COLUMN `deviationMinutes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `context_logs` ADD COLUMN `confidenceScore` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `context_logs` ADD COLUMN `confidenceLevel` TEXT NOT NULL DEFAULT 'HIGH'")
                db.execSQL("ALTER TABLE `context_logs` ADD COLUMN `triggerTask` TEXT NOT NULL DEFAULT 'GENERAL_CHECKIN'")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_limits` (
                        `packageName` TEXT NOT NULL PRIMARY KEY,
                        `appName` TEXT NOT NULL,
                        `dailyLimitMinutes` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `lastNotifiedDate` TEXT NOT NULL DEFAULT '',
                        `createdAtMs` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_activity_summaries` (
                        `date` TEXT NOT NULL PRIMARY KEY,
                        `totalScreenTimeMinutes` INTEGER NOT NULL,
                        `firstActiveTimeMs` INTEGER NOT NULL,
                        `lastActiveTimeMs` INTEGER NOT NULL,
                        `topPackageName` TEXT NOT NULL,
                        `topCategory` TEXT NOT NULL,
                        `inferredWakeTimeMs` INTEGER,
                        `inferredSleepTimeMs` INTEGER
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
                    MockDatabaseSeeder.seedMockData(instance)
                }
            }
        }
    }
}
