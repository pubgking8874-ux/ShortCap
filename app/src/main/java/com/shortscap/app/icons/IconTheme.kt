package com.shortscap.app.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.shortscap.app.study.AllowedAppsIcon
import com.shortscap.app.study.FocusPasscodeIcon

/**
 * IconTheme — the single, centralized icon manager of ShortsCap.
 *
 * Every screen requests icons through [IconKey] — either by passing the key
 * to an icon-key-aware component (e.g. `ScPremiumNavCard(iconKey = …)`) or
 * by reading [LocalIconStyle] and calling [icon] / [tint] / [container]
 * directly. The active [IconStyle] — held in `AppUiState.iconStyle` and
 * provided app-wide through [LocalIconStyle] — decides which ImageVector,
 * tint color and container background are used.
 *
 * Design rules honored by both styles:
 *  - one consistent Material-icon shape language (ShortsCap's own icon set);
 *  - rounded-square container treatment everywhere an icon is tiled;
 *  - colors are theme-agnostic enough to stay readable on Dark and Light;
 *  - no gradients, no tiny details — icons stay crisp at small sizes.
 *
 * Both styles share the same compact visual treatment: a small neutral
 * (dark/subtle charcoal) rounded container with the colored icon inside —
 * the color belongs to the ICON, never a big colored panel.
 *
 * ORIGINAL tints every icon with the ShortsCap accent (blue/black look).
 * VIBRANT switches to the per-category chromatic palette
 * ([VibrantPalette]): each category's icon gets its own tasteful color
 * inside the same neutral container.
 *
 * Future styles plug in with one enum entry + branches here — screens
 * request keys only and never change.
 */
object IconTheme {

    /** Resolves the icon vector for [key] under [style]. */
    fun icon(style: IconStyle, key: IconKey): ImageVector = when (style) {
        IconStyle.ORIGINAL -> originalIcon(key)
        IconStyle.VIBRANT -> vibrantIcon(key)
    }

    /**
     * Resolves the icon tint for [key] under [style]. [defaultTint] is the
     * caller's current palette color and is used as-is by ORIGINAL; VIBRANT
     * replaces it with the category color.
     */
    fun tint(style: IconStyle, key: IconKey, defaultTint: Color): Color =
        if (style == IconStyle.VIBRANT) VibrantPalette.color(key) else defaultTint

    // Note: there is intentionally no container() resolver. Both styles
    // share ONE compact, neutral rounded container (the theme's CardHover
    // charcoal surface) so the category color always belongs to the ICON,
    // never a big colored panel. A future style that needs its own container
    // treatment can add a resolver here without touching screens.

    // ---- ORIGINAL — the classic ShortsCap blue/black icons ----

