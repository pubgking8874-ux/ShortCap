package com.shortscap.app.shorts

/**
 * The Shorts Limit page (Settings → Short Control → Shorts Limit) — pure,
 * testable state model. The page NEVER counts Shorts or owns a cycle: it
 * renders the AUTHORITATIVE [ShortsControlState] produced by
 * [ShortsControlEngine] (the single source of truth shared with the HUD and
 * the future enforcement layer) plus the backend sync status.
 *
 * Everything in this file is a pure function / value type — no Android
 * runtime — so the 9 page states, the limit input validation and the
 * progress / remaining / countdown math are unit-tested directly.
 */

/** The explicit page states the Shorts Limit screen renders. */
enum class ShortsLimitPageState {
    /** First frame before the durable cycle state is read. */
    LOADING,

    /** No limit has ever been configured — clean first-time setup. */
    NO_LIMIT_CONFIGURED,

    /** The setup form is on screen (first-time setup or after disable). */
    LIMIT_SETUP,

    /**
     * The limit is SAVED but the 24-hour cycle has NOT been activated yet —
     * timer NOT started, limit NOT locked, editing available. The user must
     * press ACTIVE to start the cycle.
     */
    READY_TO_ACTIVATE,

    /** An active 24-hour cycle is running under the limit. */
    ACTIVE,

    /** Active cycle and a configured warning threshold was reached. */
    WARNING,

    /** currentCount >= limitCount — persists until the cycle expires. */
    LIMIT_REACHED,

    /** The window passed cycleExpiresAt (no auto-roll — user re-activates). */
    EXPIRED,

    /** Shorts control is disabled — history kept, nothing counts. */
    DISABLED,
}

/** Best-effort backend sync status for the Shorts Control state. */
enum class ShortsSyncStatus {
    /** No sync attempted yet (or sync not wired). */
    IDLE,

    /** A sync push is in flight. */
    SYNCING,

    /** The last push reached the backend. */
    SYNCED,

    /** Backend unreachable — the durable LOCAL state is authoritative. */
    OFFLINE,

    /** The backend rejected the push (server error) — local state kept. */
    ERROR,
}

/**
 * Derives the page state from the authoritative engine state.
 *
 * ACTIVATION FLOW: CONFIGURE → SAVE → READY_TO_ACTIVATE → user presses
 * ACTIVE → 24-hour cycle starts → ACTIVE/LIMIT_REACHED → EXPIRED. A saved
 * but not yet activated limit (status CONFIGURED) is READY_TO_ACTIVATE —
 * the timer has NOT started, the limit is NOT locked and editing stays
 * available. There is no enable/disable toggle, so a missing cycle (or a
 * DISABLED status from a stale backend state) always means \"set a limit\"
 * — the first-time setup screen. Expiry is surfaced from the engine's own
 * transition (EXPIRED status); the engine does NOT auto-roll a new cycle —
 * the user edits (if desired) and presses ACTIVE again.
 */
fun deriveLimitPageState(state: ShortsControlState): ShortsLimitPageState {
    val cycle = state.cycle
    if (cycle == null) return ShortsLimitPageState.NO_LIMIT_CONFIGURED
    return when (state.status) {
        ShortsLimitCycleStatus.CONFIGURED -> ShortsLimitPageState.READY_TO_ACTIVATE
        ShortsLimitCycleStatus.ACTIVE ->
            if (state.warningTriggered) ShortsLimitPageState.WARNING
            else ShortsLimitPageState.ACTIVE
        ShortsLimitCycleStatus.LIMIT_REACHED -> ShortsLimitPageState.LIMIT_REACHED
        ShortsLimitCycleStatus.EXPIRED -> ShortsLimitPageState.EXPIRED
        ShortsLimitCycleStatus.DISABLED -> ShortsLimitPageState.NO_LIMIT_CONFIGURED
    }
}

// ---------------------------------------------------------------------------
// Limit input validation
// ---------------------------------------------------------------------------

/** Product-safe upper bound for a custom Shorts limit. Values above this are
 * rejected (\"absurdly large\" guard) — never silently converted. */
const val DEFAULT_LIMIT_UPPER_BOUND = 10_000

/** Why a custom limit input is invalid. */
enum class LimitInputError {
    NOT_A_NUMBER,
    NOT_POSITIVE,
    TOO_LARGE,
}

/** Result of parsing the custom limit text field. */
sealed class LimitInputResult {
    data class Valid(val value: Int) : LimitInputResult()
    object Empty : LimitInputResult()
    data class Invalid(val reason: LimitInputError) : LimitInputResult()
}

/**
 * Parses the raw limit text. Empty text -> [LimitInputResult.Empty]; anything
 * that is not a positive whole number at or below [upperBound] is rejected
 * with an explicit reason — invalid input is never silently converted.
 */
fun parseLimitInput(text: String, upperBound: Int = DEFAULT_LIMIT_UPPER_BOUND): LimitInputResult {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return LimitInputResult.Empty
    val value = trimmed.toIntOrNull()
        ?: return LimitInputResult.Invalid(LimitInputError.NOT_A_NUMBER)
    if (value <= 0) return LimitInputResult.Invalid(LimitInputError.NOT_POSITIVE)
    if (value > upperBound) return LimitInputResult.Invalid(LimitInputError.TOO_LARGE)
    return LimitInputResult.Valid(value)
}

// ---------------------------------------------------------------------------
// Progress / remaining / countdown math
// ---------------------------------------------------------------------------

/**
 * Circular progress = currentCount / limitCount, clamped to 0..1. Never
 * divides by zero (limitCount <= 0 -> 0.0 — an empty circle).
 */
fun limitProgressFraction(currentCount: Int, limitCount: Int): Float =
    if (limitCount > 0) (currentCount.toFloat() / limitCount).coerceIn(0f, 1f) else 0f

/** Remaining Shorts = limit - current, never negative. */
fun limitRemainingCount(currentCount: Int, limitCount: Int): Int =
    (limitCount - currentCount).coerceAtLeast(0)

/** Whole hours + minutes of [remainingMillis] (countdown is DERIVED from the
 * authoritative expiry timestamp at display time — never persisted). */
fun remainingHoursMinutes(remainingMillis: Long): Pair<Long, Long> {
    val clamped = remainingMillis.coerceAtLeast(0L)
    return (clamped / 3_600_000L) to ((clamped % 3_600_000L) / 60_000L)
}

/**
 * Formats [remainingMillis] as the HH:MM:SS 24-hour countdown the circular
 * timer displays (e.g. 24:00:00 at cycle start, 00:00:00 at expiry). Derived
 * from the authoritative expiry timestamp at display time — never persisted.
 */
fun remainingCountdownHms(remainingMillis: Long): String {
    val clamped = remainingMillis.coerceAtLeast(0L)
    val totalSeconds = clamped / 1_000L
    val h = totalSeconds / 3_600L
    val m = (totalSeconds % 3_600L) / 60L
    val s = totalSeconds % 60L
    return "%02d:%02d:%02d".format(h, m, s)
}

/**
 * TIME progress of the 24-hour cycle: remaining / full cycle, clamped 0..1.
 * Full circle (1.0) = a full 24 hours remaining at cycle start; the circle
 * depletes toward 0 at expiry. This is the TIME clock — completely separate
 * from the Shorts usage fraction ([limitProgressFraction]); the two are never
 * combined mathematically.
 */
fun timeProgressFraction(remainingMillis: Long, cycleMillis: Long): Float =
    if (cycleMillis > 0) (remainingMillis.toFloat() / cycleMillis).coerceIn(0f, 1f) else 0f
