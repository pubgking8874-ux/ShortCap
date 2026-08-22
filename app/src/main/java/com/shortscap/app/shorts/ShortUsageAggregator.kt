package com.shortscap.app.shorts

/**
 * The existing Shorts counting rule, preserved from the app's product logic:
 *  - the user swiping / changing content within ~2 seconds means the item
 *    was NOT meaningfully engaged with -> do NOT count it;
 *  - meaningful engagement reaching the existing 3–5 second threshold ->
 *    the item is eligible to count as one Short.
 *
 * These are the only timing values; no duplicate timing system is created.
 * They are plain constants so a future settings UI can make them
 * configurable without touching call sites.
 */
const val SHORT_SWIPE_RULE_MILLIS = 2_000L
const val SHORT_MIN_ENGAGEMENT_MILLIS = 2_000L
const val SHORT_MAX_ENGAGEMENT_WINDOW_MILLIS = 5_000L

/** One accumulation decision for a single detection. */
data class ShortUsageUpdate(
    /** Whether this detection counts toward the user's Shorts usage. */
    val counted: Boolean,
    val platform: ShortPlatform,
    val surface: ShortSurface,
    /** Duration contributed (ms) when counted, else 0. */
    val durationMillis: Long,
    /** Shorts counted (1 when counted, else 0). */
    val countDelta: Int,
    val detectionMethod: DetectionMethod,
)

/**
 * Separates DETECTION from COUNTING.
 *
 * [ShortPlatformAdapter]s decide WHAT content is being viewed; the
 * aggregator decides WHAT counts toward usage. Detection rules can change
 * without rewriting usage accounting, and vice versa.
 *
 * Conceptual flow:
 *   content appears -> detector -> [ShortDetectionResult] -> aggregator ->
 *   count/duration -> warning/limit state -> event -> backend sync
 */
interface ShortUsageAggregator {
    fun evaluate(result: ShortDetectionResult, signals: ShortDetectionSignals): ShortUsageUpdate
}

/**
 * Conservative default aggregator: applies the 3–5 second counting rule and
 * only ever counts detections that are explicitly short-form with sufficient
 * confidence. UNKNOWN classifications are never counted.
 */
class DefaultShortUsageAggregator(
    private val confidenceThreshold: Float = 0.5f,
    private val swipeRuleMillis: Long = SHORT_SWIPE_RULE_MILLIS,
    private val minEngagementMillis: Long = SHORT_MIN_ENGAGEMENT_MILLIS,
) : ShortUsageAggregator {

    override fun evaluate(
        result: ShortDetectionResult,
        signals: ShortDetectionSignals,
    ): ShortUsageUpdate {
        // Never count what the detector could not classify as short-form.
        if (!result.isShortForm || result.confidence < confidenceThreshold) {
            return ShortUsageUpdate(
                counted = false,
                platform = result.platform,
                surface = result.surface,
                durationMillis = 0L,
                countDelta = 0,
                detectionMethod = result.detectionMethod,
            )
        }

        val engagement = signals.foregroundDurationMillis

        // Rapid swipe/change within ~2s -> the item was not meaningfully
        // engaged with; do NOT count it as a Short.
        if (engagement < swipeRuleMillis) {
            return ShortUsageUpdate(
                counted = false,
                platform = result.platform,
                surface = result.surface,
                durationMillis = 0L,
                countDelta = 0,
                detectionMethod = result.detectionMethod,
            )
        }

        // Meaningful engagement: reaching the 3–5 second threshold counts
        // the item as one Short; longer engagement is still the same Short
        // (its full duration is retained).
        return ShortUsageUpdate(
            counted = engagement >= minEngagementMillis,
            platform = result.platform,
            surface = result.surface,
            durationMillis = if (engagement >= minEngagementMillis) engagement else 0L,
            countDelta = if (engagement >= minEngagementMillis) 1 else 0,
            detectionMethod = result.detectionMethod,
        )
    }
}
