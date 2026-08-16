package com.shortscap.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * P1-2/P1-5 durable local persistence.
 *
 * One Room database backs the offline sync queue, the Shorts local store
 * (usage/events awaiting drain) AND the authoritative Shorts limit cycle
 * (P1-5), so all pending synchronization data and the active 24-hour cycle
 * survive process death and app restart.
 *
 * Version 1: P1-2 tables (sync_queue, shorts_usage, shorts_events).
 * Version 2: P1-5 adds shorts_limit_cycle (the authoritative 24-hour window).
 */
@Database(
    entities = [
        SyncQueueEntity::class,
        ShortsUsageEntity::class,
        ShortsEventEntity::class,
        ShortsLimitCycleEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ShortsCapDatabase : RoomDatabase() {

    abstract fun syncQueueDao(): SyncQueueDao

    abstract fun shortsStoreDao(): ShortsStoreDao

    abstract fun shortsLimitCycleDao(): ShortsLimitCycleDao

    companion object {
        private const val DB_NAME = "shortscap.db"

        /**
         * P1-5: adds the shorts_limit_cycle table. Pure additive — existing
         * queues/stores are untouched, so no data is lost.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `shorts_limit_cycle` (
                        `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `limitCount` INTEGER NOT NULL,
                        `currentCount` INTEGER NOT NULL,
                        `cycleDurationMillis` INTEGER NOT NULL,
                        `cycleStartedAt` INTEGER NOT NULL,
                        `cycleExpiresAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `warningTriggered` INTEGER NOT NULL,
                        `limitReached` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }

        @Volatile
        private var instance: ShortsCapDatabase? = null

        /** App-wide singleton. Prefer the instance handed out by [ShortsCapApp]. */
        fun getInstance(context: Context): ShortsCapDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShortsCapDatabase::class.java,
                    DB_NAME,
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
