package com.shortscap.app.shorts

/**
 * Centralized platform recognition.
 *
 * Maps package names to platform adapters so package-name checks never
 * spread through MonitoringService / the accessibility service. Unknown
 * packages fall back to [GenericShortVideoAdapter] (conservative — never
 * fabricates a detection).
 *
 * Extensibility (future platform): 1) add a [ShortPlatform] value,
 * 2) add a [ShortSurface] value if needed, 3) add an adapter,
 * 4) register it in [adapters]. The aggregator, backend sync and reporting
 * keep working unchanged.
 */
object ShortPlatformRegistry {

    /** Every platform-specific adapter, in registration order. */
    private val adapters: List<ShortPlatformAdapter> = listOf(
        YouTubeShortsAdapter,
        InstagramReelsAdapter,
        TikTokAdapter,
        SnapchatSpotlightAdapter,
        FacebookReelsAdapter,
        MojAdapter,
        XVideoAdapter,
        LinkedInVideoAdapter,
    )

    private val genericFallback: ShortPlatformAdapter = GenericShortVideoAdapter

    /** packageName -> adapter, built once from every adapter's known packages. */
    private val byPackage: Map<String, ShortPlatformAdapter> = buildMap {
        for (adapter in adapters) {
            for (pkg in adapter.packageNames) {
                put(pkg, adapter)
            }
        }
    }

    /** All registered platform-specific adapters (for iteration/tests). */
    val all: List<ShortPlatformAdapter> get() = adapters

    /** The adapter for [packageName], or the generic fallback when unknown. */
    fun adapterFor(packageName: String?): ShortPlatformAdapter =
        packageName?.let { byPackage[it] } ?: genericFallback

    /** The platform for [packageName] without running surface detection. */
    fun platformFor(packageName: String?): ShortPlatform =
        adapterFor(packageName).platform

    /** Run the appropriate adapter for [signals] and return its result. */
    fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        adapterFor(signals.packageName).detect(signals)
}
