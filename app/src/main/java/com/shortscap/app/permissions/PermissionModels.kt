package com.shortscap.app.permissions

/**
 * The 8 permissions surfaced in Settings → Permissions.
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
    AUTO_START,
    STORAGE_MEDIA,
    ROOT,
}

/**
 * Live status of a permission. [GRANTED] / [NOT_GRANTED] / [DISABLED] come
 * from real Android checks (see [PermissionRepository]); [FUTURE] and
 * [NOT_AVAILABLE] mark entries that exist in the UI but have no
 * implementation yet.
 */
enum class PermissionStatus {
    GRANTED,
    NOT_GRANTED,
    DISABLED,
    FUTURE,
    NOT_AVAILABLE,
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
