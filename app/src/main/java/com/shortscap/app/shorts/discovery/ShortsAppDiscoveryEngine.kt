package com.shortscap.app.shorts.discovery

import android.content.Context
import com.shortscap.app.shorts.InstalledShortApplicationRegistry
import com.shortscap.app.shorts.ShortApplicationEntry
import com.shortscap.app.shorts.ShortPlatform

/**
 * Automatic Application Detection Engine — the named entry boundary of the
 * discovery pipeline:
 *
 *   Automatic Application Detection Engine      ← this object
 *     ↓
 *   Installed Applications                       ← PackageManager (Android)
 *     ↓
 *   Shorts Classification                        ← ShortPlatformRegistry + adapters
 *     ↓
 *   Shorts Applications List                     ← ShortApplicationEntry
 *     ↓
 *   Settings → Shorts Control                    ← Shorts Applications screen
 *     ↓
 *   Shorts Limit / Detection / Restriction       ← existing engines (unchanged)
 *
 * It is a THIN boundary, not a second implementation: all matching /
 * ordering / enabled / lock logic lives in the existing
 * [InstalledShortApplicationRegistry] (single source of truth — never
 * duplicated). This engine exists so the pipeline has ONE named entry point
 * the UI talks to, and so a future richer implementation (e.g. a
 * state-holding engine with its own package-change observation) can replace
 * the delegate without touching callers.
 *
 * "Automatic" behavior: discovery runs on demand (screen entry / resume) and
 * is re-triggered by Android package added/removed/replaced broadcasts — a
 * deliberate design choice, NOT a polling loop (see the Shorts Applications
 * screen). The engine itself owns no counting, blocking, or limit logic.
 */
object ShortsAppDiscoveryEngine {

    /** Discover installed supported Shorts apps (real labels via PackageManager). */
    fun discover(
        context: Context,
        enabledByPlatformId: Map<String, Boolean>,
        locked: Boolean,
    ): List<ShortApplicationEntry> =
        InstalledShortApplicationRegistry.discover(context, enabledByPlatformId, locked)

    /** platform → known package identifiers (from the existing adapter registry). */
    val supportedPackages: Map<ShortPlatform, Set<String>>
        get() = InstalledShortApplicationRegistry.supportedPackages

    /** Deterministic registry order (adapter registration order). */
    val platformOrder: List<ShortPlatform>
        get() = InstalledShortApplicationRegistry.platformOrder

    /** Stable config id used by the existing `MonitoringSettings.platforms` list. */
    fun platformId(platform: ShortPlatform): String =
        InstalledShortApplicationRegistry.platformId(platform)

    /** The platform for a config id, or null when not a supported platform. */
    fun platformForId(id: String): ShortPlatform? =
        InstalledShortApplicationRegistry.platformForId(id)
}
