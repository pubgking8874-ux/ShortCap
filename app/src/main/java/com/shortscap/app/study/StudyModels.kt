package com.shortscap.app.study

/**
 * Study Mode — a self-contained feature living inside the existing General
 * settings section.
 *
 * Every model in this package stays SEPARATE from Device Monitoring, Shorts
 * Monitoring, Activity and History data: Study Mode never touches
 * MonitoringSettings.platforms, ActivityRepository records or the Web rule
 * list. A future backend synchronizes Study Mode through its own
 * [com.shortscap.app.study.StudyRepository] seam without mixing data
 * responsibilities, so the two systems can be built and extended
 * independently.
 */

/** Sound used when a Study Mode break reminder fires. */
enum class StudySoundMode { SOUND, VIBRATE, SILENT }

/**
 * Optional Study Mode active window. Configuration only today (like the
 * Monitoring Schedule) — future automation can auto-start sessions inside
 * this window without changing the UI.
 */
data class StudySchedule(
    val enabled: Boolean = false,
    /** Minutes since midnight — start of the study window (default 9:00 AM). */
    val startMinutes: Int = 9 * 60,
    /** Minutes since midnight — end of the study window (default 10:00 PM). */
    val endMinutes: Int = 22 * 60,
)

/**
 * One app or website the user keeps accessible during Study Mode sessions.
 * [id] is the stable key — an Android package name for apps, a domain for
 * websites (labels are display data, resolved from the default catalogs).
 */
data class StudyAllowedItem(val id: String, val name: String)

/**
 * Default allowed app catalog — apps that stay usable while studying
 * (long-form/educational YouTube, Google, Calculator, Gallery). The user can
 * toggle each one on the Allowed Apps/Websites screen; the catalog is data,
 * never hardcoded in the UI.
 */
val DefaultStudyAllowedApps = listOf(
    StudyAllowedItem("com.google.android.youtube", "YouTube"),
    StudyAllowedItem("com.google.android.googlequicksearchbox", "Google"),
    StudyAllowedItem("com.google.android.calculator", "Calculator"),
    StudyAllowedItem("com.google.android.apps.photos", "Gallery"),
)

/** Default allowed website catalog (study-friendly sites, always toggleable). */
val DefaultStudyAllowedWebsites = listOf(
    StudyAllowedItem("youtube.com", "YouTube"),
    StudyAllowedItem("google.com", "Google"),
    StudyAllowedItem("khanacademy.org", "Khan Academy"),
)

/**
 * All Study Mode configuration shown on the Study Mode screen (General →
 * Study Mode). Single source of truth for the section. Today the ViewModel
 * holds it locally; tomorrow GET / UPDATE Study Settings backend APIs swap in
 * behind the same shape — no UI changes required.
 */
data class StudyModeSettings(
    val studyDurationMinutes: Int = 45,
    val breakReminderEnabled: Boolean = true,
    val breakDurationMinutes: Int = 5,
    val soundMode: StudySoundMode = StudySoundMode.SOUND,
    val schedule: StudySchedule = StudySchedule(),
    val allowedApps: List<String> = DefaultStudyAllowedApps.map { it.id },
    val allowedWebsites: List<String> = DefaultStudyAllowedWebsites.map { it.id },
)

/**
 * One active (or last) Study Mode session — TIMESTAMP-BASED, not a visual
 * countdown. [startTimeMillis] / [endTimeMillis] are wall-clock epochs, so
 * the remaining duration stays exact even when the app goes to the
 * background or is reopened; [currentTimeMillis] is the latest tick and
 * [remainingMillis] is always derived from the clocks. A future backend
 * stores the same four fields (sessionStartTime, sessionEndTime, currentTime,
 * remainingDuration) 1:1.
 *
 * The session snapshot also carries the settings used for this run (duration,
 * break, sound, allowed lists) so the summary and future analytics know what
 * was configured — no lookup into mutable settings later.
 */
data class StudySession(
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Int,
    val breakReminderEnabled: Boolean,
    val breakDurationMinutes: Int,
    val soundMode: StudySoundMode,
    val allowedApps: List<String>,
    val allowedWebsites: List<String>,
    val currentTimeMillis: Long = System.currentTimeMillis(),
) {
    /** Exact remaining time, derived from the wall-clock timestamps. */
    val remainingMillis: Long get() = (endTimeMillis - currentTimeMillis).coerceAtLeast(0L)

    /** True once the current time has reached the session end. */
    val finished: Boolean get() = currentTimeMillis >= endTimeMillis
}

/**
 * Session statistics shown in the Study Session Summary section. Local,
 * in-memory aggregation today (incremented when a session completes); the
 * future backend replaces it with stored history behind the same shape.
 */
data class StudySummary(
    val sessionsToday: Int = 0,
    val minutesToday: Int = 0,
    val lastSessionDurationMinutes: Int? = null,
)

/** "45:00" or "1:05:00" — the live countdown shown on Home and in Session. */
fun formatStudyCountdown(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    fun two(n: Long): String = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
}

/** "9:00 AM" — 12-hour clock label for the Study Schedule pickers. */
fun formatStudyClock(minutesOfDay: Int): String {
    val h = ((minutesOfDay / 60) % 24 + 24) % 24
    val m = minutesOfDay % 60
    val period = if (h < 12) "AM" else "PM"
    val hour12 = when (h % 12) { 0 -> 12 else -> h % 12 }
    return "$hour12:${m.toString().padStart(2, '0')} $period"
}
