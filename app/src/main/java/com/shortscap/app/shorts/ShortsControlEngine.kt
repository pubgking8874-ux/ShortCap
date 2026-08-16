package com.shortscap.app.shorts

import com.shortscap.app.BuildConfig
import java.util.LinkedHashSet

/**
 * P1-5 — the AUTHORITATIVE local Shorts control state machine.
 *
 * The detector keeps detecting short-form content; this engine owns the
 * 24-hour limit lifecycle: when a window starts, how many valid Shorts were
 * counted in it, when the window expires, whether warning/limit states have
 * been reached — and it persists every transition through
 * [ShortsLimitCycleStore] so the state survives app restart, process death,
 * force-stop and (where recovery is wired) device reboot.
 *
 * Rules enforced here:
 *  - AT MOST ONE active cycle per user/device (§5). A new cycle is created
 *    only when none exists (user activates a limit) or the active one has
 *    expired (§8/§21). Opening the app, opening Shorts, rotation or HUD
 *    visibility NEVER creates or resets a cycle.
 *  - Limit activation persists IMMEDIATELY (§6) — never deferred until the
 *    first counted Short.
 *  - Changing the limit while a cycle is active ONLY changes the threshold:
 *    currentCount, cycleStartedAt and cycleExpiresAt are preserved (§7).
 *  - Expiry is timestamp-derived (cycleExpiresAt - now); no permanent
 *    second-by-second timer (§8). An expired window is marked EXPIRED and a
 *    fresh window is initialized (same limit, count 0, new timestamps).
 *  - One logical Short counts at most once (§11) — a bounded recent-candidate
 *    set deduplicates repeated/recomposition callbacks.
 *  - Counting is platform-agnostic: every platform's valid Shorts feed ONE
 *    global currentCount for the SAME active cycle (§12).
 *  - Warning is evaluated against the existing Shorts warning settings
 *    (`warning_count` count-based, `warning_minutes` time-based) and latches
 *    once per cycle (§14). No second warning configuration is invented.
 *  - Disabling control marks the window DISABLED without deleting history;
 *    re-enabling starts a fresh window (§23).
 *
 * The engine decides STATE. The Android enforcement layer decides how to
 * react (this task does not implement blocking behavior).
 */
