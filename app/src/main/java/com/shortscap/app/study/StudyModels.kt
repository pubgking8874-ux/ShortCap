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

/** How often Break Reminders fire while Study Mode is active. */
enum class BreakReminderPattern {
    /** The reminder fires ONCE, [BreakReminderConfig.intervalMinutes] after Study Mode starts. */
    ONCE,
    /** The reminder repeats every [BreakReminderConfig.intervalMinutes] until Study Mode ends. */
    REPEAT,
}

/**
 * Sound used when a Break Reminder fires. Front-end configuration today —
 * the actual playback connects later; each value maps 1:1 to a future sound
 * asset/notification channel without UI changes.
 */
enum class BreakReminderSound { DEFAULT, SOFT_BELL, GENTLE_CHIME, FOCUS_TONE, CUSTOM }

/** Preset \"Remind Me After\" intervals (minutes). Custom is the wheel picker. */
val BreakReminderPresetMinutes = listOf(15, 20, 25, 30, 45, 60)

/**
 * The complete Break Reminder configuration — no longer a plain ON/OFF
 * switch. [enabled] is the master switch; when ON, the first reminder fires
 * [intervalMinutes] after Study Mode starts and, if [pattern] is REPEAT,
 * every [intervalMinutes] after that until the session ends. [sound] is the
 * front-end sound preference (playback connects later).
 *
 * All values live in [StudyModeSettings] and are saved together on the Break
 * Reminder page, so Study Mode sessions can read the full configuration and
 * the future backend can persist it behind the same shape.
 */
data class BreakReminderConfig(
    val enabled: Boolean = true,
    val intervalMinutes: Int = 25,
    val pattern: BreakReminderPattern = BreakReminderPattern.REPEAT,
    val sound: BreakReminderSound = BreakReminderSound.DEFAULT,
)

/**
 * One detected overlap between the Break Reminder cycle and a scheduled
 * Study session. [reminderTimes] are the reminder clock minutes (minutes
 * since midnight) that land INSIDE the scheduled window — never modified,
 * only reported.
 */
data class BreakReminderConflict(
    val schedule: StudyScheduleEntry,
    val reminderTimes: List<Int>,
)

/**
 * Simulates the reminder cycle against every ENABLED Study Schedule and
 * reports the reminders that fall inside a scheduled session window. Break
 * Reminders and Study Schedules are separate systems: this is a warning-only
 * check that never touches the schedules themselves.
 */
fun breakReminderConflicts(
    config: BreakReminderConfig,
    schedules: List<StudyScheduleEntry>,
): List<BreakReminderConflict> {
    if (!config.enabled || config.intervalMinutes <= 0) return emptyList()
    return schedules.filter { it.enabled }.mapNotNull { schedule ->
        val windowStart = schedule.startMinutes
        val windowEnd = schedule.startMinutes + schedule.durationMinutes
        val reminders = if (config.pattern == BreakReminderPattern.ONCE) {
            listOf(windowStart + config.intervalMinutes)
        } else {
            generateSequence(1) { it + 1 }
                .map { windowStart + it * config.intervalMinutes }
                .takeWhile { it < windowEnd }
                .toList()
        }
        val times = reminders.filter { it > windowStart && it < windowEnd }
        if (times.isEmpty()) null else BreakReminderConflict(schedule, times)
    }
}

/**
 * Study Mode Home visualization — the animation shown while a session is
 * active. Today only [WATCH] (a clean watch/timer that communicates "time is
 * running / Study Mode is active / protected") is implemented; the other
 * values are future-ready placeholders so an upcoming
 * Appearance → Study Animation setting can switch them WITHOUT rebuilding
 * the Home page logic (ScStudyAnimation dispatches on this type).
 */
enum class StudyAnimationType {
    /** Watch/Timer — sweeping hand + tick marks + lock badge (current default). */
    WATCH,
    /** Study/Book animation (future). */
    BOOK,
    /** Focus/Cartoon animation (future). */
    FOCUS,
}

