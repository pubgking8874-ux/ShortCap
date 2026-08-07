package com.shortscap.app.model

import androidx.compose.ui.graphics.Color
import com.shortscap.app.icons.IconKey

/** Bottom nav destinations — order preserved: home, activity, web, settings */
enum class ScScreen { HOME, ACTIVITY, WEB, SETTINGS }

/** Mirrors `weekData` in the RN source (Mon..Sun screen-time minutes) */
data class DayUsage(val day: String, val minutes: Int)

val WeekData = listOf(
    DayUsage("Mon", 210), DayUsage("Tue", 185), DayUsage("Wed", 260),
    DayUsage("Thu", 150), DayUsage("Fri", 300), DayUsage("Sat", 340), DayUsage("Sun", 190),
)

/** Mirrors `appUsage` pie-chart data */
data class AppUsageSlice(val name: String, val value: Int, val color: Color)

/** Kind of entity shown with a leading icon — installed apps vs websites */
enum class ScEntityType { APP, WEBSITE }

/**
 * Reusable model for any app or website displayed in the UI (Home recent
 * activity, Web site lists, and future analytics/history screens). Rendered
 * through [com.shortscap.app.components.ScEntityIcon] and
 * [com.shortscap.app.components.ScEntityRow]. The fields map 1:1 to future
 * backend API and Android Accessibility-service data, so replacing the data
 * source requires no UI changes.
 */
data class ScEntity(
    val id: String,
    val title: String,
    val type: ScEntityType,
    val packageName: String? = null,
    val websiteUrl: String? = null,
    val icon: String? = null,
    val fallbackColor: Color,
    val usageTime: String? = null,
    val restrictionStatus: String? = null,
    val timestamp: String? = null,
)

/** Mirrors Web screen site rows, keyed by tab (Blocked / Allowed / Recent) */
data class SiteEntry(val name: String, val url: String, val on: Boolean)

enum class WebTab { BLOCKED, ALLOWED, RECENT }

/**
 * Drawer item — [id] is a stable key for click routing (labels are
 * localized, so matching on label text would break across languages).
 * The icon is requested through the centralized icon system via [iconKey],
 * so the active IconStyle renders it app-wide (no hardcoded ImageVectors).
 */
data class DrawerItem(val id: String, val iconKey: IconKey, val label: String)

/**
 * Full-screen destinations opened from the Dashboard drawer. Each maps to a
 * dedicated sub-screen (Help & Support, legal readers, Feedback).
 * Share App is not here — it launches the native share sheet directly.
 */
enum class DrawerScreen {
    HELP_SUPPORT,
    PRIVACY_POLICY,
    TERMS_CONDITIONS,
    FEEDBACK,
}

/**
 * Local profile data shown and edited on the Profile screen (opened from the
 * Dashboard top bar). Load / Update / Upload Picture will come from backend
 * APIs later — the UI consumes this shape only, so swapping the data source
 * requires no layout changes.
 */
data class ProfileData(
    val fullName: String = "",
    val email: String = "",
    val gender: String? = null,
    val dateOfBirth: String? = null,
    val pictureUri: String? = null,
)

/**
 * Dedicated settings destinations — every row on the Settings home opens its
 * own full screen via [com.shortscap.app.navigation.SettingsNavHost]. Never
 * expands inline, so the back stack behaves like a standard settings app:
 * Settings -> <item> -> Back -> Settings.
 */
enum class SettingsDestination {
    GENERAL, MONITORING, PERMISSIONS, NOTIFICATIONS, APPEARANCE, ABOUT,
}

/** One row on the Settings home — icon + title + chevron only (no subtitles). */
data class SettingsItem(
    val destination: SettingsDestination,
    val iconKey: IconKey,
    val label: String,
)

/**
 * A short-video platform monitored by the app (YouTube Shorts, Instagram
 * Reels, ...). The list is data-driven in [MonitoringSettings.platforms] so
 * new platforms can be added dynamically without UI changes.
 */
data class ShortVideoPlatform(
    val id: String,
    val name: String,
    val enabled: Boolean,
)

val DefaultShortVideoPlatforms = listOf(
    ShortVideoPlatform("youtube_shorts", "YouTube Shorts", true),
    ShortVideoPlatform("instagram_reels", "Instagram Reels", true),
    ShortVideoPlatform("facebook_reels", "Facebook Reels", false),
    ShortVideoPlatform("snapchat_spotlight", "Snapchat Spotlight", false),
)

/**
 * All Monitoring settings shown on the Monitoring screen. Single source of
 * truth for the section toggles, the screen-time limit, break reminder and
 * the platform switches. Today the ViewModel seeds demo values (stats too);
 * tomorrow GET / UPDATE Monitoring Settings APIs swap in behind the same
 * shape — no UI changes required. A SettingsRepository seam is the intended
 * home for those API calls.
 */
data class MonitoringSettings(
    val enabled: Boolean = true,
    val appBlockingEnabled: Boolean = true,
    val screenTimeLimitMinutes: Int = 60,
    val customScreenTimeLimitMinutes: Int = 90,
    val strictModeEnabled: Boolean = false,
    val breakReminderEnabled: Boolean = true,
    val breakReminderIntervalMinutes: Int = 30,
    val platforms: List<ShortVideoPlatform> = DefaultShortVideoPlatforms,
    val schedule: Schedule = Schedule(),
    // Read-only demo stats (placeholder values until Usage Stats / backend).
    val todayUsage: String = "2h 45m",
    val blockedAppsCount: Int = 12,
) {
    /**
     * Future Monitoring Schedule shape — active window (start/end time) and
     * which days monitoring runs. UI page exists; values plug in later.
     */
    data class Schedule(
        val startTimeMinutes: Int = 9 * 60,
        val endTimeMinutes: Int = 22 * 60,
        val weekdaysEnabled: Boolean = true,
        val weekendsEnabled: Boolean = true,
    )
}

/**
 * One page of the Home circular analytics widget.
 * Values are mocked in the ViewModel today; tomorrow they will be fetched
 * from the backend API. The UI only consumes this shape, so switching the
 * data source requires no layout changes.
 */
data class ScCircularMetric(
    val id: String,
    val label: String,
    val value: String,
    val unit: String = "",
    val progress: Float,
)