    private fun originalIcon(key: IconKey): ImageVector = when (key) {
        // Bottom navigation
        IconKey.HOME -> Icons.Filled.Home
        IconKey.ACTIVITY -> Icons.Filled.Schedule
        // Rank — trophy (achievement / leaderboard / winning). Distinct from
        // Activity's chart-style icon (Schedule/BarChart) so the two tabs stay
        // visually separate in the bottom navigation.
        IconKey.RANK -> Icons.Filled.EmojiEvents
        IconKey.WEB -> Icons.Filled.Language
        IconKey.SETTINGS -> Icons.Filled.Settings

        // Web (analytics + website rules)
        IconKey.WEB_ANALYTICS -> Icons.Filled.DonutLarge
        IconKey.WEB_BLOCKED -> Icons.Filled.Block
        IconKey.WEB_ALLOWED -> Icons.Filled.CheckCircle

        // Settings home
        IconKey.GENERAL -> Icons.Filled.Tune
        IconKey.MONITORING -> Icons.Filled.Visibility
        IconKey.PERMISSIONS -> Icons.Filled.VerifiedUser
        IconKey.NOTIFICATIONS -> Icons.Filled.Notifications
        // Sound & Effects — audio waves (app sounds / effects panel).
        IconKey.SOUND_EFFECTS -> Icons.Filled.GraphicEq
        IconKey.APPEARANCE -> Icons.Filled.Palette
        IconKey.DATA_BACKUP -> Icons.Filled.Storage
        IconKey.ABOUT -> Icons.Filled.Info
        IconKey.RESET_ALL -> Icons.Filled.RestartAlt

        // General
        IconKey.LANGUAGE -> Icons.Filled.Language
        IconKey.STUDY_MODE -> Icons.Filled.School
        // Exit Passcode — study/book + outward exit arrow ("use a passcode to
        // EXIT Study Mode"), NOT a padlock: the feature gates leaving a
        // session, it does not lock the device.
        IconKey.FOCUS_PASSCODE -> FocusPasscodeIcon

        // Appearance
        IconKey.THEME -> Icons.Filled.Palette
        IconKey.TEXT_SIZE -> Icons.Filled.FormatSize
        IconKey.ICONS -> Icons.Filled.AutoAwesome
        IconKey.CHART -> Icons.Filled.BarChart
        IconKey.FONT -> Icons.Filled.TextFields
        // Shorts HUD — a small floating window over other apps (the HUD overlay).
        IconKey.SHORTS_HUD -> Icons.Filled.PictureInPicture

        // Monitoring
        IconKey.MONITORING_ENABLE -> Icons.Filled.Insights
        // Usage monitoring / activity tracking — Sound & Effects Monitoring
        // section heading (activity line, NOT the eye icon of the Settings
        // Monitoring row).
        IconKey.MONITORING_ANALYTICS -> Icons.Filled.Timeline
        IconKey.BLOCKED_APPS -> Icons.Filled.DoNotDisturbOn
        // Allow Apps & Websites — app window + checkmark (allowed/permitted),
        // not a generic list icon.
        IconKey.ALLOWED_APPS -> AllowedAppsIcon
        IconKey.STRICT_MODE -> Icons.Filled.GppMaybe
        // Short Control — SmartDisplay stays the section icon (Settings →
        // Short Control row + hub entry). The sub-pages get their own icons:
        IconKey.SHORTS_CONTROL -> Icons.Filled.SmartDisplay
        // Short Applications — the grid of enabled short-form platforms.
        IconKey.SHORTS_APPLICATIONS -> Icons.Filled.Apps
        // Shorts Limit — the 24-hour count/timer limit.
        IconKey.SHORTS_LIMIT -> Icons.Filled.Timer
        // Shorts Insights — read-only usage summaries.
        IconKey.SHORTS_INSIGHTS -> Icons.Filled.Insights
        IconKey.BREAK_REMINDER -> Icons.Filled.SelfImprovement
        IconKey.REMINDER_INTERVAL -> Icons.Filled.Alarm
        IconKey.SCHEDULE -> Icons.Filled.CalendarMonth

        // Notifications
        IconKey.NOTIF_REMINDERS -> Icons.Filled.NotificationsActive
        IconKey.NOTIF_LIMIT_ALERTS -> Icons.Filled.Speed
        IconKey.NOTIF_BLOCK -> Icons.Filled.Block
        IconKey.NOTIF_WEEKLY_INSIGHTS -> Icons.Filled.Insights
        IconKey.NOTIF_SYSTEM -> Icons.Filled.AdminPanelSettings
        IconKey.NOTIF_SOUND -> Icons.Filled.VolumeUp

        // Permissions
        IconKey.PERM_USAGE_ACCESS -> Icons.Filled.DonutLarge
        IconKey.PERM_ACCESSIBILITY -> Icons.Filled.AccessibilityNew
        IconKey.PERM_OVERLAY -> Icons.Filled.Layers
        IconKey.PERM_NOTIFICATIONS -> Icons.Filled.NotificationsActive
        IconKey.PERM_BATTERY -> Icons.Filled.BatteryChargingFull
        IconKey.PERM_STORAGE -> Icons.Filled.PhotoLibrary
        IconKey.PERM_SYSTEM_AUDIO -> Icons.Filled.VolumeUp

        // Dashboard drawer
        IconKey.HELP_SUPPORT -> Icons.Filled.HelpOutline
        IconKey.FAQ -> Icons.Filled.HelpOutline
        IconKey.CONTACT_SUPPORT -> Icons.Filled.SupportAgent
        IconKey.REPORT_BUG -> Icons.Filled.BugReport
        IconKey.FEEDBACK -> Icons.Filled.Message
        IconKey.SHARE -> Icons.Filled.Share
        IconKey.PRIVACY_POLICY -> Icons.Filled.Description
        IconKey.TERMS_CONDITIONS -> Icons.Filled.Gavel

        // About ShortsCap hub + pages
        IconKey.ABOUT_INFO -> Icons.Filled.Info
        IconKey.ABOUT_FEATURES -> Icons.Filled.Star
        IconKey.ABOUT_TECHNOLOGIES -> Icons.Filled.Tune
        IconKey.ABOUT_VERSION_BUILD -> Icons.Filled.Build
        IconKey.ABOUT_BUILD -> Icons.Filled.Build
        IconKey.ABOUT_COPYRIGHT -> Icons.Filled.Copyright
        IconKey.ABOUT_MISSION -> Icons.Filled.Flag
        IconKey.ABOUT_VISION -> Icons.Filled.Visibility
        IconKey.ABOUT_PURPOSE -> Icons.Filled.TrackChanges
        IconKey.ABOUT_INTRO -> Icons.Filled.Info

        // Features
        IconKey.FEATURE_APP_BLOCKING -> Icons.Filled.Block
        IconKey.FEATURE_USAGE_TRACKING -> Icons.Filled.BarChart
        IconKey.FEATURE_FOCUS_MODE -> Icons.Filled.CenterFocusStrong
        IconKey.FEATURE_WELLBEING -> Icons.Filled.Spa
        IconKey.FEATURE_SECURE_AUTH -> Icons.Filled.Lock

        // Technologies
        IconKey.TECH_ANDROID -> Icons.Filled.Android
        IconKey.TECH_KOTLIN -> Icons.Filled.Code
        IconKey.TECH_COMPOSE -> Icons.Filled.Widgets
        IconKey.TECH_PYTHON -> Icons.Filled.Storage
        IconKey.TECH_AWS -> Icons.Filled.Cloud

        // Home dashboard stats
        IconKey.STAT_APPS_USED -> Icons.Filled.Smartphone
        IconKey.STAT_RESTRICTED -> Icons.Filled.Block
        IconKey.STAT_BLOCKED_SITES -> Icons.Filled.Language
        IconKey.STAT_FOCUS_TIME -> Icons.Filled.Timer
        IconKey.STAT_TODAY_USAGE -> Icons.Filled.Schedule

        // Profile
        IconKey.PROFILE_PERSON -> Icons.Filled.Person
        IconKey.PROFILE_EMAIL -> Icons.Filled.Email
        IconKey.PROFILE_LOCK -> Icons.Filled.Lock
        IconKey.PROFILE_EDIT -> Icons.Filled.Edit
        IconKey.PROFILE_CALENDAR -> Icons.Filled.CalendarMonth
    }

