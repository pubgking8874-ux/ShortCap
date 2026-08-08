package com.shortscap.app.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shortscap.app.accessibility.AccessibilityServiceStatus

/**
 * PermissionRepository — the single data seam for the Permissions module.
 *
 * Today [checkAll] / [checkStatus] resolve each permission's live status from
 * the Android OS (AppOps, Accessibility settings, overlay, notifications,
 * battery, storage). Tomorrow the same functions are replaced by backend API
 * calls (or a local DB) behind the exact same [PermissionInfo] shape — no UI
 * changes required.
 *
 * The future cloud-sync / analytics placeholders are intentionally documented
 * but not implemented (backend-ready only).
 */
object PermissionRepository {

    /** Initial state for every permission card. */
    fun seedPermissions(): List<PermissionInfo> = PermissionId.entries.map { id ->
        PermissionInfo(id = id, status = PermissionStatus.NOT_GRANTED)
    }

    /**
     * Re-checks every permission and stamps [PermissionInfo.lastCheckedAt].
     * Called whenever a Permissions screen resumes, so the UI updates
     * automatically after the user returns from Android Settings.
     */
    fun checkAll(context: Context, current: List<PermissionInfo>): List<PermissionInfo> {
        val now = System.currentTimeMillis()
        return current.map { info ->
            info.copy(status = checkStatus(context, info.id), lastCheckedAt = now)
        }
    }

    /** Resolves the live OS status of one permission. */
    fun checkStatus(context: Context, permissionId: PermissionId): PermissionStatus = when (permissionId) {
        PermissionId.USAGE_ACCESS -> usageAccessStatus(context)
        PermissionId.ACCESSIBILITY -> accessibilityStatus(context)
        PermissionId.OVERLAY -> overlayStatus(context)
        PermissionId.NOTIFICATIONS -> notificationsStatus(context)
        PermissionId.BATTERY_OPTIMIZATION -> batteryStatus(context)
        PermissionId.STORAGE_MEDIA -> storageStatus(context)
        PermissionId.SYSTEM_AUDIO_ACCESS -> systemAudioAccessStatus(context)
    }

    // ---- Future backend seams (placeholders only — not implemented) ----

    /** FUTURE: POST /permissions — persist statuses for cloud sync. */
    suspend fun syncPermissionsToCloud(permissions: List<PermissionInfo>) {
        // TODO: backend sync (AWS / Firebase / Python API).
    }

    /** FUTURE: analytics event fired when a permission status changes. */
    fun trackPermissionAnalytics(permissionId: PermissionId, status: PermissionStatus) {
        // TODO: analytics SDK call.
    }

    /**
     * Notification Policy Access — the Android system authorization required
     * to change the device ringer mode (Sound / Vibrate / Silent). This is
     * the SINGLE source of truth for that OS state: the Permissions screen
     * (System Audio Access) and Study Mode's Sound Mode flow both read it
     * here, so they can never disagree. Granted by the user once in the
     * system settings page (see PermissionActions), not a runtime permission.
     */
    fun isNotificationPolicyAccessGranted(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    // ---- Real OS checks ----

    private fun usageAccessStatus(context: Context): PermissionStatus {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return if (mode == AppOpsManager.MODE_ALLOWED) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED
        }
    }

    /**
     * Accessibility Service status — the REAL OS state of ShortsCap's OWN
     * accessibility service, resolved by the centralized
     * [com.shortscap.app.accessibility.AccessibilityServiceStatus] checker
     * (exact ComponentName match against ENABLED_ACCESSIBILITY_SERVICES).
     * GRANTED only when the user actually enabled the service — never assumed
     * from merely opening the Settings screen. The derived monitoring-paused
     * state on Home reads this same result automatically.
     */
    private fun accessibilityStatus(context: Context): PermissionStatus =
        if (AccessibilityServiceStatus.isEnabled(context)) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED
        }

    private fun overlayStatus(context: Context): PermissionStatus =
        if (Settings.canDrawOverlays(context)) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED
        }

    private fun notificationsStatus(context: Context): PermissionStatus =
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED
        }

    private fun batteryStatus(context: Context): PermissionStatus {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED
        }
    }

    private fun storageStatus(context: Context): PermissionStatus {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        return if (granted) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED
    }

    /**
     * System Audio Access — the REAL Android Notification Policy Access that
     * Study Mode's Sound Mode requires to change the device ringer mode
     * (Sound / Vibrate / Silent). Not a runtime permission: it is granted by
     * the user once in the Android system settings page (see
     * PermissionActions) and re-checked automatically on every resume.
     */
    private fun systemAudioAccessStatus(context: Context): PermissionStatus =
        if (isNotificationPolicyAccessGranted(context)) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_GRANTED
        }
}
