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
 * states to restore at 00:00). Settings and history stay in memory / the
 * future backend (StudyRepository) — this store exists purely so \"the
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
            .putBoolean(KEY_BREAK_REMINDER, session.breakReminderEnabled)
            .putInt(KEY_BREAK_DURATION, session.breakDurationMinutes)
            .putInt(KEY_SOUND, session.soundMode.ordinal)
            .putString(KEY_APPS, session.allowedApps.joinToString(SEP))
            .putString(KEY_WEBSITES, session.allowedWebsites.joinToString(SEP))
            .putBoolean(KEY_PREV_STRICT, previousStrictMode)
            .putBoolean(KEY_PREV_MONITORING, previousMonitoringEnabled)
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
                breakReminderEnabled = prefs.getBoolean(KEY_BREAK_REMINDER, true),
                breakDurationMinutes = prefs.getInt(KEY_BREAK_DURATION, 5),
                soundMode = StudySoundMode.entries.getOrElse(prefs.getInt(KEY_SOUND, 0)) { StudySoundMode.SOUND },
                allowedApps = (prefs.getString(KEY_APPS, "") ?: "").split(SEP).filter { it.isNotBlank() },
                allowedWebsites = (prefs.getString(KEY_WEBSITES, "") ?: "").split(SEP).filter { it.isNotBlank() },
                currentTimeMillis = System.currentTimeMillis(),
            ),
            previousStrictMode = prefs.getBoolean(KEY_PREV_STRICT, false),
            previousMonitoringEnabled = prefs.getBoolean(KEY_PREV_MONITORING, true),
        )
    }

    fun clearActiveSession() {
        prefs.edit().clear().apply()
    }

    /** Persisted session + the pre-session restriction states to restore. */
    data class StoredSession(
        val session: StudySession,
        val previousStrictMode: Boolean,
        val previousMonitoringEnabled: Boolean,
    )

    private companion object {
        const val SEP = ","
        const val KEY_START = "session_start"
        const val KEY_END = "session_end"
        const val KEY_DURATION = "session_duration_min"
        const val KEY_BREAK_REMINDER = "session_break_reminder"
        const val KEY_BREAK_DURATION = "session_break_duration"
        const val KEY_SOUND = "session_sound_mode"
        const val KEY_APPS = "session_allowed_apps"
        const val KEY_WEBSITES = "session_allowed_websites"
        const val KEY_PREV_STRICT = "session_prev_strict"
        const val KEY_PREV_MONITORING = "session_prev_monitoring"
    }
}
