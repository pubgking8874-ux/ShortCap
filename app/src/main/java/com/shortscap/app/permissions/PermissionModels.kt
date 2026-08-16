package com.shortscap.app.permissions

/**
 * The permissions surfaced in Settings → Permissions.
 *
 * Every id maps 1:1 to a future backend `GET /permissions` entry, so adding
 * or removing a permission only touches this enum + the i18n catalog.
 * [SYSTEM_AUDIO_ACCESS] is the Android Notification Policy Access that
 * Study Mode's Sound Mode needs to change the real device ringer mode
 * (Sound / Vibrate / Silent) — it lives here as a central, one-time grant
 * instead of being asked for repeatedly inside Study Mode.
 */
enum class PermissionId {
    USAGE_ACCESS,
    ACCESSIBILITY,
    OVERLAY,
    NOTIFICATIONS,
    BATTERY_OPTIMIZATION,
    STORAGE_MEDIA,
    SYSTEM_AUDIO_ACCESS,
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
 * The permissions required by the core ShortsCap ENGINES — the first-launch
 * Permission Setup gate checks EXACTLY this set, one centralized contract
 * (Settings → Permissions keeps showing all seven for fine-grained control).
 *
 *  - USAGE_ACCESS   → Screen Activity / general app-usage collection
 *  - ACCESSIBILITY  → foreground app/window observation (Shorts detection +
 *                     Screen Activity)
 *  - OVERLAY        → Shorts HUD overlay rendering
 *  - NOTIFICATIONS  → Study Mode / Shorts limit alerts
 *
 * BATTERY_OPTIMIZATION / STORAGE_MEDIA / SYSTEM_AUDIO_ACCESS stay optional:
 * they gate only power-saving, custom-sound and ringer-mode extras — never
 * the app's core engines. Adding/removing a required permission touches only
 * this set; the setup screen and its "all required permissions are ready"
 * gate react automatically.
 */
val SetupRequiredPermissionIds: Set<PermissionId> = setOf(
    PermissionId.USAGE_ACCESS,
    PermissionId.ACCESSIBILITY,
    PermissionId.OVERLAY,
    PermissionId.NOTIFICATIONS,
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
