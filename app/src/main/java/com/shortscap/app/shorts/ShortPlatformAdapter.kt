package com.shortscap.app.shorts

/**
 * One platform's short-form detection rules.
 *
 * Each adapter answers:
 *  1. Is this my platform?  -> [supports] / [packageNames]
 *  2. What surface is likely showing?  -> [detect]
 *  3. How confident is that answer?  -> [ShortDetectionResult.confidence]
 *  4. Which surface was detected?  -> [ShortDetectionResult.surface]
 *
 * Adapters are PURE rule engines: they receive [ShortDetectionSignals] and
 * return a [ShortDetectionResult]. They never own counters, budgets, or
 * timers — classification is strictly separated from usage accounting (see
 * [ShortUsageAggregator]), so detection rules can change without rewriting
 * usage accounting.
 *
 * IMPORTANT: adapters must be CONSERVATIVE. When a platform cannot be
 * reliably classified with the available signals, return UNKNOWN surface /
 * low confidence instead of fabricating a detection. Full universal
 * detection accuracy is NOT claimed.
 */
interface ShortPlatformAdapter {

    /** The platform this adapter knows how to detect. */
    val platform: ShortPlatform

    /** Every package name that identifies this platform (aliases included). */
    val packageNames: Set<String>

    /** 1. Is [packageName] this adapter's platform? */
    fun supports(packageName: String?): Boolean =
        packageName != null && packageName in packageNames

    /** 2/3/4. Detect the current surface for this platform. */
    fun detect(signals: ShortDetectionSignals): ShortDetectionResult
}
