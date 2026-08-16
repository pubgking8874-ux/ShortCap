package com.shortscap.app.screenactivity

import com.shortscap.app.db.ScreenActivityDao
import com.shortscap.app.db.ScreenActivityUsageEntity
import com.shortscap.app.sync.utcDateKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * DURABLE ScreenActivityStore — Room-backed, survives process death.
 *
 * Same [ScreenActivityStore] contract as the in-memory store (so the engine
 * is untouched) but backed by [ScreenActivityDao]: generic app-usage sessions
 * captured while the backend is unavailable are persisted to SQLite
 * immediately, so a drain after an app restart finds every session that was
 * waiting for sync.
 *
 * One row per closed foreground session (launchCount = 1); the repository
 * aggregates per (package, date) before enqueueing — matching the idempotent
 * per-day upsert the backend `POST /monitoring/app-usage/sync` contract uses
 * (last sync wins, never doubles).
 */
class RoomScreenActivityStore(
    private val dao: ScreenActivityDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ScreenActivityStore {

    override fun recordSession(session: ScreenActivitySession) {
        ioBlocking { dao.insertUsage(session.toEntity()) }
    }

    override fun sessionSnapshot(): List<ScreenActivitySession> =
        ioBlocking { dao.usageSnapshot() }.map { it.toSession() }

    override fun clear() {
        ioBlocking { dao.clear() }
    }

    private fun <T> ioBlocking(block: suspend () -> T): T =
        runBlocking(ioDispatcher) { block() }
}

// ---------------------------------------------------------------------------
// Mapping between the domain model and the Room entity
// ---------------------------------------------------------------------------

private fun ScreenActivitySession.toEntity() = ScreenActivityUsageEntity(
    packageName = packageName,
    appName = appName,
    // The UTC calendar date the session STARTED — the same bucket key the
    // repository uses when aggregating, so a session always lands in the day
    // it began (matching the backend's per-day summary semantics).
    usageDate = utcDateKey(startedAtMillis),
    durationSeconds = (durationMillis / 1000).coerceAtLeast(0),
    launchCount = 1,
    occurredAt = startedAtMillis,
)

private fun ScreenActivityUsageEntity.toSession() = ScreenActivitySession(
    packageName = packageName,
    appName = appName,
    startedAtMillis = occurredAt,
    endedAtMillis = occurredAt + durationSeconds * 1000,
)