/** One day of the week a Study Schedule entry can repeat on. */
enum class StudyDay { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

/** Mon → Sun — the canonical display/selection order for [StudyDay]. */
val StudyDaysInOrder = listOf(
    StudyDay.MONDAY,
    StudyDay.TUESDAY,
    StudyDay.WEDNESDAY,
    StudyDay.THURSDAY,
    StudyDay.FRIDAY,
    StudyDay.SATURDAY,
    StudyDay.SUNDAY,
)

/**
 * One scheduled Study Mode session. Each schedule keeps its OWN subject,
 * selected days, start time, study duration, reminder preference and
 * enabled state — editing one schedule never touches another.
 *
 * The reminder is "how long BEFORE the start" ([reminderMinutesBefore],
 * null = no reminder). The reminder CLOCK time is always DERIVED
 * (start − reminder) via [reminderTimeMinutes] — the user never enters a
 * separate reminder clock time.
 *
 * Future notification support: a scheduler can read [reminderTimeMinutes]
 * to fire a "<subject> starts in …" notification at the right moment and
 * [startMinutes] + [durationMinutes] to auto-start the EXISTING Study Mode
 * session (same countdown/restriction architecture — no second system).
 */
data class StudyScheduleEntry(
    /** Stable identity for add/edit/delete — never shown to the user. */
    val id: String,
    val subject: String,
    val days: Set<StudyDay>,
    /** Minutes since midnight — when the session starts. */
    val startMinutes: Int,
    /** Minutes — how long the session runs (feeds the existing countdown). */
    val durationMinutes: Int,
    /** Minutes before [startMinutes] to remind; null = no reminder. */
    val reminderMinutesBefore: Int? = null,
    val enabled: Boolean = true,
) {
    /** Reminder clock time = start − reminder (wraps safely into the day). */
    val reminderTimeMinutes: Int?
        get() = reminderMinutesBefore?.let {
            ((startMinutes - it) % (24 * 60) + (24 * 60)) % (24 * 60)
        }
}

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
 * Social-media / short-form entertainment packages that must NOT be offered
 * as selectable allowed apps while Study Mode is active (Instagram, Facebook,
 * Snapchat, TikTok, X/Twitter, Reddit, etc.).
 *
 * This is a FRONTEND filter only: the Add App picker hides these packages so
 * they can never be whitelisted through the UI. Backend enforcement is a
 * later task and is deliberately NOT implemented here (no fake enforcement).
 */
val RestrictedStudyApps = setOf(
    "com.instagram.android",                // Instagram
    "com.facebook.katana",                  // Facebook
    "com.facebook.orca",                    // Messenger
    "com.snapchat.android",                 // Snapchat
    "com.zhiliaoapp.musically",             // TikTok
    "com.ss.android.ugc.aweme",             // TikTok (secondary)
    "com.twitter.android",                  // X / Twitter
    "com.reddit.frontpage",                 // Reddit
    "com.reddit.lite",                      // Reddit (lite)
    "com.google.android.apps.youtube.music", // YouTube Music (short-form entertainment)
    "com.google.android.youtube",           // YouTube — its Shorts feed is short-form, so the app is
    // only whitelistable via the study catalog toggle, never re-added through the picker.
)

/**
 * All Study Mode configuration shown on the Study Mode screen (General →
 * Study Mode). Single source of truth for the section. Today the ViewModel
 * holds it locally; tomorrow GET / UPDATE Study Settings backend APIs swap in
 * behind the same shape — no UI changes required.
 */
data class StudyModeSettings(
    val studyDurationMinutes: Int = 45,
    /** Full Break Reminder configuration (enabled / interval / pattern / sound). */
    val breakReminder: BreakReminderConfig = BreakReminderConfig(),
    val breakDurationMinutes: Int = 5,
    val soundMode: StudySoundMode = StudySoundMode.SOUND,
    /** Multiple schedules — each with its own subject, days, start, duration, reminder, enabled. */
    val schedules: List<StudyScheduleEntry> = emptyList(),
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
    /** Full Break Reminder configuration active during this run (front-end today; the timer/notification system reads it later). */
    val breakReminder: BreakReminderConfig,
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
