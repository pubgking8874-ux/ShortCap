package com.shortscap.app.icons

import android.content.Context

/**
 * IconRepository — the single data seam for the Icon System module.
 *
 * Today [loadIconStyle] / [saveIconStyle] persist the user's selected icon
 * style locally in SharedPreferences (matching the ThemePreferenceStore /
 * AppearanceRepository pattern). Tomorrow the same functions are replaced
 * by backend API calls (or a local Room database) behind the exact same
 * shapes — no UI changes required.
 *
 * Future backend may store: `user_id`, `selected_icon_style`, `updated_at`.
 * The cloud-sync / analytics placeholders are intentionally documented but
 * not implemented (backend-ready only) — the preference also survives
 * logout/login because it is a local, device-level application preference.
 */
object IconRepository {

    private const val PREFS_NAME = "shortscap_icons"
    private const val KEY_ICON_STYLE = "icon_style"

    /** Loads the persisted icon style (defaults to [IconStyle.DEFAULT]). */
    fun loadIconStyle(context: Context): IconStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return IconStyle.entries.firstOrNull { it.name == prefs.getString(KEY_ICON_STYLE, null) }
            ?: IconStyle.DEFAULT
    }

    /** Persists the icon style locally. */
    fun saveIconStyle(context: Context, style: IconStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ICON_STYLE, style.name)
            .apply()
    }

    /** Resets local storage back to defaults (used by Reset All Settings). */
    fun clearSettings(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    // ---- Future backend seams (placeholders only — not implemented) ----

    /** FUTURE: PUT /settings/appearance/icon-style — sync the preference to
     *  the user's cloud profile (`user_id`, `selected_icon_style`, `updated_at`). */
    suspend fun syncIconStyleToCloud(style: IconStyle) {
        // TODO: backend sync (AWS / Firebase / Python API).
    }

    /** FUTURE: analytics event fired when the icon style changes. */
    fun trackIconStyleAnalytics(style: IconStyle) {
        // TODO: analytics SDK call.
    }
}
