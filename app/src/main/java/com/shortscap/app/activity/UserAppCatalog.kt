package com.shortscap.app.activity

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager

/**
 * UserAppCatalog — the device-local catalog of USER-FACING applications,
 * resolved from Android's own package metadata (Phase 1.7).
 *
 * Android's [android.content.pm.PackageManager] is the single source of truth
 * for application identity, display label and icon:
 *
 * ```
 * PackageManager
 *     ↓ queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)
 *     installed + launchable + user-facing applications
 *     ↓ minus device launcher / enabled IMEs / known non-reportable internals
 *     UserAppCatalog(packages, labels)
 * ```
 *
 * The catalog is DEVICE-INDEPENDENT — it contains no manufacturer/OEM
 * package blacklists. A package is a user application when Android itself
 * reports it as launchable and user-facing, which naturally excludes system
 * framework packages, System UI, launchers, keyboards/IMEs, background
 * services and transient components (none of them register a CATEGORY_LAUNCHER
 * activity). The conservative [PackageClassifier] is still applied so known
 * internal packages that DO happen to be launchable (e.g. ShortsCap itself,
 * the Google quick-search surface) never become reportable user apps.
 *
 * Activity reporting then uses catalog membership as the single eligibility
 * gate: a recorded foreground package from `screen_activity_usage` is
 * reportable ONLY if it is in this catalog. Raw monitoring data is untouched.
 */
data class UserAppCatalog(
    /** packageName of every installed, launchable, user-facing application. */
    val packages: Set<String>,
    /**
     * Android application label (PackageManager.loadLabel) per package —
     * the authoritative DISPLAY name for local Activity/Home reporting,
     * replacing any persisted/backend `appName` that may disagree with the
     * device (e.g. `org.telegram.messenger` resolves to "Telegram").
     */
    val labels: Map<String, String>,
) {
    /** Pure membership gate used by the reporting layer (testable). */
    fun isUserApplication(packageName: String): Boolean = packageName in packages
}

/**
 * Resolves the device's user-facing application catalog from Android
 * metadata. Crash-safe by construction: any PackageManager / IME failure
 * yields an empty catalog (reporting simply falls back to the existing
 * classifier rules rather than crashing).
 */
object UserAppCatalogResolver {

    /**
     * Builds [UserAppCatalog] from the current device. Called once at app
     * start and installed into [ActivityRepository] + [ActivityAppNames].
     */
    fun resolve(context: Context): UserAppCatalog {
        val pm = context.packageManager

        // 1. Launchable / user-facing apps — Android's own definition of
        //    "an application the user can open". System UI, launchers,
        //    keyboards, services and background components do NOT register
        //    a CATEGORY_LAUNCHER activity, so they never enter the catalog.
        val launchable = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            ).mapNotNull { it.activityInfo?.packageName }
        }.getOrNull().orEmpty()

        // 2. The device launcher (CATEGORY_HOME) — a surface the user passes
        //    through, never a used application.
        val launchers = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            ).mapNotNull { it.activityInfo?.packageName }
        }.getOrNull().orEmpty()

        // 3. Enabled input methods — keyboards are services, not apps.
        val imes = runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.map { it.packageName }.orEmpty()
        }.getOrNull().orEmpty()

        val packages = launchable
            .filter { it !in launchers }
            .filter { it !in imes }
            // Conservative internal-package guard (ShortsCap, quick-search
            // surface, …) — never a manufacturer blacklist.
            .filter { PackageClassifier.isReportable(PackageClassifier.classify(it)) }
            .toSet()

        val labels = packages.mapNotNull { pkg ->
            val label = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm)?.toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            label?.let { pkg to it }
        }.toMap()

        return UserAppCatalog(packages = packages, labels = labels)
    }
}