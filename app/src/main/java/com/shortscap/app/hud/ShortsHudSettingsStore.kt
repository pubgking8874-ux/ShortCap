package com.shortscap.app.hud

import android.content.Context

/**
 * Persists the Shorts HUD's local settings using the app's existing
 * SharedPreferences architecture (same pattern as ThemePreferenceStore /
 * LanguagePreferenceStore — deliberately no new storage technology):
 *
 *  - enabled/disabled state
 *  - appearance mode (ShortsCap / Brain / Live Counter)
 *  - the user's chosen position as NORMALIZED (0..1) X/Y fractions, so the
 *    saved position survives screen-size / orientation changes safely
 *  - the daily Shorts limit used for the "count / limit" display. Defaults
 *    to the product default of 200 shorts/day; when the Phase 16 settings
 *    sync provides the backend `shorts_settings.daily_limit_count`, that
 *    value feeds in here (no invented limit — the configured limit wins).
 *
 * The HUD itself never talks to the backend; it reads this local state only.
 */
class ShortsHudSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether the HUD overlay is enabled at all. */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** The selected appearance mode (default: ShortsCap). */
    fun appearance(): ShortsHudAppearance =
        ShortsHudAppearance.fromName(prefs.getString(KEY_APPEARANCE, null))

    fun setAppearance(appearance: ShortsHudAppearance) {
        prefs.edit().putString(KEY_APPEARANCE, appearance.name).apply()
    }

    /** Normalized X position (0..1 fraction of screen width). Default top-center. */
    fun positionX(): Float = prefs.getFloat(KEY_POS_X, DEFAULT_POS_X)

    /** Normalized Y position (0..1 fraction of screen height). Default top-center. */
    fun positionY(): Float = prefs.getFloat(KEY_POS_Y, DEFAULT_POS_Y)

    fun setPosition(normalizedX: Float, normalizedY: Float) {
        prefs.edit()
            .putFloat(KEY_POS_X, normalizedX.coerceIn(0f, 1f))
            .putFloat(KEY_POS_Y, normalizedY.coerceIn(0f, 1f))
            .apply()
    }

    /**
     * Daily Shorts count limit shown as the HUD denominator. Defaults to the
     * product default (200 shorts/day, matching the "4 / 200" HUD example);
     * the backend `shorts_settings.daily_limit_count` value replaces it when
     * the settings sync provides one.
     */
    fun dailyLimit(): Int = prefs.getInt(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)

    fun setDailyLimit(limit: Int) {
        prefs.edit().putInt(KEY_DAILY_LIMIT, limit.coerceAtLeast(1)).apply()
    }

    companion object {
        private const val PREFS_NAME = "shorts_hud_settings"
        private const val KEY_ENABLED = "hud_enabled"
        private const val KEY_APPEARANCE = "hud_appearance"
        private const val KEY_POS_X = "hud_pos_x"
        private const val KEY_POS_Y = "hud_pos_y"
        private const val KEY_DAILY_LIMIT = "hud_daily_limit"

        /** Default position: top-center, just clear of the status bar. */
        const val DEFAULT_POS_X = 0.5f
        const val DEFAULT_POS_Y = 0.15f

        const val DEFAULT_DAILY_LIMIT = 200
    }
}
