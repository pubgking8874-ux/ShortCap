package com.shortscap.app.i18n

import android.content.Context

/**
 * Persists the selected app language locally (SharedPreferences), mirroring
 * [com.shortscap.app.theme.ThemePreferenceStore]. Read synchronously at
 * startup so the correct language renders from the very first Dashboard
 * frame after login.
 */
class LanguagePreferenceStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadLanguage(): AppLanguage =
        AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, null) ?: "")

    fun saveLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    private companion object {
        const val PREFS_NAME = "shorts_cap_settings"
        const val KEY_LANGUAGE = "app_language"
    }
}
