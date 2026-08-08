package com.shortscap.app.permissions

/**
 * The 6 permissions surfaced in Settings → Permissions.
 *
 * Every id maps 1:1 to a future backend `GET /permissions` entry, so adding
 * or removing a permission only touches this enum + the i18n catalog.
 */
enum class PermissionId {
    USAGE_ACCESS,
    ACCESSIBILITY,
    OVERLAY,
    NOTIFICATIONS,
    BATTERY_OPTIMIZATION,
    STORAGE_MEDIA,
}

/**
 * The permissions that must be granted for monitoring to actually run.
 *
 * This is the SINGLE centralized contract for the monitoring "paused" state:
 * the Home screen (Monitoring Paused section), the Permissions screen, the
 * Monitoring screen and — later — the backend all derive their status from
 * this same set, so monitoring state can never drift between surfaces.
 * Adding or removing a required permission only touches this set (the UI and
 * the derived AppUiState.monitoringPaused react automatically).
 */
val MonitoringRequiredPermissionIds: Set<PermissionId> = setOf(
    PermissionId.USAGE_ACCESS,  // app-usage statistics → usage tracking
    PermissionId.ACCESSIBILITY, // app blocking / restriction enforcement
)

/**
 * Live status of a permission — all three come from real Android checks
 * (see [PermissionRepository]). The UI normalizes them to just two visible
 * labels: Enabled ([GRANTED]) / Disabled ([NOT_GRANTED], [DISABLED]).
 */
enum class PermissionStatus {
    GRANTED,
    NOT_GRANTED,
    DISABLED,
}

/**
 * One permission record — the single shape every Permissions screen consumes.
 *
 * Backend-ready by design:
 *  - [id]            → backend permission identifier
 *  - [status]        → resolved from Android OS checks today, from the API later
 *  - [lastCheckedAt] → epoch-millis of the last status verification (null = never)
 *  - [cloudSyncEnabled] → placeholder for future cloud-sync state
 *  - [analyticsEvent]   → placeholder for future analytics event names
 *
 * Swapping the data source (OS checks → backend API) requires NO UI changes.
 */
data class PermissionInfo(
    val id: PermissionId,
    val status: PermissionStatus = PermissionStatus.NOT_GRANTED,
    val lastCheckedAt: Long? = null,
    // Future cloud sync placeholder — set when backend sync connects.
    val cloudSyncEnabled: Boolean = false,
    // Future analytics placeholder — event key tracked on status changes.
    val analyticsEvent: String? = null,
)
