package com.shortscap.app.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.sounds.AppSound
import com.shortscap.app.sounds.SoundEffectCategory

/**
 * Sound & Effects — shared UI mappings for every Sound & Effects screen.
 *
 * The Sound & Effects system is ONE unified settings area: the main hub
 * (SoundEffectsScreen) hosts three navigation sections (Study Mode /
 * Monitoring / Notifications); each section opens a [SoundGroup] list; every
 * row opens the same individual sound configuration screen. All labels,
 * descriptions and icons are resolved here so every screen stays consistent.
 */
enum class SoundGroup(val categories: List<SoundEffectCategory>) {
    /** Study & break related sounds — six configurable options. */
    STUDY(
        listOf(
            SoundEffectCategory.STUDY_SESSION_START,
            SoundEffectCategory.STUDY_SESSION_END,
            SoundEffectCategory.BREAK_REMINDER,
            SoundEffectCategory.BREAK_START,
            SoundEffectCategory.BREAK_END,
            SoundEffectCategory.SCHEDULE_REMINDER,
        ),
    ),
    /** Shorts usage & limit sounds — two configurable options. */
    MONITORING(
        listOf(
            SoundEffectCategory.SHORTS_LIMIT_WARNING,
            SoundEffectCategory.SHORTS_LIMIT_REACHED,
        ),
    ),
    /** General notification sound — one configurable option. */
    NOTIFICATIONS(listOf(SoundEffectCategory.NOTIFICATION_SOUND)),
}

/** Localized section title for a [SoundGroup] (also used as its top-bar title). */
fun soundGroupTitle(strings: AppStrings, group: SoundGroup): String = when (group) {
    SoundGroup.STUDY -> strings.soundStudySection
    SoundGroup.MONITORING -> strings.soundMonitoringSection
    SoundGroup.NOTIFICATIONS -> strings.soundNotificationsSection
}

/** Localized info-popup title shown when tapping the ⓘ on a section heading. */
fun sectionInfoTitle(strings: AppStrings, group: SoundGroup): String = when (group) {
    SoundGroup.STUDY -> strings.soundStudyInfoTitle
    SoundGroup.MONITORING -> strings.soundMonitoringInfoTitle
    SoundGroup.NOTIFICATIONS -> strings.soundNotificationsInfoTitle
}

/** Localized info-popup description shown when tapping the ⓘ on a section heading. */
fun sectionInfoDescription(strings: AppStrings, group: SoundGroup): String = when (group) {
    SoundGroup.STUDY -> strings.soundStudyInfoDesc
    SoundGroup.MONITORING -> strings.soundMonitoringInfoDesc
    SoundGroup.NOTIFICATIONS -> strings.soundNotificationsInfoDesc
}

/** Localized label for one sound option. */
fun categoryLabel(strings: AppStrings, category: SoundEffectCategory): String = when (category) {
    SoundEffectCategory.STUDY_SESSION_START -> strings.soundStudySessionStart
    SoundEffectCategory.STUDY_SESSION_END -> strings.soundStudySessionEnd
    SoundEffectCategory.BREAK_REMINDER -> strings.soundEffectsBreakReminder
    SoundEffectCategory.SCHEDULE_REMINDER -> strings.soundEffectsScheduleReminder
    SoundEffectCategory.SHORTS_LIMIT_WARNING -> strings.soundEffectsLimitWarning
    SoundEffectCategory.SHORTS_LIMIT_REACHED -> strings.soundEffectsLimitReached
    SoundEffectCategory.BREAK_START -> strings.soundBreakSessionStart
    SoundEffectCategory.BREAK_END -> strings.soundBreakSessionEnd
    SoundEffectCategory.NOTIFICATION_SOUND -> strings.notifNotificationSound
}

/** Localized one-line description of when a sound option is used. */
fun categoryDescription(strings: AppStrings, category: SoundEffectCategory): String = when (category) {
    SoundEffectCategory.STUDY_SESSION_START -> strings.soundStudySessionStartDesc
    SoundEffectCategory.STUDY_SESSION_END -> strings.soundStudySessionEndDesc
    SoundEffectCategory.BREAK_REMINDER -> strings.soundBreakReminderDesc
    SoundEffectCategory.SCHEDULE_REMINDER -> strings.soundScheduleReminderDesc
    SoundEffectCategory.SHORTS_LIMIT_WARNING -> strings.soundLimitWarningDesc
    SoundEffectCategory.SHORTS_LIMIT_REACHED -> strings.soundLimitReachedDesc
    SoundEffectCategory.BREAK_START -> strings.soundBreakSessionStartDesc
    SoundEffectCategory.BREAK_END -> strings.soundBreakSessionEndDesc
    SoundEffectCategory.NOTIFICATION_SOUND -> strings.soundNotificationDesc
}

/** Per-option icon — study/alert themed, matching the option meaning. */
fun categoryIcon(category: SoundEffectCategory): ImageVector = when (category) {
    SoundEffectCategory.STUDY_SESSION_START -> Icons.Filled.PlayCircle
    SoundEffectCategory.STUDY_SESSION_END -> Icons.Filled.StopCircle
    SoundEffectCategory.BREAK_REMINDER -> Icons.Filled.LocalCafe
    SoundEffectCategory.SCHEDULE_REMINDER -> Icons.Filled.Alarm
    SoundEffectCategory.SHORTS_LIMIT_WARNING -> Icons.Filled.WarningAmber
    SoundEffectCategory.SHORTS_LIMIT_REACHED -> Icons.Filled.Block
    SoundEffectCategory.BREAK_START -> Icons.Filled.PlayArrow
    SoundEffectCategory.BREAK_END -> Icons.Filled.CheckCircle
    SoundEffectCategory.NOTIFICATION_SOUND -> Icons.Filled.Notifications
}

/** Localized sound-library label. */
fun soundLabel(strings: AppStrings, sound: AppSound): String = when (sound) {
    AppSound.DEFAULT -> strings.appSoundDefault
    AppSound.GENTLE_CHIME -> strings.appSoundGentleChime
    AppSound.SOFT_BELL -> strings.appSoundSoftBell
    AppSound.CALM_TONE -> strings.appSoundCalmTone
    AppSound.FOCUS_TONE -> strings.appSoundFocusTone
    AppSound.WARNING_PULSE -> strings.appSoundWarningPulse
    AppSound.LIMIT_ALERT -> strings.appSoundLimitAlert
    AppSound.SUCCESS_CHIME -> strings.appSoundSuccessChime
}
