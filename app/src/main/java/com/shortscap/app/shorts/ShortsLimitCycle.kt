package com.shortscap.app.shorts

/**
 * P1-5 — the CORE Shorts control domain model.
 *
 * A [ShortsLimitCycle] is one active 24-hour enforcement window:
 *
 *   cycleStartedAt  = UTC timestamp when the window begins
 *   cycleExpiresAt  = cycleStartedAt + 24 hours (exact, timestamp-derived)
 *   currentCount    = valid Shorts counted during THIS window only
 *
 * Configuration (`shorts_settings`) stays separate from runtime state: the
 * cycle holds the runtime window; `shorts_usage` / `shorts_events` hold the
 * historical records. One user/device has AT MOST ONE active cycle — a new
 * cycle is created only when (a) none exists and the user activates a limit,
 * or (b) the active cycle has expired. Restarting the app, killing the
 * process or reopening Shorts NEVER creates or resets a cycle.
 */
data class ShortsLimitCycle(
    /** Local persistent id (0 until first save). */
    val localId: Long = 0L,
    /** Configured maximum number of Shorts for this window. */
    val limitCount: Int,
    /** Valid Shorts counted so far in this window. */
    val currentCount: Int = 0,
    /** Counted Shorts duration accumulated in this window (ms) — used by time-based warnings. */
    val cycleDurationMillis: Long = 0L,
    /** UTC epoch millis when the window began. */
    val cycleStartedAt: Long,
    /** Exact UTC epoch millis when the window ends (start + 24h). */
    val cycleExpiresAt: Long,
    val status: ShortsLimitCycleStatus,
    /** Latches true once a configured warning threshold is reached in this window. */
    val warningTriggered: Boolean = false,
    val limitReached: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** Whether the window is still the enforceable one (ACTIVE or LIMIT_REACHED). */
    val isActive: Boolean get() = status == ShortsLimitCycleStatus.ACTIVE || status == ShortsLimitCycleStatus.LIMIT_REACHED
}

/** The allowed lifecycle states of one [ShortsLimitCycle]. */
enum class ShortsLimitCycleStatus {
    /** Window running, limit not yet reached. */
    ACTIVE,

    /** Window running and currentCount >= limitCount — persists until expiry. */
    LIMIT_REACHED,

    /** Window passed cycleExpiresAt — replaced by the next cycle. */
    EXPIRED,

    /** Shorts control is turned off — history is kept, nothing is counted. */
    DISABLED,
}

/** The local ENFORCEMENT state the Android enforcement layer consumes. */
enum class ShortsEnforcementState {
    /** Under the limit and no warning threshold reached. */
    ALLOW,

    /** A configured warning threshold (count- or time-based) was reached. */
    WARNING,

    /** currentCount >= limitCount. */
    LIMIT_REACHED,
}

/**
 * The derived, read-only state exposed to UI (Shorts Control page, Shorts
 * HUD) and the future enforcement layer. The engine is the only producer —
 * consumers never maintain their own counts.
 */
data class ShortsControlState(
    /** The active cycle, or null when Shorts control is disabled/no cycle exists. */
    val cycle: ShortsLimitCycle?,
    val status: ShortsLimitCycleStatus,
    val currentCount: Int,
    val limitCount: Int,
    /** currentCount / limitCount, safe for limitCount <= 0 (returns 0f). */
    val usageRatio: Float,
    /** limitCount - currentCount, never negative. */
    val remainingCount: Int,
    val cycleStartedAt: Long?,
    val cycleExpiresAt: Long?,
    /** Remaining time in the window (ms), 0 when expired/disabled. */
    val remainingCycleMillis: Long,
    val enforcementState: ShortsEnforcementState,
    val warningTriggered: Boolean,
    val limitReached: Boolean,
)

/**
 * The persistence boundary for the authoritative cycle state.
 *
 * P1-5 uses the same durable local architecture as P1-2 (Room), so the
 * active cycle — count, limit, start, expiry, state — survives app restart,
 * process death, force-stop and (where recovery is wired) device reboot.
 * Disabling control never deletes completed cycles (history is preserved).
 */
interface ShortsLimitCycleStore {

    /** The single active (ACTIVE/LIMIT_REACHED) cycle, newest first, or null. */
    fun currentCycle(): ShortsLimitCycle?

    /** Insert (localId == 0) or update the cycle. */
    fun save(cycle: ShortsLimitCycle): ShortsLimitCycle

    /** Completed/disabled cycles, newest first (never deleted on disable). */
    fun history(): List<ShortsLimitCycle>

    /** Drops the current active cycle (used when disabling control). */
    fun markDisabled(): ShortsLimitCycle?
}

/** In-memory store — tests only. The app uses the Room-backed store. */
class InMemoryShortsLimitCycleStore : ShortsLimitCycleStore {

    private var nextId = 1L
    private val rows = mutableListOf<ShortsLimitCycle>()

    override fun currentCycle(): ShortsLimitCycle? =
        rows.filter { it.isActive }.maxByOrNull { it.cycleStartedAt }

    override fun save(cycle: ShortsLimitCycle): ShortsLimitCycle {
        val saved = if (cycle.localId == 0L) cycle.copy(localId = nextId++) else cycle
        val idx = rows.indexOfFirst { it.localId == saved.localId }
        if (idx >= 0) rows[idx] = saved else rows += saved
        return saved
    }

    override fun history(): List<ShortsLimitCycle> = rows.sortedByDescending { it.cycleStartedAt }

    override fun markDisabled(): ShortsLimitCycle? {
        val active = currentCycle() ?: return null
        val disabled = active.copy(status = ShortsLimitCycleStatus.DISABLED)
        save(disabled)
        return disabled
    }
}