    // ---- VIBRANT — same shape language, per-category icon swaps ----
    // Only the icons that benefit from a more distinctive shape differ from
    // ORIGINAL; everything else keeps the recognizable ShortsCap icon.

    private fun vibrantIcon(key: IconKey): ImageVector = when (key) {
        IconKey.ICONS -> Icons.Filled.Brush
        IconKey.CHART -> Icons.Filled.PieChart
        IconKey.FONT -> Icons.Filled.FontDownload
        IconKey.PERMISSIONS -> Icons.Filled.Security
        IconKey.DATA_BACKUP -> Icons.Filled.Backup
        IconKey.LANGUAGE -> Icons.Filled.Translate
        IconKey.PRIVACY_POLICY -> Icons.Filled.Policy
        IconKey.FAQ -> Icons.Filled.QuestionAnswer
        IconKey.ACTIVITY -> Icons.Filled.BarChart
        IconKey.WEB -> Icons.Filled.Public
        IconKey.WEB_ANALYTICS -> Icons.Filled.Insights
        IconKey.THEME -> Icons.Filled.DarkMode
        IconKey.ABOUT_FEATURES -> Icons.Filled.EmojiEvents
        else -> originalIcon(key)
    }
}

/**
 * Per-category chromatic palette used by [IconStyle.VIBRANT] — ShortsCap's
 * own original color language (inspired by the concept of colorful
 * category icons; not derived from any third-party asset). Each category
 * gets a distinct, recognizable color that stays readable on Dark and
 * Light surfaces. Shades are deliberately tasteful (not neon) so the
 * colorful icons stay premium against the neutral dark containers.
 */
private object VibrantPalette {

    private val blue = Color(0xFF3B82F6)
    private val cyan = Color(0xFF06B6D4)
    private val teal = Color(0xFF14B8A6)
    private val green = Color(0xFF22C55E)
    private val lime = Color(0xFF84CC16)
    private val amber = Color(0xFFF59E0B)
    private val orange = Color(0xFFF97316)
    private val red = Color(0xFFEF4444)
    private val pink = Color(0xFFEC4899)
    private val purple = Color(0xFF8B5CF6)
    private val violet = Color(0xFFA855F7)
    private val indigo = Color(0xFF6366F1)
    private val slate = Color(0xFF64748B)

