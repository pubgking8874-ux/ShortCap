package com.shortscap.app.shorts

import android.util.Log
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
            // Preserve daily count and auto-restart setting.
            store.save(
                configured.copy(
                    currentCount = 0,
                    cycleDurationMillis = 0L,
                    cycleStartedAt = now,
                    cycleExpiresAt = now + cycleDurationMillis,
                    status = ShortsLimitCycleStatus.ACTIVE,
                    warningTriggered = false,
                    limitReached = false,
                    // Daily count is preserved — NOT reset on activation.
                    dailyShortsCount = configured.dailyShortsCount,
                    dailyShortsDate = configured.dailyShortsDate,
                    autoRestartEnabled = configured.autoRestartEnabled,
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

    /**
     * Enables or disables auto-restart for the current or next cycle.
     * When auto-restart is ON, a new 24h cycle is automatically created
     * when the current one expires (same saved limit, count resets to 0).
     */
    fun setAutoRestart(enabled: Boolean, now: Long = nowMillis()): ShortsControlState {
        // Update the configured row if it exists
        val configured = store.configuredCycle()
        if (configured != null) {
            store.save(configured.copy(autoRestartEnabled = enabled, updatedAt = now))
        }
        // Also update the active cycle if one exists
        val active = store.currentCycle()
        if (active != null && active.cycleExpiresAt > now) {
            store.save(active.copy(autoRestartEnabled = enabled, updatedAt = now))
        }
        return currentState()
    }

    /**
     * Records one VALID Short (the aggregator already applied the 3–5s rule).
     *
     * This method ALWAYS increments the Daily Monitoring Count, regardless of
     * whether an active limit cycle exists. The Active Limit Count is only
     * incremented when there IS an active cycle.
     *
     * Daily monitoring is informational and must never depend on limit activation.
     */
    fun onShortCounted(
        candidateKey: String,
        occurredAt: Long,
        durationMillis: Long,
        now: Long = nowMillis(),
    ): ShortsControlState {
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

        // --- STEP 1: Always increment Daily Monitoring Count ---
        // This is independent of any limit cycle.
        val todayDate = java.time.LocalDate.now().toString() // "yyyy-MM-dd"
        val newDailyCount = store.incrementDailyCount(todayDate)
        Log.i("SC_COUNT",
            "SC_COUNT DAILY_SHORT_INCREMENTED dailyCount=$newDailyCount date=$todayDate",
        )

        // --- STEP 2: Increment Active Limit Count (only if cycle exists) ---
        val savedLimit = resolveSavedLimit()
        val currentCycle = store.currentCycle()
        val hasActive = currentCycle?.let { it.cycleExpiresAt > now } == true

        // --- CYCLE_STATE diagnostic ---
        Log.i("SC_COUNT",
            "SC_COUNT CYCLE_STATE active=$hasActive " +
                "currentCount=${currentCycle?.currentCount ?: "none"} " +
                "limitCount=${currentCycle?.limitCount ?: "none"} " +
                "cycleStartedAt=${currentCycle?.cycleStartedAt ?: "none"} " +
                "cycleExpiresAt=${currentCycle?.cycleExpiresAt ?: "none"} " +
                "now=$now remainingMillis=${if (currentCycle != null && currentCycle.cycleExpiresAt > now) (currentCycle.cycleExpiresAt - now) else "none"} " +
                "status=${currentCycle?.status ?: "none"} savedLimit=${savedLimit ?: "none"}",
        )
        Log.i("SC_COUNT",
            "SC_COUNT LIMIT_CONFIG_CHECK savedLimit=${savedLimit ?: "none"} " +
                "activeCycle=$hasActive",
        )

        if (!hasActive) {
            // No active limit cycle — daily count is the only count.
            // This is NOT an error. The user may not have activated a limit.
            Log.i("SC_COUNT",
                "SC_COUNT NO_ACTIVE_CYCLE dailyCount=$newDailyCount limitNotActive",
            )
            return currentState()
        }

        // Active cycle exists — increment the limit count
        val cycle = ensureFreshCycle(now) ?: return currentState()
        val count = cycle.currentCount + 1
        val duration = cycle.cycleDurationMillis + durationMillis
        val limitReached = count >= cycle.limitCount
        val warning = evaluateWarning(count, duration)
        Log.i("SC_RT", "SC_RT ROOM_WRITE count=$count limit=${cycle.limitCount}")
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
        Log.i("SC_RT", "SC_RT ROOM_WRITE_COMPLETE count=$count")
        Log.i("SC_COUNT",
            "SC_COUNT SHORT_INCREMENTED count=$count limit=${cycle.limitCount} dailyCount=$newDailyCount",
        )
        if (limitReached) {
            Log.i("SC_COUNT",
                "SC_COUNT LIMIT_REACHED count=$count limit=${cycle.limitCount}",
            )
        }

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
        val dailyCount = store.getDailyCount()
        val cycle = store.currentCycle()
        if (cycle != null) {
            if (cycle.cycleExpiresAt > now) return deriveState(cycle, now, dailyCount)
            // Cycle has expired.
            if (cycle.autoRestartEnabled) {
                // Auto-restart: create a new 24h cycle from the saved limit.
                val savedLimit = cycle.limitCount
                Log.i("SC_COUNT",
                    "SC_COUNT AUTO_RESTART limit=$savedLimit previousCount=${cycle.currentCount} dailyCount=$dailyCount",
                )
                val newCycle = store.save(
                    ShortsLimitCycle(
                        limitCount = savedLimit,
                        currentCount = 0,
                        cycleDurationMillis = 0L,
                        cycleStartedAt = now,
                        cycleExpiresAt = now + cycleDurationMillis,
                        status = ShortsLimitCycleStatus.ACTIVE,
                        warningTriggered = false,
                        limitReached = false,
                        autoRestartEnabled = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                return deriveState(newCycle, now, dailyCount)
            }
            // No auto-restart: mark EXPIRED, stop enforcement.
            val expired = cycle.copy(status = ShortsLimitCycleStatus.EXPIRED, updatedAt = now)
            store.save(expired)
            return deriveState(expired, now, dailyCount)
        }
        val configured = store.configuredCycle()
        if (configured != null) {
            return ShortsControlState(
                cycle = configured,
                status = ShortsLimitCycleStatus.CONFIGURED,
                currentCount = 0,
                limitCount = configured.limitCount,
                dailyShortsCount = dailyCount,
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
            return deriveState(it, now, dailyCount)
        }
        return ShortsControlState(
            cycle = null,
            status = ShortsLimitCycleStatus.DISABLED,
            currentCount = 0,
            limitCount = 0,
            dailyShortsCount = dailyCount,
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
     * Returns the active cycle valid at [now] for counting.
     *
     * If no active cycle exists, this method auto-initializes a new 24-hour
     * cycle from the saved limit (either a CONFIGURED row or the most recent
     * EXPIRED row's limitCount). This ensures that Shorts continue to be
     * counted even when the user hasn't explicitly re-activated after expiry.
     *
     * Returns null ONLY when no saved limit exists at all (never configured).
     */
    private fun ensureFreshCycle(now: Long): ShortsLimitCycle? {
        // Check for an existing active cycle
        val active = store.currentCycle()
        if (active != null) {
            if (active.cycleExpiresAt > now) return active
            // Active cycle has expired — mark it
            store.save(active.copy(status = ShortsLimitCycleStatus.EXPIRED, updatedAt = now))
        }

        // No active cycle — attempt to auto-initialize from saved limit
        val savedLimit = resolveSavedLimit()
        if (savedLimit != null) {
            Log.i("SC_COUNT",
                "SC_COUNT CYCLE_AUTO_INITIALIZED limit=$savedLimit reason=${if (active != null) "EXPIRED" else "MISSING"}",
            )
            return store.save(
                ShortsLimitCycle(
                    limitCount = savedLimit,
                    currentCount = 0,
                    cycleDurationMillis = 0L,
                    cycleStartedAt = now,
                    cycleExpiresAt = now + cycleDurationMillis,
                    status = ShortsLimitCycleStatus.ACTIVE,
                    warningTriggered = false,
                    limitReached = false,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }

        // No saved limit exists — counting cannot proceed
        return null
    }

    /**
     * Resolves the user's persistent Shorts limit from the store.
     *
     * Priority:
     *  1. A CONFIGURED row (user explicitly set a limit, not yet activated)
     *  2. The most recent EXPIRED row's limitCount (limit persists after cycle expiry)
     *  3. null (never configured)
     */
    private fun resolveSavedLimit(): Int? {
        // Priority 1: explicit configured limit
        val configured = store.configuredCycle()
        if (configured != null && configured.limitCount > 0) {
            return configured.limitCount
        }
        // Priority 2: most recent expired cycle's limit
        val lastExpired = store.history()
            .firstOrNull { it.status == ShortsLimitCycleStatus.EXPIRED }
        if (lastExpired != null && lastExpired.limitCount > 0) {
            return lastExpired.limitCount
        }
        return null
    }

    private fun deriveState(cycle: ShortsLimitCycle, now: Long, dailyCount: Int = 0): ShortsControlState {
        val limit = cycle.limitCount
        // When the window is over (EXPIRED / DISABLED), there is no active
        // enforcement cycle, so the limit count is 0. Only an ACTIVE or
        // LIMIT_REACHED cycle has a meaningful currentCount.
        val windowOver = cycle.status == ShortsLimitCycleStatus.EXPIRED ||
            cycle.status == ShortsLimitCycleStatus.DISABLED
        val effectiveCount = if (windowOver) 0 else cycle.currentCount
        val ratio = if (limit > 0) effectiveCount.toFloat() / limit else 0f
        val enforcement = when {
            windowOver -> ShortsEnforcementState.ALLOW
            cycle.limitReached || cycle.currentCount >= limit -> ShortsEnforcementState.LIMIT_REACHED
            cycle.warningTriggered -> ShortsEnforcementState.WARNING
            else -> ShortsEnforcementState.ALLOW
        }
        return ShortsControlState(
            cycle = cycle,
            status = cycle.status,
            currentCount = effectiveCount,
            limitCount = limit,
            dailyShortsCount = dailyCount,
            usageRatio = ratio,
            remainingCount = (limit - effectiveCount).coerceAtLeast(0),
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

    // ==================================================================
    // DEBUG / TEST CONTROLS — Development-only limit cycle manipulation
    // ==================================================================
    // These methods are only safe because allowEditWhileActive is gated by
    // BuildConfig.DEBUG. In release builds allowEditWhileActive=false so
    // production cycles are always locked. The UI section that calls these
    // methods is also gated by BuildConfig.DEBUG.
    // ==================================================================

    /** Stored original expiry for pause/resume. 0L = not paused. */
    @Volatile private var pausedOriginalExpiry: Long = 0L

    /**
     * DEBUG: Reset the current cycle to a clean testing state.
     * Sets count=0, limitReached=false, warningTriggered=false, and extends
     * the cycle by 24 hours from now so the developer can test freely.
     */
    fun debugResetCycle(): ShortsControlState {
        val now = nowMillis()
        val cycle = store.currentCycle()
        val limit = cycle?.limitCount?.takeIf { it > 0 }
            ?: store.configuredCycle()?.limitCount
            ?: 50
        store.save(
            ShortsLimitCycle(
                limitCount = limit,
                currentCount = 0,
                cycleDurationMillis = 0L,
                cycleStartedAt = now,
                cycleExpiresAt = now + cycleDurationMillis,
                status = ShortsLimitCycleStatus.ACTIVE,
                warningTriggered = false,
                limitReached = false,
                createdAt = now,
                updatedAt = now,
            )
        )
        pausedOriginalExpiry = 0L
        Log.i("SC_DEBUG", "SC_DEBUG RESET_CYCLE limit=$limit now=$now")
        return currentState()
    }

    /**
     * DEBUG: Set the current count to an arbitrary value.
     * Useful for testing limit-reached behavior at specific counts.
     */
    fun debugSetCount(count: Int): ShortsControlState {
        val now = nowMillis()
        val cycle = ensureFreshCycle(now) ?: return currentState()
        val limitReached = count >= cycle.limitCount
        store.save(
            cycle.copy(
                currentCount = count,
                limitReached = limitReached,
                status = if (limitReached) ShortsLimitCycleStatus.LIMIT_REACHED
                    else ShortsLimitCycleStatus.ACTIVE,
                updatedAt = now,
            )
        )
        Log.i("SC_DEBUG", "SC_DEBUG SET_COUNT count=$count limit=${cycle.limitCount} limitReached=$limitReached")
        return currentState()
    }

    /**
     * DEBUG: Set the limit to an arbitrary value.
     * Uses the existing setLimit() path.
     */
    fun debugSetLimit(limit: Int): ShortsControlState {
        if (limit <= 0) return currentState()
        val now = nowMillis()
        val cycle = store.currentCycle()
        if (cycle != null && cycle.cycleExpiresAt > now) {
            // Active cycle — update threshold only (preserves count/timers)
            store.save(cycle.copy(limitCount = limit, updatedAt = now))
        } else {
            // No active cycle — save configured limit
            val configured = store.configuredCycle()
            if (configured != null) {
                store.save(configured.copy(limitCount = limit, updatedAt = now))
            } else {
                store.save(
                    ShortsLimitCycle(
                        limitCount = limit,
                        cycleStartedAt = 0L,
                        cycleExpiresAt = 0L,
                        status = ShortsLimitCycleStatus.CONFIGURED,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
        Log.i("SC_DEBUG", "SC_DEBUG SET_LIMIT limit=$limit")
        return currentState()
    }

    /**
     * DEBUG: Clear only the limit-reached flag without resetting count.
     */
    fun debugClearLimitReached(): ShortsControlState {
        val now = nowMillis()
        val cycle = store.currentCycle() ?: return currentState()
        store.save(
            cycle.copy(
                limitReached = false,
                status = ShortsLimitCycleStatus.ACTIVE,
                updatedAt = now,
            )
        )
        Log.i("SC_DEBUG", "SC_DEBUG CLEAR_LIMIT_REACHED count=${cycle.currentCount}")
        return currentState()
    }

    /**
     * DEBUG: Pause the 24-hour cycle by extending its expiry far into the
     * future. The cycle remains ACTIVE but the countdown effectively stops.
     * Call [debugResumeCycle] to restore the original expiry.
     */
    fun debugPauseCycle(): ShortsControlState {
        val now = nowMillis()
        val cycle = store.currentCycle() ?: return currentState()
        if (pausedOriginalExpiry > 0L) return currentState() // already paused
        pausedOriginalExpiry = cycle.cycleExpiresAt
        store.save(
            cycle.copy(
                cycleExpiresAt = now + 365L * 24L * 60L * 60L * 1000L, // ~1 year
                updatedAt = now,
            )
        )
        Log.i("SC_DEBUG", "SC_DEBUG PAUSE originalExpiry=$pausedOriginalExpiry")
        return currentState()
    }

    /**
     * DEBUG: Resume a paused cycle by restoring its original expiry.
     * If the original expiry has already passed, the cycle is marked EXPIRED.
     */
    fun debugResumeCycle(): ShortsControlState {
        val now = nowMillis()
        val cycle = store.currentCycle() ?: return currentState()
        val original = pausedOriginalExpiry
        if (original <= 0L) return currentState() // not paused
        pausedOriginalExpiry = 0L
        if (original <= now) {
            // Original expiry already passed — mark expired
            store.save(cycle.copy(status = ShortsLimitCycleStatus.EXPIRED, updatedAt = now))
            Log.i("SC_DEBUG", "SC_DEBUG RESUME expired originalExpiry=$original")
        } else {
            store.save(cycle.copy(cycleExpiresAt = original, updatedAt = now))
            Log.i("SC_DEBUG", "SC_DEBUG RESUME restoredExpiry=$original")
        }
        return currentState()
    }

    /** DEBUG: Whether the cycle is currently paused. */
    fun isDebugPaused(): Boolean = pausedOriginalExpiry > 0L

}
