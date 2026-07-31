package com.shortscap.app.theme

import android.content.Context

/**
 * Persists the user's Theme selection (Dark / Light / System Default).
 * SharedPreferences is used deliberately: it is a single enum value, read
 * synchronously at startup so the correct theme renders on the very first
 * frame, and it needs no extra dependencies.
 */
class ThemePreferenceStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadThemeMode(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME_MODE, null) }
            ?: ThemeMode.DARK

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "shorts_cap_settings"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
