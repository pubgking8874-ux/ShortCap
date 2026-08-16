package com.shortscap.app.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * AccessibilityServiceStatus — the single, authoritative checker for whether
 * ShortsCap's OWN accessibility service is currently enabled by the user.
 *
 * The Permissions screen, PermissionRepository (and through it the derived
 * Monitoring Paused state on Home) all read this one check, so the app can
 * never disagree about the service state — and it never assumes the user
 * granted the permission merely because the Android Settings screen was
 * opened. Opening settings changes nothing; only the real OS state does.
 *
 * The check is manufacturer-agnostic: it reads the same
 * [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES] setting that every Android
 * device (Pixel, Samsung, Xiaomi, OnePlus, ...) maintains, and matches the
 * exact [ComponentName] of our service.
 */
object AccessibilityServiceStatus {

    /** The ComponentName of ShortsCap's accessibility service. */
    fun component(context: Context): ComponentName =
        ComponentName(context, ShortsCapAccessibilityService::class.java)

    /**
     * True when the ShortsCap accessibility service is in the OS's enabled
     * services list (i.e. the user actually enabled it). Format of the
     * setting: a colon-separated list of ComponentNames, e.g.
     * "com.shortscap.app/com.shortscap.app.accessibility.ShortsCapAccessibilityService"
     * (or, on some devices/OS versions, the short relative form
     * "com.shortscap.app/.accessibility.ShortsCapAccessibilityService" taken
     * from the manifest).
     *
     * Each entry is compared as a PARSED ComponentName ([unflattenFromString]
     * normalizes BOTH the long and the short/relative form), never as a raw
     * string — a raw equality against the long form silently reports
     * "Disabled" on devices that store the short form even though the system
     * shows the service as ON.
     */
    fun isEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = component(context)
        return enabledServices.split(':').any { entry ->
            val enabled = ComponentName.unflattenFromString(entry)
            enabled != null &&
                enabled.packageName == expected.packageName &&
                enabled.className == expected.className
        }
    }
}
