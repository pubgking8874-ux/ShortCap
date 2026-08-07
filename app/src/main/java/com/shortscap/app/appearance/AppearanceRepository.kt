package com.shortscap.app.appearance

import android.content.Context
import com.shortscap.app.charts.ChartStyle

/**
 * AppearanceRepository — the single data seam for the Appearance module.
 *
 * Today [loadTextSizeMode] / [saveTextSizeMode] and [loadChartStyle] /
 * [saveChartStyle] persist the user's global typography-scale and chart-style
 * choices locally in SharedPreferences (matching the ThemePreferenceStore
 * pattern). Tomorrow the same functions are replaced by backend API calls (or
 * a local Room database) behind the exact same shapes — no UI changes
 * required.
 *
 * The future cloud-sync / analytics placeholders are intentionally documented
 * but not implemented (backend-ready only).
 */
object AppearanceRepository {

    private const val PREFS_NAME = "shortscap_appearance"
    private const val KEY_TEXT_SIZE = "text_size_mode"
    private const val KEY_CHART_STYLE = "chart_style"

    /** Loads the persisted text-size mode (defaults to [TextSizeMode.MEDIUM]). */
    fun loadTextSizeMode(context: Context): TextSizeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TextSizeMode.entries.firstOrNull { it.name == prefs.getString(KEY_TEXT_SIZE, null) }
            ?: TextSizeMode.MEDIUM
    }

    /** Persists the text-size mode locally. */
    fun saveTextSizeMode(context: Context, mode: TextSizeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEXT_SIZE, mode.name)
            .apply()
    }

    /** Loads the persisted global chart style (defaults to [ChartStyle.DEFAULT]). */
    fun loadChartStyle(context: Context): ChartStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ChartStyle.entries.firstOrNull { it.name == prefs.getString(KEY_CHART_STYLE, null) }
            ?: ChartStyle.DEFAULT
    }

    /** Persists the global chart style locally (presentation preference only). */
    fun saveChartStyle(context: Context, style: ChartStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CHART_STYLE, style.name)
            .apply()
    }

    /** Resets local storage back to defaults (used by Reset All). */
    fun clearSettings(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    // ---- Future backend seams (placeholders only — not implemented) ----

    /** FUTURE: POST /settings/appearance — persist preferences for cloud sync. */
    suspend fun syncAppearanceToCloud(textSizeMode: TextSizeMode) {
        // TODO: backend sync (AWS / Firebase / Python API).
    }

    /** FUTURE: analytics event fired when an appearance preference changes. */
    fun trackAppearanceAnalytics(preference: String, value: Any) {
        // TODO: analytics SDK call.
    }
}
