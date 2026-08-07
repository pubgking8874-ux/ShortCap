package com.shortscap.app.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Quickreply
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.notifications.NotificationCategory
import com.shortscap.app.notifications.NotificationSettingId

/** Leading icon for a notification category row / category page. */
fun notificationCategoryIcon(category: NotificationCategory): ImageVector = when (category) {
    NotificationCategory.REMINDERS -> Icons.Filled.NotificationsActive
    NotificationCategory.LIMIT_ALERTS -> Icons.Filled.Speed
    NotificationCategory.BLOCK_NOTIFICATIONS -> Icons.Filled.Block
    NotificationCategory.WEEKLY_INSIGHTS -> Icons.Filled.Insights
    NotificationCategory.SYSTEM_NOTIFICATIONS -> Icons.Filled.AdminPanelSettings
    NotificationCategory.SOUND_VIBRATION -> Icons.Filled.VolumeUp
}

/**
 * Semantic icon key for a notification category — lets the centralized icon
 * system color each category with its own color in the Vibrant style (the
 * icon vector itself stays [notificationCategoryIcon]).
 */
fun notificationCategoryIconKey(category: NotificationCategory): IconKey = when (category) {
    NotificationCategory.REMINDERS -> IconKey.NOTIF_REMINDERS
    NotificationCategory.LIMIT_ALERTS -> IconKey.NOTIF_LIMIT_ALERTS
    NotificationCategory.BLOCK_NOTIFICATIONS -> IconKey.NOTIF_BLOCK
    NotificationCategory.WEEKLY_INSIGHTS -> IconKey.NOTIF_WEEKLY_INSIGHTS
    NotificationCategory.SYSTEM_NOTIFICATIONS -> IconKey.NOTIF_SYSTEM
    NotificationCategory.SOUND_VIBRATION -> IconKey.NOTIF_SOUND
}

/**
 * Semantic icon key for a single notification option — each option inherits
 * its category's color in the Vibrant style (the icon vector stays
 * [notificationSettingIcon]).
 */
fun notificationSettingIconKey(id: NotificationSettingId): IconKey =
    notificationCategoryIconKey(id.category)

/** Localized title for a notification category. */
fun notificationCategoryTitle(category: NotificationCategory, strings: AppStrings): String = when (category) {
    NotificationCategory.REMINDERS -> strings.notifReminders
    NotificationCategory.LIMIT_ALERTS -> strings.notifLimitAlerts
    NotificationCategory.BLOCK_NOTIFICATIONS -> strings.notifBlockNotifications
    NotificationCategory.WEEKLY_INSIGHTS -> strings.notifWeeklyInsights
    NotificationCategory.SYSTEM_NOTIFICATIONS -> strings.notifSystemNotifications
    NotificationCategory.SOUND_VIBRATION -> strings.notifSoundVibration
}

/** Leading icon for a single notification option. */
fun notificationSettingIcon(id: NotificationSettingId): ImageVector = when (id) {
    NotificationSettingId.DAILY_USAGE_REMINDER -> Icons.Filled.Alarm
    NotificationSettingId.DAILY_SCREEN_TIME_SUMMARY -> Icons.Filled.Timelapse
    NotificationSettingId.GOAL_ACHIEVEMENT -> Icons.Filled.EmojiEvents
    NotificationSettingId.LIMIT_50_PERCENT -> Icons.Filled.HourglassBottom
    NotificationSettingId.LIMIT_80_PERCENT -> Icons.Filled.HourglassFull
    NotificationSettingId.LIMIT_100_PERCENT -> Icons.Filled.AlarmOn
    NotificationSettingId.APP_BLOCKED_ALERT -> Icons.Filled.DoNotDisturbOn
    NotificationSettingId.RESTRICTION_MESSAGE -> Icons.Filled.Quickreply
    NotificationSettingId.WEEKLY_PROGRESS_REPORT -> Icons.Filled.Assessment
    NotificationSettingId.WEEKLY_ACHIEVEMENT -> Icons.Filled.Star
    NotificationSettingId.PERMISSION_REMINDER -> Icons.Filled.AdminPanelSettings
    NotificationSettingId.MONITORING_STOPPED -> Icons.Filled.PauseCircle
    NotificationSettingId.BACKGROUND_SERVICE_STATUS -> Icons.Filled.SyncProblem
    NotificationSettingId.NOTIFICATION_SOUND -> Icons.Filled.MusicNote
    NotificationSettingId.VIBRATION -> Icons.Filled.Vibration
}

