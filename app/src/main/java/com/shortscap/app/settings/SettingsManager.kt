package com.shortscap.app.settings

import android.content.Context
import com.shortscap.app.appearance.AppearanceRepository
import com.shortscap.app.appearance.FontMode
import com.shortscap.app.appearance.TextSizeMode
import com.shortscap.app.charts.ChartStyle
import com.shortscap.app.i18n.AppLanguage
import com.shortscap.app.i18n.LanguagePreferenceStore
import com.shortscap.app.icons.IconRepository
import com.shortscap.app.icons.IconStyle
import com.shortscap.app.model.MonitoringSettings
import com.shortscap.app.notifications.NotificationRepository
import com.shortscap.app.notifications.NotificationSetting
import com.shortscap.app.theme.ThemeMode
import com.shortscap.app.theme.ThemePreferenceStore

/**
 * SettingsManager — the single, centralized authority for what counts as a
 * resettable application setting and what its default value is.
 *
 * Reset All Settings routes through this manager so that EVERY configurable
 * preference (today: theme, text size, icon style, language, monitoring and
 * notifications) is restored in one place. Any future setting is registered
 * here exactly once (a default accessor + one line in [restoreDefaults]) and
 * is then included automatically in every reset — no other code changes.
 *
 * Scope: application settings ONLY. Account, profile, login session,
 * authentication tokens, monitoring history, backend and cloud data are never
 * touched here.
 *
 * Backend-ready: local persistence is restored here; the future
 * cloud/backend reset is a documented seam ([resetCloudSettings]).
 */
object SettingsManager {

    // ---- Defaults registry — one accessor per resettable setting. ---- //

    /** Theme default after reset — System Default (follows the device). */
    fun defaultThemeMode(): ThemeMode = ThemeMode.SYSTEM

    /** Text Size default after reset — Medium (1.0×). */
    fun defaultTextSizeMode(): TextSizeMode = TextSizeMode.MEDIUM

    /** Chart Style default after reset — Circular Chart (donut). */
    fun defaultChartStyle(): ChartStyle = ChartStyle.DEFAULT

    /** Font default after reset — Inter (the original ShortsCap family). */
    fun defaultFontMode(): FontMode = FontMode.DEFAULT

    /** Icon Style default after reset — ShortsCap Original. */
    fun defaultIconStyle(): IconStyle = IconStyle.ORIGINAL

    /** Language default after reset — English. */
    fun defaultLanguage(): AppLanguage = AppLanguage.ENGLISH

    /** Monitoring preferences default (all toggles / limits at seed state). */
    fun defaultMonitoring(): MonitoringSettings = MonitoringSettings()

    /** Notification preferences default (every option at its seeded state). */
    fun defaultNotificationSettings(): List<NotificationSetting> =
        NotificationRepository.seedSettings()

    /**
     * Restores every local preference store to its default value and persists
     * those defaults, so the app stays consistent across restarts. Add a new
     * resettable store here (one line) to include it in future resets.
     */
    fun restoreDefaults(context: Context) {
        ThemePreferenceStore(context).saveThemeMode(defaultThemeMode())
        LanguagePreferenceStore(context).saveLanguage(defaultLanguage())
        AppearanceRepository.saveTextSizeMode(context, defaultTextSizeMode())
        AppearanceRepository.saveChartStyle(context, defaultChartStyle())
        AppearanceRepository.saveFontMode(context, defaultFontMode())
        IconRepository.saveIconStyle(context, defaultIconStyle())
        NotificationRepository.saveSettings(context, defaultNotificationSettings())
    }

    /** FUTURE: restore backend / cloud preferences through the API. */
    suspend fun resetCloudSettings() {
        // TODO: POST /settings/reset — clears backend + cloud preferences.
    }
}