    fun color(key: IconKey): Color = when (key) {
        // Bottom navigation
        IconKey.HOME -> blue
        IconKey.ACTIVITY -> cyan
        IconKey.RANK -> indigo
        IconKey.WEB -> teal
        IconKey.SETTINGS -> purple

        // Web (analytics + website rules)
        IconKey.WEB_ANALYTICS -> cyan
        IconKey.WEB_BLOCKED -> red
        IconKey.WEB_ALLOWED -> green

        // Settings home
        IconKey.GENERAL -> purple
        IconKey.MONITORING -> blue
        IconKey.PERMISSIONS -> green
        IconKey.NOTIFICATIONS -> pink
        IconKey.SOUND_EFFECTS -> teal
        IconKey.APPEARANCE -> violet
        IconKey.DATA_BACKUP -> cyan
        IconKey.ABOUT -> indigo
        IconKey.RESET_ALL -> red

        // General
        IconKey.LANGUAGE -> violet
        IconKey.STUDY_MODE -> indigo
        IconKey.FOCUS_PASSCODE -> amber

        // Appearance
        IconKey.THEME -> violet
        IconKey.TEXT_SIZE -> teal
        IconKey.ICONS -> indigo
        IconKey.CHART -> cyan
        IconKey.FONT -> violet
        IconKey.SHORTS_HUD -> teal

        // Monitoring
        IconKey.MONITORING_ENABLE -> blue
        IconKey.MONITORING_ANALYTICS -> blue
        IconKey.BLOCKED_APPS -> orange
        IconKey.ALLOWED_APPS -> green
        IconKey.STRICT_MODE -> red
        IconKey.SHORTS_CONTROL -> cyan
        IconKey.SHORTS_APPLICATIONS -> indigo
        IconKey.SHORTS_LIMIT -> amber
        IconKey.SHORTS_INSIGHTS -> purple
        IconKey.BREAK_REMINDER -> teal
        IconKey.REMINDER_INTERVAL -> purple
        IconKey.SCHEDULE -> indigo

        // Notifications
        IconKey.NOTIF_REMINDERS -> pink
        IconKey.NOTIF_LIMIT_ALERTS -> amber
        IconKey.NOTIF_BLOCK -> red
        IconKey.NOTIF_WEEKLY_INSIGHTS -> purple
        IconKey.NOTIF_SYSTEM -> slate
        IconKey.NOTIF_SOUND -> orange

        // Permissions
        IconKey.PERM_USAGE_ACCESS -> green
        IconKey.PERM_ACCESSIBILITY -> blue
        IconKey.PERM_OVERLAY -> purple
        IconKey.PERM_NOTIFICATIONS -> pink
        IconKey.PERM_BATTERY -> lime
        IconKey.PERM_STORAGE -> cyan
        IconKey.PERM_SYSTEM_AUDIO -> orange

        // Dashboard drawer
        IconKey.HELP_SUPPORT -> blue
        IconKey.FAQ -> blue
        IconKey.CONTACT_SUPPORT -> cyan
        IconKey.REPORT_BUG -> red
        IconKey.FEEDBACK -> orange
        IconKey.SHARE -> cyan
        IconKey.PRIVACY_POLICY -> slate
        IconKey.TERMS_CONDITIONS -> amber

        // About ShortsCap hub + pages
        IconKey.ABOUT_INFO -> indigo
        IconKey.ABOUT_FEATURES -> amber
        IconKey.ABOUT_TECHNOLOGIES -> cyan
        IconKey.ABOUT_VERSION_BUILD -> blue
        IconKey.ABOUT_BUILD -> indigo
        IconKey.ABOUT_COPYRIGHT -> purple
        IconKey.ABOUT_MISSION -> orange
        IconKey.ABOUT_VISION -> cyan
        IconKey.ABOUT_PURPOSE -> purple
        IconKey.ABOUT_INTRO -> indigo

        // Features
        IconKey.FEATURE_APP_BLOCKING -> red
        IconKey.FEATURE_USAGE_TRACKING -> blue
        IconKey.FEATURE_FOCUS_MODE -> amber
        IconKey.FEATURE_WELLBEING -> green
        IconKey.FEATURE_SECURE_AUTH -> purple

        // Technologies
        IconKey.TECH_ANDROID -> green
        IconKey.TECH_KOTLIN -> orange
        IconKey.TECH_COMPOSE -> blue
        IconKey.TECH_PYTHON -> amber
        IconKey.TECH_AWS -> orange

        // Home dashboard stats
        IconKey.STAT_APPS_USED -> blue
        IconKey.STAT_RESTRICTED -> red
        IconKey.STAT_BLOCKED_SITES -> amber
        IconKey.STAT_FOCUS_TIME -> green
        IconKey.STAT_TODAY_USAGE -> blue

        // Profile
        IconKey.PROFILE_PERSON -> blue
        IconKey.PROFILE_EMAIL -> cyan
        IconKey.PROFILE_LOCK -> amber
        IconKey.PROFILE_EDIT -> purple
        IconKey.PROFILE_CALENDAR -> pink
    }
}

/** Active icon style for the current composition — provided app-wide in
 *  ShortsCapApp (mirrors how LocalScColors provides the theme palette). */
val LocalIconStyle = staticCompositionLocalOf { IconStyle.ORIGINAL }
