package com.shortscap.app.permissions

import android.content.Context

/**
 * Persists the one-time first-launch Permission Setup completion state.
 *
 * ONLY a single boolean is stored (the necessary setup state — nothing more):
 * once the user has completed the Permission Setup gate (all required
 * permissions granted → Continue), the gate never shows again — across app
 * restarts, force-stop and reboot.
 *
 * Later revocations are deliberately NOT re-gated here: if the user revokes
 * a permission afterwards, Settings → Permissions reflects the real Android
 * OS state (the single source of truth) without re-locking the app behind
 * the setup flow or breaking unrelated engines.
 *
 * Mirrors the [com.shortscap.app.i18n.LanguagePreferenceStore] persistence
 * convention (same prefs file, separate key).
 */
class FirstLaunchSetupStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once the user has completed the first-launch Permission Setup. */
    fun isCompleted(): Boolean = prefs.getBoolean(KEY_SETUP_COMPLETED, false)

    /** Marks the first-launch Permission Setup as completed (called on Continue). */
    fun markCompleted() {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, true).apply()
    }

    /** Test-only: clears the completed flag (never called from production UI). */
    fun resetForTesting() {
        prefs.edit().remove(KEY_SETUP_COMPLETED).apply()
    }

    private companion object {
        const val PREFS_NAME = "shorts_cap_settings"
        const val KEY_SETUP_COMPLETED = "first_launch_setup_completed"
    }
}
