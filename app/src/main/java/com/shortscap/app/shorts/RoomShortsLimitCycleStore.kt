package com.shortscap.app.shorts

import com.shortscap.app.db.ShortsLimitCycleDao
import com.shortscap.app.db.ShortsLimitCycleEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * P1-5 DURABLE [ShortsLimitCycleStore] — Room-backed, survives process death.
 *
 * The authoritative 24-hour cycle (count, limit, start, expiry, state) is
 * persisted to SQLite on every mutation, so closing/reopening the app, a
 * process kill, or a force-stop never resets the count or the window. The
 * engine — never the UI — writes through this store (P1-5 §4/§5/§24).
 */
class RoomShortsLimitCycleStore(
    private val dao: ShortsLimitCycleDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShortsLimitCycleStore {

    override fun currentCycle(): ShortsLimitCycle? =
        ioBlocking { dao.currentActive() }?.toCycle()

    override fun save(cycle: ShortsLimitCycle): ShortsLimitCycle =
        ioBlocking {
            if (cycle.localId == 0L) {
                val newId = dao.insert(cycle.toEntity())
                cycle.copy(localId = newId)
            } else {
                dao.update(cycle.toEntity())
                cycle
            }
        }

    override fun history(): List<ShortsLimitCycle> =
        ioBlocking { dao.history() }.map { it.toCycle() }

    override fun markDisabled(): ShortsLimitCycle? {
        val active = currentCycle() ?: return null
        val disabled = active.copy(status = ShortsLimitCycleStatus.DISABLED)
        save(disabled)
        return disabled
    }

    private fun <T> ioBlocking(block: suspend () -> T): T =
        runBlocking(ioDispatcher) { block() }
}

// ---------------------------------------------------------------------------
// Mapping between the domain model and the Room entity
// ---------------------------------------------------------------------------

private fun ShortsLimitCycle.toEntity() = ShortsLimitCycleEntity(
    localId = localId,
    limitCount = limitCount,
    currentCount = currentCount,
    cycleDurationMillis = cycleDurationMillis,
    cycleStartedAt = cycleStartedAt,
    cycleExpiresAt = cycleExpiresAt,
    status = status.name,
    warningTriggered = warningTriggered,
    limitReached = limitReached,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ShortsLimitCycleEntity.toCycle() = ShortsLimitCycle(
    localId = localId,
    limitCount = limitCount,
    currentCount = currentCount,
    cycleDurationMillis = cycleDurationMillis,
    cycleStartedAt = cycleStartedAt,
    cycleExpiresAt = cycleExpiresAt,
    status = try {
        ShortsLimitCycleStatus.valueOf(status)
    } catch (_: IllegalArgumentException) {
        ShortsLimitCycleStatus.DISABLED
    },
    warningTriggered = warningTriggered,
    limitReached = limitReached,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
