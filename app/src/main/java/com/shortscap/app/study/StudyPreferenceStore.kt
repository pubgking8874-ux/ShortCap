package com.shortscap.app.study

import android.content.Context

/**
 * Persists the ACTIVE Study Mode session across process death and app
 * reopens — the countdown is timestamp-based (wall-clock start/end), so
 * after reload the remaining duration is still exact, and an expired session
 * is detected and ended immediately on the next launch. Mirrors the
 * ThemePreferenceStore pattern (SharedPreferences).
 *
 * Only the CURRENT session is stored here (plus the pre-session restriction
 * states to restore at 00:00 and the end-alert fired flag so the "session
 * ended" sound + notification cannot be delivered twice for one session,
 * whichever caller — ViewModel ticker, resume check or the background
 * MonitoringService — fires it first). Settings and history stay in memory /
 * the future backend (StudyRepository) — this store exists purely so \"the
 * application is reopened\" never resets or loses a running session.
 */
class StudyPreferenceStore(context: Context) {

    private val prefs = context.getSharedPreferences("study_mode_prefs", Context.MODE_PRIVATE)

    /** Persists the running session + the restriction states to restore later. */
    fun saveActiveSession(
        session: StudySession,
        previousStrictMode: Boolean,
        previousMonitoringEnabled: Boolean,
    ) {
        prefs.edit()
            .putLong(KEY_START, session.startTimeMillis)
            .putLong(KEY_END, session.endTimeMillis)
            .putInt(KEY_DURATION, session.durationMinutes)
            .putBoolean(KEY_BREAK_REMINDER, session.breakReminder.enabled)
            .putInt(KEY_BREAK_INTERVAL, session.breakReminder.intervalMinutes)
            .putInt(KEY_BREAK_PATTERN, session.breakReminder.pattern.ordinal)
            .putInt(KEY_BREAK_SOUND, session.breakReminder.sound.ordinal)
            .putInt(KEY_BREAK_DURATION, session.breakDurationMinutes)
            .putInt(KEY_SOUND, session.soundMode.ordinal)
            .putString(KEY_APPS, session.allowedApps.joinToString(SEP))
            .putString(KEY_WEBSITES, session.allowedWebsites.joinToString(SEP))
            .putBoolean(KEY_PREV_STRICT, previousStrictMode)
            .putBoolean(KEY_PREV_MONITORING, previousMonitoringEnabled)
            .putBoolean(KEY_END_ALERT, false)
            .apply()
    }

    /** Returns the persisted session (if any) with the states to restore. */
    fun loadActiveSession(): StoredSession? {
        val start = prefs.getLong(KEY_START, -1L)
        val end = prefs.getLong(KEY_END, -1L)
        if (start < 0 || end < 0) return null
        return StoredSession(
            session = StudySession(
                startTimeMillis = start,
                endTimeMillis = end,
                durationMinutes = prefs.getInt(KEY_DURATION, 0),
                breakReminder = BreakReminderConfig(
                    enabled = prefs.getBoolean(KEY_BREAK_REMINDER, true),
                    intervalMinutes = prefs.getInt(KEY_BREAK_INTERVAL, 25),
                    pattern = BreakReminderPattern.entries.getOrElse(prefs.getInt(KEY_BREAK_PATTERN, 0)) { BreakReminderPattern.REPEAT },
                    sound = BreakReminderSound.entries.getOrElse(prefs.getInt(KEY_BREAK_SOUND, 0)) { BreakReminderSound.DEFAULT },
                ),
                breakDurationMinutes = prefs.getInt(KEY_BREAK_DURATION, 5),
                soundMode = StudySoundMode.entries.getOrElse(prefs.getInt(KEY_SOUND, 0)) { StudySoundMode.SOUND },
                allowedApps = (prefs.getString(KEY_APPS, "") ?: "").split(SEP).filter { it.isNotBlank() },
                allowedWebsites = (prefs.getString(KEY_WEBSITES, "") ?: "").split(SEP).filter { it.isNotBlank() },
                currentTimeMillis = System.currentTimeMillis(),
            ),
            previousStrictMode = prefs.getBoolean(KEY_PREV_STRICT, false),
            previousMonitoringEnabled = prefs.getBoolean(KEY_PREV_MONITORING, true),
            endAlertFired = prefs.getBoolean(KEY_END_ALERT, false),
        )
    }

    /**
     * Persists the in-progress Break Reminder cycle for the active session.
     * [breakStartAtMillis] is when the current break started (-1 = not in a
     * break); [nextBreakAtMillis] is the wall-clock time of the next planned
     * break (-1 = none planned). Written atomically by [BreakCycle], so the
     * ViewModel ticker and the background MonitoringService can both drive the
     * cycle without ever double-firing a break sound.
     */
    fun saveBreakCycle(breakStartAtMillis: Long, nextBreakAtMillis: Long) {
        prefs.edit()
            .putLong(KEY_BREAK_START, breakStartAtMillis)
            .putLong(KEY_NEXT_BREAK, nextBreakAtMillis)
            .apply()
    }

    /** When the current break started (wall-clock); -1 = not in a break. */
    fun breakStartAt(): Long = prefs.getLong(KEY_BREAK_START, -1L)

    /** Wall-clock time of the next planned break; -1 = none planned. */
    fun nextBreakAt(): Long = prefs.getLong(KEY_NEXT_BREAK, -1L)

    /**
     * Persists a session end extended by a break (the session total is study
     * time + break time), so a restore after process death uses the extended
     * end and never finishes the session early.
     */
    fun extendSessionEnd(newEndMillis: Long) {
        prefs.edit().putLong(KEY_END, newEndMillis).apply()
    }

    /**
     * Marks the current session's end alert (sound + notification) as already
     * delivered, so the same session can never fire it twice from any caller.
     */
    fun markEndAlertFired() {
        prefs.edit().putBoolean(KEY_END_ALERT, true).apply()
    }

    /** True once the current session's end alert was delivered. */
    fun endAlertFired(): Boolean = prefs.getBoolean(KEY_END_ALERT, false)

    fun clearActiveSession() {
        prefs.edit().clear().apply()
    }

    /** Persisted session + the pre-session restriction states to restore. */
    data class StoredSession(
        val session: StudySession,
        val previousStrictMode: Boolean,
        val previousMonitoringEnabled: Boolean,
        /** True when the end alert (sound + notification) was already delivered. */
        val endAlertFired: Boolean,
    )

    private companion object {
        const val SEP = ","
        const val KEY_START = "session_start"
        const val KEY_END = "session_end"
        const val KEY_DURATION = "session_duration_min"
        const val KEY_BREAK_REMINDER = "session_break_reminder"
        const val KEY_BREAK_INTERVAL = "session_break_interval"
        const val KEY_BREAK_PATTERN = "session_break_pattern"
        const val KEY_BREAK_SOUND = "session_break_sound"
        const val KEY_BREAK_DURATION = "session_break_duration"
        const val KEY_SOUND = "session_sound_mode"
        const val KEY_APPS = "session_allowed_apps"
        const val KEY_WEBSITES = "session_allowed_websites"
        const val KEY_PREV_STRICT = "session_prev_strict"
        const val KEY_PREV_MONITORING = "session_prev_monitoring"
        const val KEY_END_ALERT = "session_end_alert_fired"
        const val KEY_BREAK_START = "session_break_start_at"
        const val KEY_NEXT_BREAK = "session_next_break_at"
    }
}
