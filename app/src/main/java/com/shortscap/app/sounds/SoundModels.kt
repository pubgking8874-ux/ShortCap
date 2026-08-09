package com.shortscap.app.sounds

/**
 * Sound & Effects — the CENTRAL control panel for every ShortsCap app sound.
 *
 * This is NOT the Android device Sound / Vibrate / Silent mode (that stays
 * owned by Study Mode → Sound Mode via system audio access). This system owns
 * ShortsCap's own notification sounds and effects only: one master switch
 * ([SoundEffectsConfig.appSoundsEnabled]) plus one selectable [AppSound] per
 * [SoundEffectCategory].
 *
 * Future features read their sound from this ONE configuration (Break
 * Reminder, Study Schedule reminders, Shorts limit alerts, break start/end)
 * so there is exactly one sound preference system — never per-feature
 * copies. Custom sounds plug in later as additional [AppSound] values or a
 * per-category custom override without changing the screen.
 */

/** Which ShortsCap feature a sound belongs to. */
enum class SoundEffectCategory {
    STUDY_SESSION_START,
    STUDY_SESSION_END,
    BREAK_REMINDER,
    SCHEDULE_REMINDER,
    SHORTS_LIMIT_WARNING,
    SHORTS_LIMIT_REACHED,
    BREAK_START,
    BREAK_END,
    NOTIFICATION_SOUND,
}

/** Every app sound available in the central sound library. */
enum class AppSound {
    DEFAULT,
    GENTLE_CHIME,
    SOFT_BELL,
    CALM_TONE,
    FOCUS_TONE,
    WARNING_PULSE,
    LIMIT_ALERT,
    SUCCESS_CHIME,
}

/**
 * The complete Sound & Effects configuration — the single source of truth
 * every feature reads when it needs to play a ShortsCap sound. [selected]
 * holds one [AppSound] per [SoundEffectCategory]; missing entries fall back
 * to the category default so new categories never break consumers.
 */
data class SoundEffectsConfig(
    /** Master switch: OFF disables every ShortsCap sound and effect. */
    val appSoundsEnabled: Boolean = true,
    /** One sound per category (falls back to the category default). */
    val selected: Map<SoundEffectCategory, AppSound> = SoundEffectsConfig.defaultSelection(),
) {
    fun soundFor(category: SoundEffectCategory): AppSound = selected[category] ?: AppSound.DEFAULT

    fun withMaster(enabled: Boolean): SoundEffectsConfig = copy(appSoundsEnabled = enabled)

    fun withSound(category: SoundEffectCategory, sound: AppSound): SoundEffectsConfig =
        copy(selected = selected + (category to sound))

    companion object {
        /** Per-category default sounds — a tasteful starting library. */
        fun defaultSelection(): Map<SoundEffectCategory, AppSound> = mapOf(
            SoundEffectCategory.STUDY_SESSION_START to AppSound.FOCUS_TONE,
            SoundEffectCategory.STUDY_SESSION_END to AppSound.SUCCESS_CHIME,
            SoundEffectCategory.BREAK_REMINDER to AppSound.GENTLE_CHIME,
            SoundEffectCategory.SCHEDULE_REMINDER to AppSound.SOFT_BELL,
            SoundEffectCategory.SHORTS_LIMIT_WARNING to AppSound.WARNING_PULSE,
            SoundEffectCategory.SHORTS_LIMIT_REACHED to AppSound.LIMIT_ALERT,
            SoundEffectCategory.BREAK_START to AppSound.CALM_TONE,
            SoundEffectCategory.BREAK_END to AppSound.SUCCESS_CHIME,
            SoundEffectCategory.NOTIFICATION_SOUND to AppSound.GENTLE_CHIME,
        )
    }
}
