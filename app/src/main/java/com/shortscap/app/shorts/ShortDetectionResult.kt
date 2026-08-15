package com.shortscap.app.shorts

/**
 * How a piece of content was classified — makes it possible to understand
 * WHY something was (or was not) treated as short-form.
 */
enum class DetectionMethod {
    /** A platform-specific adapter produced the result. */
    PLATFORM_ADAPTER,

    /** Generic UI/accessibility signals produced the result (no platform rules). */
    GENERIC_UI_SIGNAL,

    /** User-interaction timing signals produced the result. */
    INTERACTION_SIGNAL,

    /** No signal was strong enough to classify the content. */
    UNKNOWN,
}

/**
 * The structured outcome of short-form detection for one foreground context.
 *
 * [platform] / [surface] identify WHAT was detected; [isShortForm] is
 * intentionally conservative — it is true only when the detected surface and
 * [confidence] support it. When confidence is insufficient the result
 * reports the platform but leaves surface / isShortForm UNKNOWN, because
 * falsely counting content is worse than not counting it.
 *
 * [detectionMethod] records WHY it was classified this way.
 *
 * [metadata] may carry non-sensitive detection context only — it MUST never
 * contain screen contents, passwords, tokens, or any personal/authentication
 * data.
 */
data class ShortDetectionResult(
    val platform: ShortPlatform,
    val surface: ShortSurface,
    val isShortForm: Boolean,
    /** 0f..1f — how confident the detector is in this classification. */
    val confidence: Float,
    val detectionMethod: DetectionMethod,
    /** Epoch millis when the detection was made. */
    val occurredAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any?> = emptyMap(),
) {
    companion object {
        /** The conservative baseline when nothing can be classified. */
        val UNKNOWN = ShortDetectionResult(
            platform = ShortPlatform.UNKNOWN,
            surface = ShortSurface.UNKNOWN,
            isShortForm = false,
            confidence = 0f,
            detectionMethod = DetectionMethod.UNKNOWN,
        )
    }
}