class ShortsControlEngine(
    private val store: ShortsLimitCycleStore,
    /** Count-based warning threshold (existing `warning_count` semantics), null = off. */
    private val warningCount: Int? = null,
    /** Time-based warning threshold in MINUTES (existing `warning_minutes` semantics), null = off. */
    private val warningMinutes: Int? = null,
    /** Exact 24-hour window length. */
    private val cycleDurationMillis: Long = CYCLE_DURATION_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /**
     * 24-hour edit lock policy. The FINAL product rule: once a Shorts limit
     * is saved it becomes active immediately and is LOCKED until the current
     * 24-hour cycle expires — the user cannot change the limit during an
     * active cycle, and there is no enable/disable toggle or password.
     *
     * SAFE DEVELOPMENT-ONLY test seam: defaulted to [BuildConfig.DEBUG], so
     * debug builds may edit the limit during an active cycle (developers do
     * not wait 24 hours per test) while release/production builds enforce
     * the lock. Never exposed through production UI and never stored as a
     * preference.
     */
    private val allowEditWhileActive: Boolean = BuildConfig.DEBUG,
) {

    /** Recent logical candidates already counted — prevents duplicate counting. */
    private val recentCandidates = LinkedHashSet<String>()

    companion object {
        /** One 24-hour enforcement window. */
        const val CYCLE_DURATION_MILLIS: Long = 24L * 60L * 60L * 1000L

        /** Bounded recent-candidate set — prevents unbounded memory growth. */
        const val MAX_RECENT_CANDIDATES = 512

        @Volatile
        private var sharedEngine: ShortsControlEngine? = null

        /** Installs the app-wide engine (called from [com.shortscap.app.ShortsCapApplication]). */
        fun install(engine: ShortsControlEngine) {
            sharedEngine = engine
        }

        /** The app-wide engine (fallback: a throwaway in-memory engine). */
        val shared: ShortsControlEngine
            get() = sharedEngine ?: ShortsControlEngine(InMemoryShortsLimitCycleStore())
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Activates Shorts control with [limitCount] — the limit becomes ACTIVE
     * immediately (no separate toggle). While an active 24-hour cycle is
     * running the limit is LOCKED: in production ([allowEditWhileActive] =
     * false) an edit is rejected and the current state is returned unchanged.
     * The DEBUG test seam ([allowEditWhileActive] = true, the default in
     * debug builds) permits a threshold-only change for testing — count and
     * timers are preserved. A limit <= 0 is rejected (control stays off).
     */
    fun setLimit(limitCount: Int, now: Long = nowMillis()): ShortsControlState {
        if (limitCount <= 0) return currentState()
        val active = store.currentCycle()
        val fresh = active == null || active.cycleExpiresAt <= now
        if (fresh) {
            store.save(
                ShortsLimitCycle(
                    limitCount = limitCount,
                    cycleStartedAt = now,
                    cycleExpiresAt = now + cycleDurationMillis,
                    status = ShortsLimitCycleStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        } else {
            // 24-hour lock: reject the edit while a cycle is active unless
            // the development test seam is enabled.
            if (isLimitLocked(now)) return currentState()
            // Threshold-only change: same window, same count, same timers.
            store.save(active!!.copy(limitCount = limitCount, updatedAt = now))
        }
        return currentState()
    }

    /**
     * Whether the saved Shorts limit is currently locked. Locked while an
     * unexpired cycle is active AND the production lock is enforced; the
     * DEBUG test seam reports unlocked so developers can test limit changes
     * without waiting 24 hours. Expired cycles are never locked.
     */
    fun isLimitLocked(now: Long = nowMillis()): Boolean {
        val active = store.currentCycle() ?: return false
        if (active.cycleExpiresAt <= now) return false
        return !allowEditWhileActive
    }

    /**
     * Whether a 24-hour cycle is CURRENTLY ACTIVE and unexpired — the shared
     * Short Control lock state consumed by Short Applications (toggles are
     * read-only), Shorts Limit, Shorts HUD and enforcement. Unlike
     * [isLimitLocked] this is NOT gated by the DEBUG edit test seam: while a
     * cycle runs, monitored-app configuration must stay locked so the user
     * cannot bypass enforcement mid-cycle (installing or disabling apps is
     * never a bypass mechanism).
     */
    fun hasActiveCycle(now: Long = nowMillis()): Boolean {
        val active = store.currentCycle() ?: return false
        return active.cycleExpiresAt > now
    }

    /**
     * Whether any 24-hour window has EVER existed. The Shorts Limit page uses
     * this to distinguish "never configured" (first-time setup) from
     * "disabled after use" — both report no active cycle, but only the
     * latter has history.
     */
    fun hasHistory(): Boolean = store.history().isNotEmpty()

    /**
     * Disables Shorts control: the active window becomes DISABLED (history is
     * kept), nothing counts, and the engine reports DISABLED until re-enabled.
     */
    fun disable(): ShortsControlState {
        store.markDisabled()
        return currentState()
    }

    /** Records one VALID Short (the aggregator already applied the 3–5s rule). */
    fun onShortCounted(
        candidateKey: String,
        occurredAt: Long,
        durationMillis: Long,
        now: Long = nowMillis(),
    ): ShortsControlState {
        val cycle = ensureFreshCycle(now) ?: return currentState()
        // One logical Short = one count: ignore repeated/recomposition
        // callbacks for the same candidate within the session.
        if (!recentCandidates.add(candidateKey)) return currentState()
        if (recentCandidates.size > MAX_RECENT_CANDIDATES) {
            val iter = recentCandidates.iterator()
            repeat(MAX_RECENT_CANDIDATES / 2) {
                if (iter.hasNext()) {
                    iter.next()
                    iter.remove()
                }
            }
        }

        val count = cycle.currentCount + 1
        val duration = cycle.cycleDurationMillis + durationMillis
        val limitReached = count >= cycle.limitCount
        val warning = evaluateWarning(count, duration)
        store.save(
            cycle.copy(
                currentCount = count,
                cycleDurationMillis = duration,
                limitReached = limitReached,
                warningTriggered = cycle.warningTriggered || warning,
                status = if (limitReached) ShortsLimitCycleStatus.LIMIT_REACHED else ShortsLimitCycleStatus.ACTIVE,
                updatedAt = now,
            )
        )
        return currentState()
    }

    /**
     * The derived current state. Loads the persisted cycle, applies expiry
     * (marking the old window EXPIRED and initializing the next one), and
     * returns the read-only state consumers (Short Control page, HUD,
     * enforcement layer) render. Never resets on restart.
     */
    fun currentState(now: Long = nowMillis()): ShortsControlState {
        val cycle = ensureFreshCycle(now)
        return cycle?.let { deriveState(it, now) }
            ?: ShortsControlState(
                cycle = null,
                status = ShortsLimitCycleStatus.DISABLED,
                currentCount = 0,
                limitCount = 0,
                usageRatio = 0f,
                remainingCount = 0,
                cycleStartedAt = null,
                cycleExpiresAt = null,
                remainingCycleMillis = 0L,
                enforcementState = ShortsEnforcementState.ALLOW,
                warningTriggered = false,
                limitReached = false,
            )
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Returns the active cycle valid at [now]: the persisted active window
     * if unexpired, otherwise a fresh window initialized after marking the
     * expired one EXPIRED. Null when control is disabled (no active window).
     */
    private fun ensureFreshCycle(now: Long): ShortsLimitCycle? {
        val active = store.currentCycle() ?: return null
        if (active.cycleExpiresAt > now) return active
        // Expired: persist EXPIRED, then initialize the next window.
        store.save(active.copy(status = ShortsLimitCycleStatus.EXPIRED, updatedAt = now))
        val fresh = ShortsLimitCycle(
            limitCount = active.limitCount,
            cycleStartedAt = now,
            cycleExpiresAt = now + cycleDurationMillis,
            status = ShortsLimitCycleStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        return store.save(fresh)
    }

    private fun deriveState(cycle: ShortsLimitCycle, now: Long): ShortsControlState {
        val limit = cycle.limitCount
        val ratio = if (limit > 0) cycle.currentCount.toFloat() / limit else 0f
        val enforcement = when {
            cycle.status == ShortsLimitCycleStatus.DISABLED -> ShortsEnforcementState.ALLOW
            cycle.limitReached || cycle.currentCount >= limit -> ShortsEnforcementState.LIMIT_REACHED
            cycle.warningTriggered -> ShortsEnforcementState.WARNING
            else -> ShortsEnforcementState.ALLOW
        }
        return ShortsControlState(
            cycle = cycle,
            status = cycle.status,
            currentCount = cycle.currentCount,
            limitCount = limit,
            usageRatio = ratio,
            remainingCount = (limit - cycle.currentCount).coerceAtLeast(0),
            cycleStartedAt = cycle.cycleStartedAt,
            cycleExpiresAt = cycle.cycleExpiresAt,
            remainingCycleMillis = (cycle.cycleExpiresAt - now).coerceAtLeast(0L),
            enforcementState = enforcement,
            warningTriggered = cycle.warningTriggered,
            limitReached = cycle.limitReached || cycle.currentCount >= limit,
        )
    }

    /** Count- and/or time-based warning using the existing settings semantics. */
    private fun evaluateWarning(count: Int, durationMillis: Long): Boolean {
        val countWarn = warningCount?.takeIf { it > 0 }?.let { count >= it } ?: false
        val timeWarn = warningMinutes?.takeIf { it > 0 }
            ?.let { durationMillis >= it * 60_000L } ?: false
        return countWarn || timeWarn
    }

}
