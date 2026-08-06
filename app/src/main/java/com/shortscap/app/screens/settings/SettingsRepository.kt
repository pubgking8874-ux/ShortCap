package com.shortscap.app.screens.settings

import com.shortscap.app.model.MonitoringSettings

/**
 * SettingsRepository — backend seam for all Settings data, mirroring the
 * ProfileRepository pattern. Today the ViewModel holds local state and the UI
 * never calls these directly; each function below documents the future API it
 * will call. Swapping the data source (Python/Firebase/AWS backend, or a
 * local Room database) requires **no UI changes**.
 */
object SettingsRepository {

    /** GET Monitoring Settings — load the full [MonitoringSettings] model. */
    suspend fun getMonitoringSettings(): MonitoringSettings = MonitoringSettings()

    /** UPDATE Monitoring Settings — persist the whole model (cloud sync). */
    suspend fun updateMonitoringSettings(settings: MonitoringSettings) {
        // TODO: POST /settings/monitoring — Firebase / AWS backend or Room.
    }

    /** GET Blocked Apps — identifiers of the apps the user has blocked. */
    suspend fun getBlockedApps(): List<String> = emptyList()

    /** UPDATE Blocked Apps — persist the blocked-app list. */
    suspend fun updateBlockedApps(blockedApps: List<String>) {
        // TODO: POST /settings/blocked-apps.
    }

    /** GET Allowed Apps — apps that bypass restrictions. */
    suspend fun getAllowedApps(): List<String> = emptyList()

    /** UPDATE Allowed Apps — persist the allowed-app list. */
    suspend fun updateAllowedApps(allowedApps: List<String>) {
        // TODO: POST /settings/allowed-apps.
    }

    /** GET Monitoring Schedule — active start/end times and days. */
    suspend fun getMonitoringSchedule(): MonitoringSettings.Schedule? = null

    /** UPDATE Monitoring Schedule — persist the active schedule. */
    suspend fun updateMonitoringSchedule(schedule: MonitoringSettings.Schedule) {
        // TODO: POST /settings/schedule (start/end time, weekdays/weekends).
    }

    // Future cloud integration: Firebase Firestore, AWS AppSync / API Gateway,
    // and a local database (Room) cache — all behind these same functions.
}
