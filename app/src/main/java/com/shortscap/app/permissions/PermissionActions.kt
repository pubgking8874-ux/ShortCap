package com.shortscap.app.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * PermissionActions — launches the correct Android system settings page for
 * each permission. Future entries (Auto Start, Root) have no system page yet
 * and intentionally do nothing (their UI buttons are disabled).
 */
object PermissionActions {

    /** Opens the Android settings page that manages [permissionId]. */
    fun open(context: Context, permissionId: PermissionId) {
        val packageName = context.packageName
        val intent = when (permissionId) {
            PermissionId.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            PermissionId.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            PermissionId.OVERLAY -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            PermissionId.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            PermissionId.BATTERY_OPTIMIZATION -> Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"),
            )
            PermissionId.STORAGE_MEDIA -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            )
            // Future features — no system settings page yet.
            PermissionId.AUTO_START,
            PermissionId.ROOT,
            -> return
        }
        runCatching { context.startActivity(intent) }
    }
}
