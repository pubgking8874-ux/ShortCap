package com.shortscap.app.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * PermissionActions — launches the correct Android system settings page for
 * each permission.
 *
 * [open] returns true when a settings screen was actually launched, so callers
 * can fall back gracefully (e.g. a toast with the safest manual route) instead
 * of assuming success. For Accessibility, if the system accessibility settings
 * screen cannot be opened, it automatically falls back to the app-details page
 * — the one settings route that exists on every manufacturer (Pixel, Samsung,
 * Xiaomi, OnePlus, ...) — and never hardcodes a vendor-specific screen.
 *
 */
object PermissionActions {

    /** Opens the Android settings page that manages [permissionId]. */
    fun open(context: Context, permissionId: PermissionId): Boolean {
        val packageName = context.packageName
        val intents: List<Intent> = when (permissionId) {
            PermissionId.ACCESSIBILITY -> listOf(
                // Primary route: the system Accessibility settings, where the
                // user finds and enables ShortsCap's own service.
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                // Safe fallback: the app-details page (reachable on every
                // manufacturer); the user can then open Accessibility.
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
            PermissionId.USAGE_ACCESS -> listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            PermissionId.OVERLAY -> listOf(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            PermissionId.NOTIFICATIONS -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
            PermissionId.BATTERY_OPTIMIZATION -> listOf(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
            PermissionId.STORAGE_MEDIA -> listOf(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
        for (intent in intents) {
            if (tryLaunch(context, intent)) return true
        }
        return false
    }

    /** Launches [intent] when an activity can handle it; never throws. */
    private fun tryLaunch(context: Context, intent: Intent): Boolean {
        // resolveActivity guards against a missing handler; runCatching guards
        // against any other launch failure — the app never crashes here.
        if (context.packageManager.resolveActivity(intent, 0) == null) return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
