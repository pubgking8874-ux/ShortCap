package com.shortscap.app.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * P1-2 durable storage for the Shorts local store.
 *
 * Mirrors [com.shortscap.app.shorts.LocalShortsUsage] /
 * [com.shortscap.app.shorts.LocalShortsEvent] so usage/events captured while
 * the backend is unavailable survive process death and are drained to the
 * backend by the existing sync layer after restart (Phase 11B sync boundary).
 * Platform/surface/detection-method identities are stored by enum name;
 * [usageId] / [eventId] preserve insertion order for the snapshots.
 */
@Entity(tableName = "shorts_usage")
data class ShortsUsageEntity(
    @PrimaryKey(autoGenerate = true) val usageId: Long = 0L,
    val platform: String,
    val surface: String,
    val detectionMethod: String,
    val confidence: Float,
    val occurredAt: Long,
    val durationMillis: Long,
    val countDelta: Int,
)

@Entity(tableName = "shorts_events")
data class ShortsEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0L,
    val eventType: String,
    val platform: String,
    val surface: String,
    val detectionMethod: String,
    val confidence: Float,
    val occurredAt: Long,
    val durationMillis: Long,
)

@Dao
interface ShortsStoreDao {

    @Insert
    suspend fun insertUsage(usage: ShortsUsageEntity): Long

    @Insert
    suspend fun insertEvent(event: ShortsEventEntity): Long

    /** Unsynced usage records in insertion order (oldest first). */
    @Query("SELECT * FROM shorts_usage ORDER BY usageId ASC")
    suspend fun usageSnapshot(): List<ShortsUsageEntity>

    /** Unsynced events in insertion order (oldest first). */
    @Query("SELECT * FROM shorts_events ORDER BY eventId ASC")
    suspend fun eventSnapshot(): List<ShortsEventEntity>

    /** Clears local records (e.g. after a confirmed sync). */
    @Query("DELETE FROM shorts_usage")
    suspend fun clearUsage()

    @Query("DELETE FROM shorts_events")
    suspend fun clearEvents()
}
