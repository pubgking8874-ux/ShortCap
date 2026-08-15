package com.shortscap.app.screens.settings

import com.shortscap.app.model.MonitoringSettings
import com.shortscap.app.network.ApiResult
import com.shortscap.app.network.MonitoringSettingsDto
import com.shortscap.app.sync.SettingsSyncer
import com.shortscap.app.sync.SyncCoordinator

/**
 * SettingsRepository — backend seam for all Settings data, mirroring the
 * ProfileRepository pattern. Since Phase 16, the seam is wired to the real
 * backend through [SyncCoordinator]:
 *
 *  - reads fetch from the backend (server-authoritative during initial
 *    sync / refresh) and FALL BACK to the local model when the backend is
 *    unreachable — the UI never breaks offline;
 *  - writes enqueue a sync record immediately (local change is
 *    authoritative; the successful backend response confirms persistence —
 *    conflict policy, Phase 16 §7).
 *
 * The UI keeps consuming the same [MonitoringSettings] shape — no screen
 * changes required.
 */
object SettingsRepository {

    /**
     * GET Monitoring Settings — server value when available, local fallback
     * otherwise. Device monitoring / monitoring / strict mode map 1:1 to the
     * backend `monitoring_settings` fields; the app's screen-time limit and
     * per-platform toggles stay local (the backend schema does not carry
     * them — no invented fields).
     */
    suspend fun getMonitoringSettings(): MonitoringSettings {
        return when (val result = SyncCoordinator.api.getMonitoringSettings()) {
            is ApiResult.Success -> {
                val dto: MonitoringSettingsDto = result.data
                MonitoringSettings(
                    enabled = dto.monitoringEnabled ?: true,
                    appBlockingEnabled = dto.deviceMonitoringEnabled ?: true,
                    strictModeEnabled = dto.strictModeEnabled ?: false,
                )
            }
            else -> MonitoringSettings() // offline / error -> local fallback
        }
    }

    /**
     * UPDATE Monitoring Settings — persist the whole model (cloud sync).
     * Enqueues the sync record; the queue retries until the backend confirms.
     */
    suspend fun updateMonitoringSettings(settings: MonitoringSettings) {
        SyncCoordinator.enqueue(
            SettingsSyncer.monitoringSettings(
                mapOf(
                    "deviceMonitoringEnabled" to settings.appBlockingEnabled,
                    "monitoringEnabled" to settings.enabled,
                    "strictModeEnabled" to settings.strictModeEnabled,
                )
            )
        )
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
