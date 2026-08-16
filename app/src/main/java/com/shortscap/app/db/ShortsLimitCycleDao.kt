package com.shortscap.app.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * P1-5 durable storage for the authoritative Shorts limit cycle.
 *
 * Mirrors [com.shortscap.app.shorts.ShortsLimitCycle]: one row per 24-hour
 * window. The single active (ACTIVE / LIMIT_REACHED) row is the authoritative
 * runtime state; completed/disabled windows are retained as history (never
 * deleted when control is disabled). A CONFIGURED row holds the
 * saved-but-not-activated limit (READY state — no cycle running yet). The
 * engine — not the UI — is the only writer.
 */
@Entity(tableName = "shorts_limit_cycle")
data class ShortsLimitCycleEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val limitCount: Int,
    val currentCount: Int,
    val cycleDurationMillis: Long,
    val cycleStartedAt: Long,
    val cycleExpiresAt: Long,
    val status: String,
    val warningTriggered: Boolean,
    val limitReached: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface ShortsLimitCycleDao {

    /** The single active window (ACTIVE or LIMIT_REACHED), newest first. */
    @Query(
        "SELECT * FROM shorts_limit_cycle WHERE status IN ('ACTIVE', 'LIMIT_REACHED') " +
            "ORDER BY cycleStartedAt DESC LIMIT 1"
    )
    suspend fun currentActive(): ShortsLimitCycleEntity?

    /** The saved-but-not-activated configured limit (CONFIGURED), newest first. */
    @Query(
        "SELECT * FROM shorts_limit_cycle WHERE status = 'CONFIGURED' " +
            "ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun configured(): ShortsLimitCycleEntity?

    /** Inserts a new window, returning its local id. */
    @Insert
    suspend fun insert(cycle: ShortsLimitCycleEntity): Long

    /** Updates an existing window in place. */
    @Update
    suspend fun update(cycle: ShortsLimitCycleEntity)

    /** All windows, newest first (history is never deleted on disable). */
    @Query("SELECT * FROM shorts_limit_cycle ORDER BY cycleStartedAt DESC")
    suspend fun history(): List<ShortsLimitCycleEntity>
}
