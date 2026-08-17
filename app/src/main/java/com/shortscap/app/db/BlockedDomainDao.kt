package com.shortscap.app.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Domain Blocking Foundation — durable storage for the blocked-domain list.
 *
 * This is the persistence layer the future Local VPN/DNS filtering engine
 * consumes: one row per blocked domain. The canonical (normalized) domain is
 * the primary key, so duplicates are structurally impossible — [insert] uses
 * IGNORE so re-blocking an already-blocked domain is a no-op (returns 0)
 * instead of an error or a duplicate row.
 *
 * [enabled] lets the future engine keep a domain in the list while
 * temporarily not enforcing it (e.g. a pause toggle); it defaults to true
 * when a domain is blocked. Columns are minimal — nothing beyond what the
 * VPN/DNS engine needs.
 */
@Entity(tableName = "blocked_domains")
data class BlockedDomainEntity(
    /** Canonical normalized domain (bare lowercase hostname). */
    @PrimaryKey val domain: String,
    /** Epoch-millis timestamp of when the domain was blocked. */
    val createdAt: Long,
    /** Whether the block is currently enforced. */
    val enabled: Boolean,
)

@Dao
interface BlockedDomainDao {

    /** Inserts one blocked domain; returns 0 when it already exists (duplicate-safe). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(domain: BlockedDomainEntity): Long

    /** Removes the block for [domain] (no-op when it is not blocked). */
    @Query("DELETE FROM blocked_domains WHERE domain = :domain")
    suspend fun remove(domain: String)

    /** Whether [domain] is currently in the blocked list. */
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_domains WHERE domain = :domain)")
    suspend fun isBlocked(domain: String): Boolean

    /** Every blocked domain, oldest first. */
    @Query("SELECT * FROM blocked_domains ORDER BY createdAt ASC")
    suspend fun all(): List<BlockedDomainEntity>
}
