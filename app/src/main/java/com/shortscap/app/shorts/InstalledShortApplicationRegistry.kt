package com.shortscap.app.shorts

import android.content.Context
import android.content.pm.PackageManager

/**
 * One discovered Shorts application — the local UI/domain representation used
 * by Settings → Short Control → Short Applications.
 *
 * A platform (e.g. YOUTUBE) and an installed application (packageName, real
 * label, icon) are deliberately SEPARATE concepts: a platform may be
 * supported by architecture but absent from the device (then it is simply
 * not listed), and a platform may map to one or more known package
 * identifiers (regional variants / lite versions / future package changes).
 *
 * [appLabel] is the REAL application label resolved via PackageManager (the
 * UI falls back to the platform display name when the package cannot be
 * read); the icon is resolved separately by the UI from [packageName] with
 * the existing generic fallback when unavailable. [locked] reflects the
 * active 24-hour Shorts Control cycle: while a cycle is ACTIVE all toggles
 * are read-only so the user cannot bypass enforcement mid-cycle.
 */
data class ShortApplicationEntry(
    /** The supported platform this entry represents (from the registry). */
    val platform: ShortPlatform,
    /** The installed package identifier matched against the registry. */
    val packageName: String,
    /** Real application label from PackageManager (null when unreadable). */
    val appLabel: String?,
    /** Always true — entries only ever come from supported platforms. */
    val supported: Boolean = true,
    /** Whether the package is currently installed on the device. */
    val installed: Boolean,
    /** User's enable/disable choice for this platform (persisted config). */
    val enabled: Boolean,
    /** True while the 24-hour Shorts Control cycle is active — toggles locked. */
    val locked: Boolean,
)

/**
 * Installed Short Applications discovery layer.
 *
 * Single source of truth: [ShortPlatformRegistry] (the existing adapters'
 * `packageNames`) — NOT a second hardcoded list. Flow:
 *
 *   PackageManager → installed-package check → [ShortPlatformRegistry]
 *   → supported platform → [ShortApplicationEntry] → Short Control UI
 *
 * The registry only ever reports packages that match a SUPPORTED platform;
 * unknown/random installed applications never appear. Ordering is the
 * deterministic adapter registration order, never random PackageManager
 * order. Discovery is cheap (one getPackageInfo per supported package) and
 * is re-run on screen entry / resume / package-change signals — never a
 * polling loop. No package inventory is logged or uploaded anywhere.
 */
object InstalledShortApplicationRegistry {

    /** platform → known package identifiers, derived from the EXISTING
     *  adapter registry (regional variants / aliases included). */
    val supportedPackages: Map<ShortPlatform, Set<String>> =
        ShortPlatformRegistry.all
            .filter { it.platform != ShortPlatform.UNKNOWN }
            .associate { it.platform to it.packageNames }

    /** Deterministic registry order (adapter registration order). */
    val platformOrder: List<ShortPlatform> =
        ShortPlatformRegistry.all.map { it.platform }.filter { it != ShortPlatform.UNKNOWN }

    /** Stable config id used by the existing `MonitoringSettings.platforms` list. */
    fun platformId(platform: ShortPlatform): String = when (platform) {
        ShortPlatform.YOUTUBE -> "youtube_shorts"
        ShortPlatform.INSTAGRAM -> "instagram_reels"
        ShortPlatform.TIKTOK -> "tiktok"
        ShortPlatform.SNAPCHAT -> "snapchat_spotlight"
        ShortPlatform.FACEBOOK -> "facebook_reels"
        ShortPlatform.MOJ -> "moj"
        ShortPlatform.X -> "x"
        ShortPlatform.LINKEDIN -> "linkedin"
        ShortPlatform.UNKNOWN -> "unknown"
    }

    /** The platform for a config id, or null when not a supported platform. */
    fun platformForId(id: String): ShortPlatform? =
        platformOrder.firstOrNull { platformId(it) == id }

    /**
     * PURE builder — unit-testable without Android. Returns, in registry
     * order, the supported platforms whose known packages satisfy
     * [isInstalled]; unknown packages are never included. The FIRST
     * installed known package wins per platform (preferred variant).
     */
    fun buildEntries(
        isInstalled: (String) -> Boolean,
        enabledByPlatformId: Map<String, Boolean>,
        locked: Boolean,
    ): List<ShortApplicationEntry> =
        platformOrder.mapNotNull { platform ->
            val packageName = supportedPackages[platform].orEmpty()
                .firstOrNull { isInstalled(it) }
                ?: return@mapNotNull null
            ShortApplicationEntry(
                platform = platform,
                packageName = packageName,
                appLabel = null, // resolved by the Android-backed discover()
                installed = true,
                enabled = enabledByPlatformId[platformId(platform)] ?: true,
                locked = locked,
            )
        }

    /**
     * Android-backed discovery: installed supported platforms with their REAL
     * package labels. Runs synchronously (a few getPackageInfo calls); the
     * UI calls it on entry / resume / package-change, never in a loop.
     */
    fun discover(
        context: Context,
        enabledByPlatformId: Map<String, Boolean>,
        locked: Boolean,
    ): List<ShortApplicationEntry> {
        val pm = context.packageManager
        return buildEntries(
            isInstalled = { isPackageInstalled(pm, it) },
            enabledByPlatformId = enabledByPlatformId,
            locked = locked,
        ).map { entry ->
            entry.copy(appLabel = loadLabel(pm, entry.packageName))
        }
    }

    /** Whether [packageName] is installed. Requires <queries> visibility on
     *  Android 11+ — the supported packages are declared in the manifest. */
    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean = try {
        pm.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }

    /** Real application label, or null when the package is unreadable. */
    private fun loadLabel(pm: PackageManager, packageName: String): String? = try {
        pm.getApplicationInfo(packageName, 0).loadLabel(pm)?.toString()
    } catch (_: Exception) {
        null
    }
}
