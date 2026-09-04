package com.shortscap.app.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Screen Activity durable storage — mirrors the backend `app_usage` summary
 * shape (device, package, date, duration, launches) so sessions captured
 * while the backend is unavailable survive process death and are drained to
 * the backend by the existing sync layer after restart.
 *
 * One row per closed foreground session (launchCount = 1); [ScreenActivityDao]
 * consumers aggregate per (package, date) before enqueueing — matching the
 * idempotent per-day upsert the backend `POST /monitoring/app-usage/sync`
 * contract uses (last sync wins, never doubles).
 */
@Entity(tableName = "screen_activity_usage")
data class ScreenActivityUsageEntity(
    @PrimaryKey(autoGenerate = true) val usageId: Long = 0L,
    val packageName: String,
    val appName: String? = null,
    /** UTC calendar date key (YYYY-MM-DD) of the usage. */
    val usageDate: String,
    val durationSeconds: Long,
    val launchCount: Int,
    val occurredAt: Long,
)

@Dao
interface ScreenActivityDao {

    @Insert
    suspend fun insertUsage(usage: ScreenActivityUsageEntity): Long

    /** Unsynced session rows in insertion order (oldest first). */
    @Query("SELECT * FROM screen_activity_usage ORDER BY usageId ASC")
    suspend fun usageSnapshot(): List<ScreenActivityUsageEntity>

    /** Closed sessions whose occurredAt is in [rangeStart, rangeEnd) — used by
     *  Activity/Home reporting (local-calendar-day bucketing happens in the
     *  reporting layer, never here). Read-only; no schema change. */
    @Query("SELECT * FROM screen_activity_usage WHERE occurredAt >= :rangeStart AND occurredAt < :rangeEnd ORDER BY occurredAt ASC")
    suspend fun usageInRange(rangeStart: Long, rangeEnd: Long): List<ScreenActivityUsageEntity>

    /** Most recent closed sessions (Home Recent Activity), newest first. */
    @Query("SELECT * FROM screen_activity_usage ORDER BY occurredAt DESC LIMIT :limit")
    suspend fun recentSessions(limit: Int): List<ScreenActivityUsageEntity>

    /** Clears local session rows (after they were drained to the queue). */
    @Query("DELETE FROM screen_activity_usage")
    suspend fun clear()
}
