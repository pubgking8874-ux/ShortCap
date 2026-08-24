package com.shortscap.app.shorts

import android.util.Log
import com.shortscap.app.monitoring.WindowContentEvidence

/**
 * TikTok adapter.
 *
 * TikTok's primary surface IS its short-video feed, but the app also hosts
 * LIVE, stories, search and other screens. Detection tiers:
 *
 *  1. Activity class match — specific short-video activity classes,
 *     confidence 0.80.
 *  2. Shorts/Feed keyword in activity class — any class name containing
 *     "feed", "fyp", or "short" (case-insensitive), confidence 0.65.
 *  3. Structural content evidence — node class or resource id contains
 *     video/feed keywords AND is a player/container, confidence 0.70.
 *  4. Scroll interaction fallback — for builds where no feed-specific
 *     class/ID/text is exposed but the user is scrolling a vertical feed,
 *     confidence from scrollInteractionConfidence().
 *
 * Without any of these signals → UNKNOWN / low confidence (never counted).
 * Both known package aliases (aweme + musically) are covered.
 */
object TikTokAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.TIKTOK
    override val packageNames: Set<String> = setOf(
        "com.ss.android.ugc.aweme",
        "com.zhiliaoapp.musically",
    )

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult {
        val className = signals.activityClassName

        Log.i("SC_PLATFORM_OBS",
            "SC_PLATFORM_OBS ADAPTER_INPUT pkg=${signals.packageName} cls=$className " +
                "interactionCount=${signals.interactionCount} " +
                "evidenceClasses=${signals.contentEvidence.nodeClasses.size} " +
                "evidenceIds=${signals.contentEvidence.nodeViewIds.size} " +
                "descs=${signals.contentEvidence.nodeContentDescriptions.size}"
        )

        // Tier 1: known short-video activity class
        if (className != null && className.lowercase().let {
                it.contains("feed") || it.contains("fyp") || it.contains("main") || it.contains("player")
            }) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=ACTIVITY_CLASS surface=TIKTOK_SHORT_FEED confidence=0.80"
            )
            return shortsResult(confidence = 0.80f, surfaceSignal = "activity_class")
        }

        // Tier 2: activity class contains short-video keywords
        if (className != null && className.lowercase().let {
                it.contains("short") || it.contains("reel") || it.contains("story")
            }) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=SHORT_KEYWORD surface=TIKTOK_SHORT_FEED confidence=0.65"
            )
            return shortsResult(confidence = 0.65f, surfaceSignal = "short_keyword_in_class")
        }

        // Tier 3: structural content evidence
        val result = evaluateContentEvidence(signals.contentEvidence)
        if (result != null) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=CONTENT_EVIDENCE surface=TIKTOK_SHORT_FEED confidence=${result.confidence}"
            )
            return result
        }

        // Tier 4: content description text signals
        val textResult = evaluateTextEvidence(signals.contentEvidence)
        if (textResult != null) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=TEXT_EVIDENCE surface=TIKTOK_SHORT_FEED confidence=${textResult.confidence}"
            )
            return textResult
        }

        // Tier 5: scroll interaction fallback
        val scrollConf = scrollInteractionConfidence(signals.interactionCount)
        if (scrollConf >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=SCROLL_FALLBACK surface=TIKTOK_SHORT_FEED confidence=$scrollConf"
            )
            return scrollDetectedResult(ShortPlatform.TIKTOK, ShortSurface.TIKTOK_SHORT_FEED, signals.interactionCount)
        }

        Log.i("SC_PLATFORM_OBS",
            "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                "result=UNCONFIRMED isShortForm=false confidence=0.35 reason=NO_FEED_SIGNALS"
        )
        return unconfirmedResult(ShortPlatform.TIKTOK, 0.35f)
    }

    private fun shortsResult(confidence: Float, surfaceSignal: String): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.TIKTOK,
            surface = ShortSurface.TIKTOK_SHORT_FEED,
            isShortForm = true,
            confidence = confidence,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf("surfaceSignal" to surfaceSignal),
        )

    /** Structural evidence: node class or resource id contains feed/video keywords + player shape. */
    private fun evaluateContentEvidence(evidence: WindowContentEvidence): ShortDetectionResult? {
        val classHits = evidence.nodeClasses.count { it.lowercase().let {
            (it.contains("feed") || it.contains("player") || it.contains("video")) &&
                (it.contains("player") || it.contains("pager") || it.contains("recycler") || it.contains("container"))
        }}
        val idHits = evidence.nodeViewIds.count { it.lowercase().let {
            (it.contains("feed") || it.contains("player") || it.contains("video") || it.contains("fyp")) &&
                (it.contains("player") || it.contains("pager") || it.contains("recycler"))
        }}
        if (classHits == 0 && idHits == 0) return null
        return ShortDetectionResult(
            platform = ShortPlatform.TIKTOK,
            surface = ShortSurface.TIKTOK_SHORT_FEED,
            isShortForm = true,
            confidence = 0.70f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf(
                "surfaceSignal" to "content_evidence",
                "playerClassHits" to classHits,
                "playerIdHits" to idHits,
            ),
        )
    }

    /** Text signals unique to TikTok short-video feed. */
    private fun evaluateTextEvidence(evidence: WindowContentEvidence): ShortDetectionResult? {
        val textHit = evidence.nodeContentDescriptions.any { desc ->
            val lower = desc.lowercase()
            TIKTOK_UNIQUE_DESCRIPTIONS.any { lower.contains(it) }
        }
        if (!textHit) return null
        return ShortDetectionResult(
            platform = ShortPlatform.TIKTOK,
            surface = ShortSurface.TIKTOK_SHORT_FEED,
            isShortForm = true,
            confidence = 0.60f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf("surfaceSignal" to "text_evidence"),
        )
    }

    private val TIKTOK_UNIQUE_DESCRIPTIONS = setOf(
        "for you",
        "like this video",
        "comment on this video",
    )
}
