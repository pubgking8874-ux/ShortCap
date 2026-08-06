package com.shortscap.app.i18n

/**
 * LanguageRepository — backend seam for syncing the selected language with
 * the user's cloud profile, mirroring the ProfileRepository / SettingsRepository
 * pattern. Not implemented today: the language is stored locally via
 * [LanguagePreferenceStore] and the UI never calls these directly. When AWS /
 * Firebase / the Python backend connects, swap the bodies below — no UI
 * changes required.
 */
object LanguageRepository {

    /** PUT the language on the user's cloud profile. */
    suspend fun syncLanguageToCloud(language: AppLanguage) {
        // TODO: POST /profile/language { code: language.code }
    }

    /** Pulls the cloud language preference (e.g. on login), null if not set. */
    suspend fun loadLanguageFromCloud(): AppLanguage? = null
}
