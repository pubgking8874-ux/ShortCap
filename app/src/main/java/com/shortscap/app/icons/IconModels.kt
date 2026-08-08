package com.shortscap.app.icons

/**
 * Centralized Icon System — models.
 *
 * Every screen in ShortsCap requests its icons through [IconKey] instead of
 * hardcoding ImageVectors, and the active [IconStyle] decides which icon,
 * tint and container treatment is returned (see [IconTheme]).
 *
 * Two styles ship today:
 *  - [IconStyle.ORIGINAL] — the classic ShortsCap blue/black icon system
 *    (the default since the first install; visually identical to the icons
 *    used before this system existed).
 *  - [IconStyle.VIBRANT]  — colorful category-based icons: every category
 *    gets its own recognizable icon + color inside a tinted rounded-square
 *    container.
 *
 * Extending the system with a new style (MINIMAL, COLORFUL, FUTURISTIC, …)
 * is one enum entry + one branch in [IconTheme] — screens never change.
 */
enum class IconStyle {

    /** Classic ShortsCap blue-black icon system — the default. */
    ORIGINAL,

    /** Colorful category-based icon system with per-category colors. */
    VIBRANT;

    companion object {
        /** Default style — used on first install, after Reset All Settings
         *  and whenever no preference has been stored yet. */
        val DEFAULT: IconStyle = ORIGINAL
    }
}

/**
 * Semantic icon keys — the stable vocabulary screens use to request icons.
 *
 * A key represents one category / destination / concept (e.g. MONITORING,
 * PERMISSIONS, DATA_BACKUP). [IconTheme] maps every key to the icon, tint
 * and container background for the active [IconStyle], so screens never
 * touch ImageVectors or colors directly and future styles require zero
 * screen changes.
 */
enum class IconKey {
    // ---- Bottom navigation ----
    HOME,
    ACTIVITY,
    WEB,
    SETTINGS,

    // ---- Web (analytics + website rules) ----
    WEB_ANALYTICS,
    WEB_BLOCKED,
    WEB_ALLOWED,

    // ---- Settings home ----
    GENERAL,
    MONITORING,
    PERMISSIONS,
    NOTIFICATIONS,
    APPEARANCE,
    DATA_BACKUP,
    ABOUT,
    RESET_ALL,

    // ---- General ----
    LANGUAGE,
    STUDY_MODE,
    FOCUS_PASSCODE,

    // ---- Appearance ----
    THEME,
    TEXT_SIZE,
    ICONS,
    CHART,
    FONT,

    // ---- Monitoring ----
    MONITORING_ENABLE,
    BLOCKED_APPS,
    ALLOWED_APPS,
    STRICT_MODE,
    SHORTS_CONTROL,
    BREAK_REMINDER,
    REMINDER_INTERVAL,
    SCHEDULE,

    // ---- Notifications (categories) ----
    NOTIF_REMINDERS,
    NOTIF_LIMIT_ALERTS,
    NOTIF_BLOCK,
    NOTIF_WEEKLY_INSIGHTS,
    NOTIF_SYSTEM,
    NOTIF_SOUND,

    // ---- Permissions ----
    PERM_USAGE_ACCESS,
    PERM_ACCESSIBILITY,
    PERM_OVERLAY,
    PERM_NOTIFICATIONS,
    PERM_BATTERY,
    PERM_STORAGE,
    PERM_SYSTEM_AUDIO,

    // ---- Dashboard drawer ----
    HELP_SUPPORT,
    FAQ,
    CONTACT_SUPPORT,
    REPORT_BUG,
    FEEDBACK,
    SHARE,
    PRIVACY_POLICY,
    TERMS_CONDITIONS,

    // ---- About ShortsCap hub + pages ----
    ABOUT_INFO,
    ABOUT_FEATURES,
    ABOUT_TECHNOLOGIES,
    ABOUT_VERSION_BUILD,
    ABOUT_BUILD,
    ABOUT_COPYRIGHT,
    ABOUT_MISSION,
    ABOUT_VISION,
    ABOUT_PURPOSE,
    ABOUT_INTRO,

    // ---- Features ----
    FEATURE_APP_BLOCKING,
    FEATURE_USAGE_TRACKING,
    FEATURE_FOCUS_MODE,
    FEATURE_WELLBEING,
    FEATURE_SECURE_AUTH,

    // ---- Technologies ----
    TECH_ANDROID,
    TECH_KOTLIN,
    TECH_COMPOSE,
    TECH_PYTHON,
    TECH_AWS,

    // ---- Home dashboard stats ----
    STAT_APPS_USED,
    STAT_RESTRICTED,
    STAT_BLOCKED_SITES,
    STAT_FOCUS_TIME,
    STAT_TODAY_USAGE,

    // ---- Profile ----
    PROFILE_PERSON,
    PROFILE_EMAIL,
    PROFILE_LOCK,
    PROFILE_EDIT,
    PROFILE_CALENDAR,
}
