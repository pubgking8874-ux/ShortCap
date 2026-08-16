package com.shortscap.app.hud

/**
 * The three Shorts HUD appearance modes. The HUD is a UI/presentation layer
 * only — it consumes the existing Shorts detection/aggregation results and
 * never detects or counts Shorts itself.
 */
enum class ShortsHudAppearance {
    /** Branded mode — ShortsCap logo + count (primary application accent). */
    SHORTSCAP,

    /** Animated motivational brain with usage-driven states. */
    BRAIN,

    /** Cleanest minimal mode — a subtle animated numeric counter. */
    LIVE_COUNTER,

    ;

    companion object {
        /** Default appearance on first install. */
        const val DEFAULT_NAME = "SHORTSCAP"

        /** Resolves a stored name safely; unknown values fall back to the default. */
        fun fromName(name: String?): ShortsHudAppearance =
            entries.firstOrNull { it.name == name } ?: SHORTSCAP
    }
}

/**
 * The Brain HUD's behavioral states, derived from Shorts usage relative to
 * the configured daily limit. UI thresholds ONLY — they never modify the
 * existing enforcement/scoring rules.
 *
 *   usage_ratio = current_shorts_count / configured_daily_limit
 *
 *   0–40%            -> HEALTHY      (calm, subtle breathing)
 *   40–75%           -> TIRED        (slightly slower, reduced energy)
 *   75–99%           -> NEAR_LIMIT   (alert, warmer accent, stronger pulse)
 *   >= 100%          -> LIMIT_REACHED (red/alert treatment)
 */
enum class BrainState {
    HEALTHY,
    TIRED,
    NEAR_LIMIT,
    LIMIT_REACHED,
    ;

    companion object {
        /** Ratio where the brain transitions from HEALTHY to TIRED. */
        const val TIRED_AT = 0.40f

        /** Ratio where the brain transitions from TIRED to NEAR_LIMIT. */
        const val NEAR_LIMIT_AT = 0.75f

        /** Ratio at/above which the brain is LIMIT_REACHED. */
        const val LIMIT_REACHED_AT = 1.00f

        /**
         * The brain state for [usageRatio] (0f..1f+, any value is safe).
         * A non-positive ratio (no usage / no limit) maps to HEALTHY; values
         * above 1f map to LIMIT_REACHED.
         */
        fun forRatio(usageRatio: Float): BrainState = when {
            usageRatio < TIRED_AT -> HEALTHY
            usageRatio < NEAR_LIMIT_AT -> TIRED
            usageRatio < LIMIT_REACHED_AT -> NEAR_LIMIT
            else -> LIMIT_REACHED
        }
    }
}