/** Localized title for a single notification option. */
fun notificationSettingTitle(id: NotificationSettingId, strings: AppStrings): String = when (id) {
    NotificationSettingId.DAILY_USAGE_REMINDER -> strings.notifDailyUsageReminder
    NotificationSettingId.DAILY_SCREEN_TIME_SUMMARY -> strings.notifDailyScreenTimeSummary
    NotificationSettingId.GOAL_ACHIEVEMENT -> strings.notifGoalAchievement
    NotificationSettingId.LIMIT_50_PERCENT -> strings.notifLimit50
    NotificationSettingId.LIMIT_80_PERCENT -> strings.notifLimit80
    NotificationSettingId.LIMIT_100_PERCENT -> strings.notifLimit100
    NotificationSettingId.APP_BLOCKED_ALERT -> strings.notifAppBlockedAlert
    NotificationSettingId.RESTRICTION_MESSAGE -> strings.notifRestrictionMessage
    NotificationSettingId.WEEKLY_PROGRESS_REPORT -> strings.notifWeeklyProgressReport
    NotificationSettingId.WEEKLY_ACHIEVEMENT -> strings.notifWeeklyAchievement
    NotificationSettingId.PERMISSION_REMINDER -> strings.notifPermissionReminder
    NotificationSettingId.MONITORING_STOPPED -> strings.notifMonitoringStopped
    NotificationSettingId.BACKGROUND_SERVICE_STATUS -> strings.notifBackgroundServiceStatus
    NotificationSettingId.NOTIFICATION_SOUND -> strings.notifNotificationSound
    NotificationSettingId.VIBRATION -> strings.notifVibration
}

/** Localized short description (purpose) for a single notification option. */
fun notificationSettingDescription(id: NotificationSettingId, strings: AppStrings): String = when (id) {
    NotificationSettingId.DAILY_USAGE_REMINDER -> strings.notifDailyUsageReminderDesc
    NotificationSettingId.DAILY_SCREEN_TIME_SUMMARY -> strings.notifDailyScreenTimeSummaryDesc
    NotificationSettingId.GOAL_ACHIEVEMENT -> strings.notifGoalAchievementDesc
    NotificationSettingId.LIMIT_50_PERCENT -> strings.notifLimit50Desc
    NotificationSettingId.LIMIT_80_PERCENT -> strings.notifLimit80Desc
    NotificationSettingId.LIMIT_100_PERCENT -> strings.notifLimit100Desc
    NotificationSettingId.APP_BLOCKED_ALERT -> strings.notifAppBlockedAlertDesc
    NotificationSettingId.RESTRICTION_MESSAGE -> strings.notifRestrictionMessageDesc
    NotificationSettingId.WEEKLY_PROGRESS_REPORT -> strings.notifWeeklyProgressReportDesc
    NotificationSettingId.WEEKLY_ACHIEVEMENT -> strings.notifWeeklyAchievementDesc
    NotificationSettingId.PERMISSION_REMINDER -> strings.notifPermissionReminderDesc
    NotificationSettingId.MONITORING_STOPPED -> strings.notifMonitoringStoppedDesc
    NotificationSettingId.BACKGROUND_SERVICE_STATUS -> strings.notifBackgroundServiceStatusDesc
    NotificationSettingId.NOTIFICATION_SOUND -> strings.notifNotificationSoundDesc
    NotificationSettingId.VIBRATION -> strings.notifVibrationDesc
}
