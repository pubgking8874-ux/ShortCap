package com.shortscap.app.shorts

import android.util.Log
import com.shortscap.app.monitoring.WindowContentEvidence

/**
 * LinkedIn video adapter.
 *
 * LinkedIn hosts short-form video surfaces inside its feed (plus articles,
 * jobs, messaging, learning). Detection tiers:
 *
 *  1. Activity class match — video-specific activity, confidence 0.80.
 *  2. Activity class keyword — "video", "player" in class name, confidence 0.65.
 *  3. Structural content evidence — video node classes/IDs, confidence 0.70.
 *  4. Content description text signals — video-unique labels, confidence 0.60.
 *  5. Scroll interaction fallback — confidence from scrollInteractionConfidence().
 *
 * Without any of these signals → UNKNOWN / low confidence (never counted).
 */
object LinkedInVideoAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.LINKEDIN
    override val packageNames: Set<String> = setOf("com.linkedin.android")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult {
        val className = signals.activityClassName

        Log.i("SC_PLATFORM_OBS",
            "SC_PLATFORM_OBS ADAPTER_INPUT pkg=${signals.packageName} cls=$className " +
                "interactionCount=${signals.interactionCount} " +
                "evidenceClasses=${signals.contentEvidence.nodeClasses.size} " +
                "evidenceIds=${signals.contentEvidence.nodeViewIds.size} " +
                "descs=${signals.contentEvidence.nodeContentDescriptions.size}"
        )

        // Tier 1: video-specific activity class
        if (className != null && className.lowercase().let {
                it.contains("video") || it.contains("player")
            }) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=ACTIVITY_CLASS surface=LINKEDIN_SHORT_VIDEO confidence=0.80"
            )
            return shortsResult(confidence = 0.80f, surfaceSignal = "activity_class")
        }

        // Tier 2: hosting class — need content evidence
        if (className != null && className.lowercase().let {
                it.contains("feed") || it.contains("main") || it.contains("home")
            }) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_CHECK pkg=${signals.packageName} " +
                    "method=HOSTING_CLASS cls=$className checking=content_evidence"
            )
        }

        // Tier 3: structural content evidence
        val result = evaluateContentEvidence(signals.contentEvidence)
        if (result != null) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=CONTENT_EVIDENCE surface=LINKEDIN_SHORT_VIDEO confidence=${result.confidence}"
            )
            return result
        }

        // Tier 4: content description text signals
        val textResult = evaluateTextEvidence(signals.contentEvidence)
        if (textResult != null) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=TEXT_EVIDENCE surface=LINKEDIN_SHORT_VIDEO confidence=${textResult.confidence}"
            )
            return textResult
        }

        // Tier 5: scroll interaction fallback
        val scrollConf = scrollInteractionConfidence(signals.interactionCount)
        if (scrollConf >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=SCROLL_FALLBACK surface=LINKEDIN_SHORT_VIDEO confidence=$scrollConf"
            )
            return scrollDetectedResult(ShortPlatform.LINKEDIN, ShortSurface.LINKEDIN_SHORT_VIDEO, signals.interactionCount)
        }

        Log.i("SC_PLATFORM_OBS",
            "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                "result=UNCONFIRMED isShortForm=false confidence=0.10 reason=NO_VIDEO_SIGNALS"
        )
        return unconfirmedResult(ShortPlatform.LINKEDIN, 0.10f)
    }

    private fun shortsResult(confidence: Float, surfaceSignal: String): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.LINKEDIN,
            surface = ShortSurface.LINKEDIN_SHORT_VIDEO,
            isShortForm = true,
            confidence = confidence,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf("surfaceSignal" to surfaceSignal),
        )

    /** Structural evidence: video node classes/IDs. */
    private fun evaluateContentEvidence(evidence: WindowContentEvidence): ShortDetectionResult? {
        val classHits = evidence.nodeClasses.count { it.lowercase().let {
            it.contains("video") || it.contains("player")
        }}
        val idHits = evidence.nodeViewIds.count { it.lowercase().let {
            it.contains("video") || it.contains("player")
        }}
        if (classHits == 0 && idHits == 0) return null
        return ShortDetectionResult(
            platform = ShortPlatform.LINKEDIN,
            surface = ShortSurface.LINKEDIN_SHORT_VIDEO,
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

    /** Text signals unique to LinkedIn video. */
    private fun evaluateTextEvidence(evidence: WindowContentEvidence): ShortDetectionResult? {
        val textHit = evidence.nodeContentDescriptions.any { desc ->
            val lower = desc.lowercase()
            LINKEDIN_UNIQUE_DESCRIPTIONS.any { lower.contains(it) }
        }
        if (!textHit) return null
        return ShortDetectionResult(
            platform = ShortPlatform.LINKEDIN,
            surface = ShortSurface.LINKEDIN_SHORT_VIDEO,
            isShortForm = true,
            confidence = 0.60f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf("surfaceSignal" to "text_evidence"),
        )
    }

    /** Content descriptions unique to LinkedIn video. */
    private val LINKEDIN_UNIQUE_DESCRIPTIONS = setOf(
        "share video",
        "like this video",
        "comment on this video",
    )
}
