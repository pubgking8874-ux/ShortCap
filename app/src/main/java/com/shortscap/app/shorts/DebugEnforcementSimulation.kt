package com.shortscap.app.shorts

import android.util.Log
import com.shortscap.app.BuildConfig

/**
 * DEBUG-ONLY enforcement simulation — makes the EXISTING Shorts enforcement
 * decision instantly testable WITHOUT watching 10/15/50/100 real Shorts.
 *
 * WHAT IT IS
 * ==========
 * A strictly separated, fully in-memory simulation. It never replaces,
 * edits or even reads-with-write-access the production enforcement state:
 *
 *   - It owns its OWN [ShortsControlEngine] instance bound to an
 *     [InMemoryShortsLimitCycleStore]. Every count / limit / status /
 *     limit-reached / enforcement-state derivation therefore runs through
 *     the SAME engine class the production build uses — there is ONE source
 *     of truth for enforcement rules ([ShortsControlEngine]), and this
 *     simulation simply feeds simulated input into it. Nothing is ever
 *     persisted (no Room row, no `shorts_usage` / `shorts_events` write,
 *     no real cycle mutation).
 *   - The simulated "short-form surface is on screen" flag is fed into the
 *     SAME pure decision function the production enforcement layer uses:
 *     [shouldRestrict] (the exact boolean [ShortsRestrictionEngine] consumes
 *     to show/hide the real touch-blocking overlay). When that production
 *     decision turns true the simulation records an enforcement trigger —
 *     the same edge that, on a real device, would invoke the real
 *     restriction overlay / blocking reaction.
 *
 * PRODUCTION ISOLATION (release-safety)
 * =====================================
 * Every public entry point short-circuits when !BuildConfig.DEBUG (this is
 * the execution gate — not merely a hidden button), and the DEBUG control
 * surface that drives it is only composed in debug builds. In a RELEASE
 * build the simulation cannot start, cannot increment, cannot evaluate and
 * cannot trigger anything: the real production state machine, the real Room
 * cycle, the real limit and the real enforcement consumers are untouched.
 *
 * The real limit IS read (read-only) so the developer simulates against the
 * limit they actually configured — the simulation never becomes a second,
 * independent production limit.
 *
 * RESET semantics: reset() clears ONLY the simulation (simulated count → 0,
 * simulated surface → off). The real application state — real count, real
 * limit, persisted usage history, monitoring, settings — is never reset,
 * deleted or modified by any method here.
 */
