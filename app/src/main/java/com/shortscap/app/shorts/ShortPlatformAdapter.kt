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

/**
 * Confidence derived purely from scroll-interaction evidence — the
 * additional signal the Accessibility Service now provides
 * (TYPE_VIEW_SCROLLED within the foreground context). No scrolls = no
 * evidence (0f, never countable); 1+ scrolls = medium-to-high confidence.
 * The existing confidence threshold (0.5) and 3–5 second engagement rule
 * still gate counting on top of this.
 */
internal fun scrollInteractionConfidence(interactionCount: Int): Float = when {
    interactionCount <= 0 -> 0f
    interactionCount == 1 -> 0.55f
    interactionCount == 2 -> 0.65f
    else -> 0.75f
}

/** Conservative "no countable evidence yet" result (platform identity only). */
internal fun unconfirmedResult(platform: ShortPlatform, baseConfidence: Float): ShortDetectionResult =
    ShortDetectionResult(
        platform = platform,
        surface = ShortSurface.UNKNOWN,
        isShortForm = false,
        confidence = baseConfidence,
        detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
    )

/**
 * A countable result backed by observed scroll interaction on [surface].
 * One context/session still counts as ONE Short (the aggregator adds the
 * 3–5 second rule and countDelta = 1) — scrolls only provide the evidence.
 */
internal fun scrollDetectedResult(
    platform: ShortPlatform,
    surface: ShortSurface,
    interactionCount: Int,
): ShortDetectionResult =
    ShortDetectionResult(
        platform = platform,
        surface = surface,
        isShortForm = true,
        confidence = scrollInteractionConfidence(interactionCount),
        detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        metadata = mapOf("surfaceSignal" to "scroll_interaction"),
    )
