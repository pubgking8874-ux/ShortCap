package com.shortscap.app.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

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

/** Mirrors Home screen "Recent Activity" rows */
data class RecentActivityItem(val name: String, val time: String, val whenText: String, val color: Color)

/** Mirrors Web screen site rows, keyed by tab (Blocked / Allowed / Recent) */
data class SiteEntry(val name: String, val url: String, val on: Boolean)

enum class WebTab { BLOCKED, ALLOWED, RECENT }

/** Mirrors drawer item list */
data class DrawerItem(val icon: ImageVector, val label: String)

/** Mirrors profile popover item list */
data class ProfileMenuItem(val icon: ImageVector, val label: String, val isDanger: Boolean = false)

/** Mirrors Settings categories list */
data class SettingsCategory(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val sub: String,
)

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
