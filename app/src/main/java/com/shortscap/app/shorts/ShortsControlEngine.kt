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
     * SAVES/CONFIGURES the Shorts limit WITHOUT starting a 24-hour cycle
     * (READY state). The user must then press ACTIVE ([activate]) for the
     * cycle to start — the timer never starts on save, the limit is never
     * locked and no cycle row is created here.
     *
     * While an active 24-hour cycle is ALREADY running the limit is LOCKED:
     * in production ([allowEditWhileActive] = false) an edit is rejected and
     * the current state is returned unchanged. The DEBUG test seam
     * ([allowEditWhileActive] = true, the default in debug builds) permits a
     * threshold-only change on the running cycle for testing — count and
     * timers are preserved. A limit <= 0 is rejected (nothing saved).
     */
    fun setLimit(limitCount: Int, now: Long = nowMillis()): ShortsControlState {
        if (limitCount <= 0) return currentState()
        val active = store.currentCycle()
        if (active != null && active.cycleExpiresAt > now) {
            // A cycle is running: production lock rejects; debug seam allows a
            // threshold-only edit on the SAME window.
            if (isLimitLocked(now)) return currentState()
            store.save(active.copy(limitCount = limitCount, updatedAt = now))
            return currentState()
        }
        // No running cycle: persist the CONFIGURED limit (READY state). The
        // configured row survives restart; activating later uses this limit.
        val configured = store.configuredCycle()
        if (configured != null) {
            store.save(configured.copy(limitCount = limitCount, updatedAt = now))
        } else {
            store.save(
                ShortsLimitCycle(
                    limitCount = limitCount,
                    cycleStartedAt = 0L,
                    cycleExpiresAt = 0L,
                    status = ShortsLimitCycleStatus.CONFIGURED,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
        return currentState()
    }

    /**
     * Explicitly STARTS the 24-hour cycle (READY → ACTIVE). No-op when there
     * is nothing to activate (no configured limit and no prior window) or a
     * cycle is already running (an active cycle is never restarted, reset or
     * duplicated).
     *
     * The limit comes from the CONFIGURED row when one exists (that row is
     * transitioned in place into the ACTIVE cycle — one row per window);
     * after an expiry with no fresh configuration, the most recent EXPIRED
     * window's limit is reused so the user can simply press ACTIVE again
     * without re-entering the limit. On success: cycleStartedAt = now,
     * cycleExpiresAt = now + 24h, currentCount = 0, status = ACTIVE.
     */
    fun activate(now: Long = nowMillis()): ShortsControlState {
        val active = store.currentCycle()
        if (active != null && active.cycleExpiresAt > now) return currentState()
        val configured = store.configuredCycle()
        if (configured != null) {
            // Start the window with the configured limit; the CONFIGURED row
            // is transitioned in place into the ACTIVE cycle.
            store.save(
                configured.copy(
                    currentCount = 0,
                    cycleDurationMillis = 0L,
                    cycleStartedAt = now,
                    cycleExpiresAt = now + cycleDurationMillis,
                    status = ShortsLimitCycleStatus.ACTIVE,
                    warningTriggered = false,
                    limitReached = false,
                    updatedAt = now,
                )
            )
            return currentState()
        }
        // After expiry: reuse the last window's limit for the next cycle.
        val lastExpired = store.history()
            .firstOrNull { it.status == ShortsLimitCycleStatus.EXPIRED }
            ?: return currentState()
        store.save(
            ShortsLimitCycle(
                limitCount = lastExpired.limitCount,
                cycleStartedAt = now,
                cycleExpiresAt = now + cycleDurationMillis,
                status = ShortsLimitCycleStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            )
        )
        return currentState()
    }

    /**
     * Whether the saved Shorts limit is currently locked. Locked ONLY while
     * an unexpired cycle is ACTIVE and the production lock is enforced; a
     * CONFIGURED (saved, not activated) limit is NEVER locked — editing
     * stays available before the user presses ACTIVE. The DEBUG test seam
     * reports unlocked for active cycles so developers can test limit
     * changes without waiting 24 hours. Expired cycles are never locked.
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
     * (marking the old window EXPIRED — the NEXT cycle only starts when the
     * user explicitly activates it), and returns the read-only state
     * consumers (Short Control page, HUD, enforcement layer) render. Never
     * resets on restart. A saved-but-not-activated limit reports status
     * CONFIGURED (READY_TO_ACTIVATE — no cycle, no countdown, no lock).
     */
    fun currentState(now: Long = nowMillis()): ShortsControlState {
        val cycle = store.currentCycle()
        if (cycle != null) {
            if (cycle.cycleExpiresAt > now) return deriveState(cycle, now)
            // Expired: persist EXPIRED (no auto-roll — the user re-activates)
            // and surface the EXPIRED state so the page shows the expired
            // notice + ACTIVE button instead of silently resetting.
            val expired = cycle.copy(status = ShortsLimitCycleStatus.EXPIRED, updatedAt = now)
            store.save(expired)
            return deriveState(expired, now)
        }
        val configured = store.configuredCycle()
        if (configured != null) {
            return ShortsControlState(
                cycle = configured,
                status = ShortsLimitCycleStatus.CONFIGURED,
                currentCount = 0,
                limitCount = configured.limitCount,
                usageRatio = 0f,
                remainingCount = configured.limitCount,
                cycleStartedAt = null,
                cycleExpiresAt = null,
                remainingCycleMillis = 0L,
                enforcementState = ShortsEnforcementState.ALLOW,
                warningTriggered = false,
                limitReached = false,
            )
        }
        // No active window and no fresh configuration: keep surfacing the most
        // recent EXPIRED window (editing + re-activation available) rather
        // than falling back to first-time setup.
        store.history().firstOrNull { it.status == ShortsLimitCycleStatus.EXPIRED }?.let {
            return deriveState(it, now)
        }
        return ShortsControlState(
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
     * Returns the active cycle valid at [now] for counting, or null when no
     * cycle is running. A saved-but-not-activated limit (CONFIGURED) is NOT
     * counted; an expired window is marked EXPIRED and no next cycle is
     * auto-created — counting resumes only after an explicit [activate].
     */
    private fun ensureFreshCycle(now: Long): ShortsLimitCycle? {
        val active = store.currentCycle() ?: return null
        if (active.cycleExpiresAt > now) return active
        store.save(active.copy(status = ShortsLimitCycleStatus.EXPIRED, updatedAt = now))
        return null
    }

    private fun deriveState(cycle: ShortsLimitCycle, now: Long): ShortsControlState {
        val limit = cycle.limitCount
        val ratio = if (limit > 0) cycle.currentCount.toFloat() / limit else 0f
        // A finished window (EXPIRED / DISABLED) is never in an enforcement
        // state — enforcement belongs to the running window only.
        val windowOver = cycle.status == ShortsLimitCycleStatus.EXPIRED ||
            cycle.status == ShortsLimitCycleStatus.DISABLED
        val enforcement = when {
            windowOver -> ShortsEnforcementState.ALLOW
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
            limitReached = if (windowOver) false else cycle.limitReached || cycle.currentCount >= limit,
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