class DebugEnforcementSimulation(
    /** The REAL authoritative engine whose configured limit the sim reads. */
    private val realEngineProvider: () -> ShortsControlEngine = { ShortsControlEngine.shared },
    /** Controllable clock (tests) — defaults to the real clock. */
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    companion object {
        /** Dedicated DEBUG-only log tag for the simulation path. */
        const val TAG = "SC_DEBUG_ENFORCEMENT"
    }

    /** The simulation's private in-memory cycle — the REAL engine class, but
     * zero persistence (no Room store is ever attached). Recreated on every
     * [start] so start/stop/start always begins from a clean simulated window. */
    private var simStore = InMemoryShortsLimitCycleStore()
    private var simEngine = ShortsControlEngine(store = simStore, nowMillis = nowMillis)

    private var active = false
    private var surfaceActive = false
    private var triggerCount = 0
    private var blockerActive = false

    // ------------------------------------------------------------------
    // Public simulation controls
    // ------------------------------------------------------------------

    /**
     * Starts the simulation. Uses [limitOverride] when given, otherwise the
     * REAL configured Shorts limit (read-only). A non-positive limit is
     * rejected exactly as the production engine rejects it — the simulation
     * stays off (this is also the zero-limit behavior: no fabricated cycle).
     */
    fun start(limitOverride: Int? = null): DebugEnforcementSnapshot {
        if (!enabled) return snapshot()
        if (active) return snapshot()
        val limit = limitOverride ?: realLimit()
        if (limit <= 0) {
            log("SIM_START_REJECTED limit=$limit reason=NOT_POSITIVE")
            return snapshot()
        }
        // Real engine semantics on a FRESH in-memory store: configure then
        // start a 24-hour ACTIVE cycle (count 0). Recreating the private
        // store keeps every start independent of any earlier simulated run.
        simStore = InMemoryShortsLimitCycleStore()
        simEngine = ShortsControlEngine(store = simStore, nowMillis = nowMillis)
        simEngine.setLimit(limit)
        simEngine.activate()
        active = true
        surfaceActive = false
        triggerCount = 0
        blockerActive = false
        log("SIM_START limit=$limit realLimit=${realLimit()} count=0")
        return evaluate()
    }

    /** Stops the simulation (returns to the inert state). Real state untouched. */
    fun stop(): DebugEnforcementSnapshot {
        if (!enabled) return snapshot()
        if (!active) return snapshot()
        active = false
        surfaceActive = false
        blockerActive = false
        log("SIM_END count=${simEngine.currentState(nowMillis()).currentCount}")
        return snapshot()
    }

    /** Simulates ONE more Short watched (count + 1, exactly one per call). */
    fun increment(): DebugEnforcementSnapshot {
        if (!enabled || !active) return snapshot()
        val cs = simEngine.currentState(nowMillis())
        simEngine.debugSetCount(cs.currentCount + 1)
        return evaluate("SIM_INCREMENT")
    }

    /** Simulates a batch of [n] Shorts watched in one step. */
    fun incrementBy(n: Int): DebugEnforcementSnapshot {
        if (!enabled || !active || n <= 0) return snapshot()
        val cs = simEngine.currentState(nowMillis())
        simEngine.debugSetCount(cs.currentCount + n)
        return evaluate("SIM_BATCH_INCREMENT added=$n")
    }

    /** Sets the simulated count to an arbitrary value (0..limit..beyond). */
    fun setCount(count: Int): DebugEnforcementSnapshot {
        if (!enabled || !active) return snapshot()
        simEngine.debugSetCount(count.coerceAtLeast(0))
        return evaluate("SIM_SET_COUNT")
    }

    /**
     * Changes the simulated limit (in-memory only). Follows the production
     * engine: a limit <= 0 is rejected and nothing changes; a positive limit
     * is applied to the running in-memory cycle (count preserved) and the
     * decision is re-derived immediately.
     */
    fun setLimit(limit: Int): DebugEnforcementSnapshot {
        if (!enabled || !active) return snapshot()
        if (limit <= 0) {
            log("SIM_LIMIT_REJECTED limit=$limit reason=NOT_POSITIVE")
            return evaluate()
        }
        simEngine.setLimit(limit)
        return evaluate("SIM_LIMIT")
    }

    /**
     * Toggles the simulated "short-form surface is on screen" precondition —
     * the same precondition the real [ShortsRestrictionEngine] requires
     * before it will show the blocking overlay.
     */
    fun setSurfaceActive(surfaceOn: Boolean): DebugEnforcementSnapshot {
        if (!enabled || !this.active) return snapshot()
        surfaceActive = surfaceOn
        return evaluate("SIM_SURFACE active=$surfaceOn")
    }

    /**
     * Resets ONLY the simulation: simulated count → 0, simulated surface →
     * off, simulated blocker lifted, trigger history cleared. The real
     * application state is never touched.
     */
    fun reset(): DebugEnforcementSnapshot {
        if (!enabled) return snapshot()
        if (!active) return snapshot()
        simEngine.debugSetCount(0)
        surfaceActive = false
        blockerActive = false
        triggerCount = 0
        log("SIM_RESET count=0")
        return evaluate()
    }

    /** Pure read of the current simulation snapshot (no side effects). */
    fun snapshot(): DebugEnforcementSnapshot {
        val cs = if (active) simEngine.currentState(nowMillis()) else null
        return build(cs)
    }

    /** Read-only view of the REAL configured limit (0 when none). */
    fun realLimit(): Int = realEngineProvider().currentState(nowMillis()).limitCount

    // ------------------------------------------------------------------
    // Decision evaluation — the REAL production decision, fed simulated input
    // ------------------------------------------------------------------

    /**
     * Re-reads the simulated engine state (real derivation) and runs the REAL
     * production decision [shouldRestrict] against the simulated surface.
     * The enforcement trigger is EDGE-TRIGGERED exactly like production:
     * firing once when the decision turns true while the simulated blocker is
     * down, lifting when the surface leaves / limit clears, and never
     * duplicating while the block condition persists.
     */
    private fun evaluate(opEvent: String? = null): DebugEnforcementSnapshot {
        val cs = simEngine.currentState(nowMillis())
        if (opEvent != null) {
            log("$opEvent count=${cs.currentCount} limit=${cs.limitCount}")
        }
        val restrict = active && shouldRestrict(surfaceActive, cs)
        logDecision(cs, restrict)
        when {
            restrict && !blockerActive -> {
                // The exact transition under which production invokes the
                // real enforcement reaction (restriction overlay) after a
                // surface re-evaluation.
                blockerActive = true
                triggerCount++
                log("SIM_ENFORCEMENT_TRIGGERED count=${cs.currentCount} limit=${cs.limitCount}")
                log("SIM_ENFORCEMENT_COMPLETED count=${cs.currentCount} limit=${cs.limitCount}")
            }
            !restrict && blockerActive -> {
                blockerActive = false
                log("SIM_ENFORCEMENT_LIFTED reason=${surfaceActiveDecisionReason(cs)}")
            }
        }
        return build(cs)
    }

    private fun surfaceActiveDecisionReason(cs: ShortsControlState): String = when {
        !surfaceActive -> "SURFACE_LEFT"
        !cs.limitReached -> "LIMIT_CLEARED"
        else -> "STATE_CHANGED"
    }

    private fun logDecision(cs: ShortsControlState, restrict: Boolean) {
        val decision = when {
            cs.enforcementState == ShortsEnforcementState.LIMIT_REACHED -> "LIMIT_REACHED"
            cs.enforcementState == ShortsEnforcementState.WARNING -> "WARNING"
            else -> "NOT_ENFORCED"
        }
        log("SIM_DECISION count=${cs.currentCount} limit=${cs.limitCount} " +
            "decision=$decision restrict=$restrict status=${cs.status}")
    }

    private fun build(cs: ShortsControlState?): DebugEnforcementSnapshot {
        // cs is non-null whenever the simulation is active (evaluate always
        // passes the fresh state); snapshot() passes null while inactive so no
        // engine read happens for an inert simulation.
        val state = if (active) (cs ?: simEngine.currentState(nowMillis())) else null
        return DebugEnforcementSnapshot(
            active = active,
            realLimit = realLimit(),
            simulatedLimit = state?.limitCount ?: 0,
            simulatedCount = state?.currentCount ?: 0,
            remainingCount = state?.remainingCount ?: 0,
            surfaceActive = surfaceActive,
            status = state?.status ?: ShortsLimitCycleStatus.DISABLED,
            enforcementState = state?.enforcementState ?: ShortsEnforcementState.ALLOW,
            limitReached = state?.limitReached ?: false,
            restrictDecision = active && state != null && shouldRestrict(surfaceActive, state),
            blockerActive = blockerActive,
            triggerCount = triggerCount,
        )
    }

    /** Execution gate — release builds can never run the simulation. */
    private val enabled: Boolean get() = BuildConfig.DEBUG

    private fun log(event: String) {
        Log.i(TAG, event)
    }
}

/**
 * Read-only snapshot of the enforcement simulation. [enforcementState],
 * [limitReached] and [status] come from the REAL engine derivation over the
 * in-memory cycle; [restrictDecision] is the REAL production show/hide
 * decision ([shouldRestrict]) evaluated with the simulated surface;
 * [triggerCount] counts how many times the enforcement edge fired during
 * this simulation session.
 */
data class DebugEnforcementSnapshot(
    val active: Boolean,
    /** The REAL configured Shorts limit (read-only, never modified). */
    val realLimit: Int,
    val simulatedLimit: Int,
    val simulatedCount: Int,
    val remainingCount: Int,
    /** Simulated "short-form surface is on screen" precondition. */
    val surfaceActive: Boolean,
    val status: ShortsLimitCycleStatus,
    val enforcementState: ShortsEnforcementState,
    val limitReached: Boolean,
    /** The real production decision: would the blocker show right now? */
    val restrictDecision: Boolean,
    /** Whether the simulated blocker is currently up (restrict active). */
    val blockerActive: Boolean,
    /** Total enforcement-trigger events this simulation session. */
    val triggerCount: Int,
) {
    /** True only in DEBUG builds — release is always inert. */
    val simulationAvailable: Boolean get() = BuildConfig.DEBUG
}
