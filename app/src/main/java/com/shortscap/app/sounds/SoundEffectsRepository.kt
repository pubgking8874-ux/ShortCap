package com.shortscap.app.sounds

import android.content.Context

/**
 * SoundEffectsRepository — the single data seam for the Sound & Effects
 * module. Mirrors NotificationRepository: today [loadSettings] /
 * [saveSettings] persist the configuration locally in SharedPreferences;
 * tomorrow they are replaced by backend APIs behind the exact same
 * [SoundEffectsConfig] shape — no UI changes required.
 */
object SoundEffectsRepository {

    private const val PREFS_NAME = "shortscap_sound_effects"
    private const val KEY_MASTER = "app_sounds_enabled"

    /** Default configuration — used on first install and by Reset All. */
    fun defaults(): SoundEffectsConfig = SoundEffectsConfig()

    /**
     * Loads the persisted configuration. Missing keys fall back to the
     * per-category defaults, so new categories/sounds added in a future
     * release appear with sensible sounds and no migration step.
     */
    fun loadSettings(context: Context): SoundEffectsConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_MASTER, true)
        val selected = SoundEffectCategory.entries.associateWith { category ->
            val stored = prefs.getString(category.name, null)
            AppSound.entries.firstOrNull { it.name == stored }
                ?: SoundEffectsConfig.defaultSelection()[category]
                ?: AppSound.DEFAULT
        }
        return SoundEffectsConfig(appSoundsEnabled = enabled, selected = selected)
    }

    /** Persists the whole configuration locally (single prefs file). */
    fun saveSettings(context: Context, config: SoundEffectsConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_MASTER, config.appSoundsEnabled)
            config.selected.forEach { (category, sound) -> putString(category.name, sound.name) }
            apply()
        }
    }

    /** Resets local storage back to defaults (used by Reset All). */
    fun clearSettings(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    // ---- Future backend seams (placeholders only — not implemented) ----

    /** FUTURE: PUT /settings/sounds — persist the config for cloud sync. */
    suspend fun syncToCloud(config: SoundEffectsConfig) {
        // TODO: backend sync (AWS / Firebase / Python API).
    }
}
