package com.shortscap.app.charts

import androidx.compose.ui.graphics.Color

/**
 * Global chart-style preference — the single source of truth for how every
 * supported analytics chart in ShortsCap renders.
 *
 * ONE preference for the whole app (Activity, Web Usage Analytics, weekly /
 * monthly reports and any future analytics screen):
 *  - [BAR]      — distributions/time series drawn as bars;
 *  - [CIRCULAR] — distributions drawn as circular/donut charts;
 *  - [GRAPH]    — time series drawn as a professional line/area graph
 *                 (distributions fall back to the donut, since a line does
 *                 not represent a share).
 *
 * This is a presentation preference ONLY. It lives in the user-preferences
 * layer (persisted by AppearanceRepository, reset by SettingsManager) and
 * NEVER touches the underlying usage data, which flows separately from the
 * data/analytics layer into the renderer unchanged. A future backend stores
 * it as a user preference (e.g. `UserPreferences.chartStyle`), never inside
 * ActivityUsage / WebUsage records.
 */
enum class ChartStyle {
    BAR,
    CIRCULAR,
    GRAPH;

    companion object {
        /** Default — the classic circular/donut look (first install / Reset All). */
        val DEFAULT: ChartStyle = CIRCULAR
    }
}

/**
 * One slice of a distribution chart — pure data, no rendering.
 *
 * [value] is the raw share (minutes, count or percentage). The renderer only
 * scales it for drawing; it never recalculates, filters or duplicates the
 * data, so switching chart styles cannot change the displayed values.
 */
data class ChartSlice(
    val label: String,
    val value: Float,
    val color: Color,
)
