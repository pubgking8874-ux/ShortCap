package com.shortscap.app.notifications

import android.content.Context

/**
 * NotificationRepository — the single data seam for the Notifications module.
 *
 * Today [loadSettings] / [saveSettings] persist every option's on/off state
 * locally in SharedPreferences (matching the ThemePreferenceStore /
 * LanguagePreferenceStore pattern). Tomorrow the same functions are replaced
 * by backend API calls (or a local Room database) behind the exact same
 * [NotificationSetting] shape — no UI changes required.
 *
 * The future cloud-sync / analytics placeholders are intentionally documented
 * but not implemented (backend-ready only).
 */
object NotificationRepository {

    private const val PREFS_NAME = "shortscap_notifications"

    /** Initial state for all notification options — used before first load. */
    fun seedSettings(): List<NotificationSetting> = NotificationSettingId.entries.map { id ->
        NotificationSetting(id = id, enabled = defaultEnabled(id))
    }

    /**
     * Loads every option from local storage. Missing keys fall back to the
     * default state, so new options added in a future release appear enabled
     * with no migration step.
     */
    fun loadSettings(context: Context): List<NotificationSetting> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NotificationSettingId.entries.map { id ->
            NotificationSetting(
                id = id,
                enabled = prefs.getBoolean(id.name, defaultEnabled(id)),
            )
        }
    }

    /** Persists every option's current state locally (single prefs file). */
    fun saveSettings(context: Context, settings: List<NotificationSetting>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            settings.forEach { putBoolean(it.id.name, it.enabled) }
            apply()
        }
    }

    /** Resets local storage back to the seed state (used by Reset All). */
    fun clearSettings(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    // ---- Future backend seams (placeholders only — not implemented) ----

    /** FUTURE: POST /notifications/settings — persist states for cloud sync. */
    suspend fun syncSettingsToCloud(settings: List<NotificationSetting>) {
        // TODO: backend sync (AWS / Firebase / Python API).
    }

    /** FUTURE: analytics event fired when a notification option changes. */
    fun trackNotificationAnalytics(settingId: NotificationSettingId, enabled: Boolean) {
        // TODO: analytics SDK call.
    }

    // Defaults: the three usage-limit alerts and weekly insights are opt-in
    // (sensitive to noise); everything else starts enabled.
    private fun defaultEnabled(id: NotificationSettingId): Boolean = when (id) {
        NotificationSettingId.LIMIT_50_PERCENT,
        NotificationSettingId.LIMIT_80_PERCENT,
        NotificationSettingId.WEEKLY_PROGRESS_REPORT,
        NotificationSettingId.WEEKLY_ACHIEVEMENT,
        -> false
        else -> true
    }
}
