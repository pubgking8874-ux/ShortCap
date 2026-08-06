package com.shortscap.app.notifications

/**
 * The 6 notification categories shown on Settings → Notifications.
 *
 * Every category maps 1:1 to a future backend `GET /notifications/categories`
 * entry and opens its own dedicated screen — nothing ever expands inline.
 */
enum class NotificationCategory {
    REMINDERS,
    LIMIT_ALERTS,
    BLOCK_NOTIFICATIONS,
    WEEKLY_INSIGHTS,
    SYSTEM_NOTIFICATIONS,
    SOUND_VIBRATION,
}

/**
 * Every notification option in the app. Each id maps 1:1 to a future backend
 * `GET /notifications/settings` entry, so adding or removing an option only
 * touches this enum + the i18n catalog.
 *
 * [category] groups options onto the dedicated category page they belong to.
 */
enum class NotificationSettingId(val category: NotificationCategory) {
    // 1. Reminder Notifications
    DAILY_USAGE_REMINDER(NotificationCategory.REMINDERS),
    DAILY_SCREEN_TIME_SUMMARY(NotificationCategory.REMINDERS),
    GOAL_ACHIEVEMENT(NotificationCategory.REMINDERS),

    // 2. Limit Alerts
    LIMIT_50_PERCENT(NotificationCategory.LIMIT_ALERTS),
    LIMIT_80_PERCENT(NotificationCategory.LIMIT_ALERTS),
    LIMIT_100_PERCENT(NotificationCategory.LIMIT_ALERTS),

    // 3. Block Notifications
    APP_BLOCKED_ALERT(NotificationCategory.BLOCK_NOTIFICATIONS),
    RESTRICTION_MESSAGE(NotificationCategory.BLOCK_NOTIFICATIONS),

    // 4. Weekly Insights
    WEEKLY_PROGRESS_REPORT(NotificationCategory.WEEKLY_INSIGHTS),
    WEEKLY_ACHIEVEMENT(NotificationCategory.WEEKLY_INSIGHTS),

    // 5. System Notifications
    PERMISSION_REMINDER(NotificationCategory.SYSTEM_NOTIFICATIONS),
    MONITORING_STOPPED(NotificationCategory.SYSTEM_NOTIFICATIONS),
    BACKGROUND_SERVICE_STATUS(NotificationCategory.SYSTEM_NOTIFICATIONS),

    // 6. Sound & Vibration
    NOTIFICATION_SOUND(NotificationCategory.SOUND_VIBRATION),
    VIBRATION(NotificationCategory.SOUND_VIBRATION),
}

/**
 * One notification option — the single shape every Notifications screen
 * consumes.
 *
 * Backend-ready by design:
 *  - [id]          → unique backend setting identifier
 *  - [enabled]     → current on/off state (persisted locally today)
 *  - [cloudSyncEnabled] → placeholder for future cloud-sync state
 *  - [analyticsEvent]   → placeholder for future analytics event names
 *
 * Swapping the data source (local SharedPreferences → backend API) requires
 * NO UI changes.
 */
data class NotificationSetting(
    val id: NotificationSettingId,
    val enabled: Boolean = true,
    // Future cloud sync placeholder — set when backend sync connects.
    val cloudSyncEnabled: Boolean = false,
    // Future analytics placeholder — event key tracked on toggle changes.
    val analyticsEvent: String? = null,
)
