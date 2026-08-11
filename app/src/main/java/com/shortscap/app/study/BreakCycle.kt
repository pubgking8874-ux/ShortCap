package com.shortscap.app.study

import android.content.Context
import android.util.Log
import com.shortscap.app.sounds.SoundEffectCategory
import com.shortscap.app.sounds.SoundTriggerer

/**
 * BreakCycle — the persisted break-session cycle that runs inside an active
 * Study Mode session.
 *
 * Driven by the existing Break Reminder settings ([BreakReminderConfig]): when
 * a reminder time arrives the session enters a break and plays the fixed
 * **Break Session Start** sound; once [StudySession.breakDurationMinutes] have
 * passed the break ends, plays the fixed **Break Session End** sound, and
 * study resumes. The break time is added to the session end (timestamp-based,
 * exactly like the rest of the session), so the session total is study time +
 * break time and the countdown never reaches zero mid-break.
 *
 * All state lives in [StudyPreferenceStore] — the single source of truth — so
 * the ViewModel ticker and the background
 * [com.shortscap.app.monitoring.MonitoringService] watcher can both call
 * [check] every second without ever double-firing a break sound: whichever
 * caller performs a transition first persists it, and the other caller sees
 * the new state and does nothing (the check is also synchronized for safety).
 * Every [Result] carries the AUTHORITATIVE persisted session end so callers
 * can always mirror it — even when the other caller won the race — and can
 * never finish a session mid-break.
 *
 * Like the session-end alert, a transition missed while the process was dead
 * is caught on the next [check] — the sounds honour the Sound & Effects
 * master switch via [SoundTriggerer] and a sound failure never blocks the
 * cycle (the state still advances).
 */
object BreakCycle {

    private const val TAG = "BreakCycle"

    /** Outcome of one [check] — at most ONE transition per call. */
    sealed interface Result {
        /**
         * The authoritative persisted session end after this check (study
         * time + any break time). -1 only when no session is active.
         */
        val sessionEndMillis: Long

        /** Nothing happened (no break due, break still running, or no session). */
        data class NoChange(override val sessionEndMillis: Long) : Result

        /** A break just started; the session end was extended by the break. */
        data class BreakStarted(override val sessionEndMillis: Long) : Result

        /** A break just ended; study has resumed. */
        data class BreakEnded(override val sessionEndMillis: Long) : Result
    }

    /**
     * Initializes the break schedule for a freshly started session. No breaks
     * are planned when the Break Reminder is disabled or has no interval.
     */
    fun initialize(context: Context, session: StudySession) {
        val config = session.breakReminder
        val nextBreak = if (config.enabled && config.intervalMinutes > 0) {
            session.startTimeMillis + config.intervalMinutes * 60_000L
        } else {
            -1L
        }
        StudyPreferenceStore(context).saveBreakCycle(breakStartAtMillis = -1L, nextBreakAtMillis = nextBreak)
    }

    /**
     * Advances the break cycle by at most one transition: an overdue break is
     * ended first (BREAK_END), then a due break is started (BREAK_START,
     * extending the session end by the break duration). Safe to call once a
     * second from any caller; the returned [Result.sessionEndMillis] is always
     * the authoritative persisted end, so callers mirror it regardless of
     * which caller performed the transition.
     */
    @Synchronized
    fun check(context: Context): Result {
        val store = StudyPreferenceStore(context)
        val stored = store.loadActiveSession() ?: return Result.NoChange(-1L)
        val session = stored.session
        val now = System.currentTimeMillis()
        val breakDurationMs = session.breakDurationMinutes.coerceAtLeast(1) * 60_000L

        // 1) End an overdue break first — a started break is never skipped.
        val breakStart = store.breakStartAt()
        if (breakStart >= 0L) {
            if (now < breakStart + breakDurationMs) {
                return Result.NoChange(session.endTimeMillis)
            }
            runCatching {
                SoundTriggerer.play(context, SoundEffectCategory.BREAK_END)
            }.onFailure { t -> Log.w(TAG, "Break Session End sound failed: ${t.message}", t) }
            // ONCE fires a single break per session; REPEAT keeps cycling.
            val nextBreak = when (session.breakReminder.pattern) {
                BreakReminderPattern.ONCE -> -1L
                BreakReminderPattern.REPEAT -> {
                    val prev = store.nextBreakAt()
                    if (prev >= 0L) {
                        prev + session.breakReminder.intervalMinutes.coerceAtLeast(1) * 60_000L
                    } else {
                        -1L
                    }
                }
            }
            store.saveBreakCycle(breakStartAtMillis = -1L, nextBreakAtMillis = nextBreak)
            return Result.BreakEnded(session.endTimeMillis)
        }

        // 2) Start a break when its reminder time arrives — never after the
        //    session has already finished (the end is extended by prior breaks).
        val nextBreak = store.nextBreakAt()
        if (nextBreak >= 0L && now >= nextBreak && now < session.endTimeMillis) {
            runCatching {
                SoundTriggerer.play(context, SoundEffectCategory.BREAK_START)
            }.onFailure { t -> Log.w(TAG, "Break Session Start sound failed: ${t.message}", t) }
            val newEnd = session.endTimeMillis + breakDurationMs
            // Extend the persisted end so a restore after process death uses
            // the correct total (study + break) and never ends early.
            store.extendSessionEnd(newEnd)
            store.saveBreakCycle(breakStartAtMillis = now, nextBreakAtMillis = nextBreak)
            return Result.BreakStarted(newEnd)
        }
        return Result.NoChange(session.endTimeMillis)
    }
}
