package com.shortscap.app.web

/**
 * The blocking-engine seam for REAL website blocking.
 *
 * IMPORTANT: ShortsCap does not currently block anything at the network
 * level. The Web screens manage a local rule list (BLOCKED / ALLOWED) — a
 * visual + local state only. Nothing in the app claims the UI alone can
 * block a website, and [PlaceholderBlockingEngine] deliberately performs no
 * filtering ([isAvailable] = false).
 *
 * When a real Android-supported mechanism is connected — VPN / DNS-based
 * domain filtering, a local proxy, or an accessibility-driven blocker —
 * implement this interface and swap it into AppViewModel. The Web UI and the
 * [WebRule] data model do not change.
 */
interface WebsiteBlockingEngine {

    /** Whether the engine is actually enforcing rules on this device. */
    val isAvailable: Boolean

    /** Enforce a block for [domain]. */
    suspend fun applyBlock(domain: String): Result<Unit>

    /** Lift the block for [domain]. */
    suspend fun removeBlock(domain: String): Result<Unit>

    /** Whether [domain] is currently blocked by the engine. */
    suspend fun isBlocked(domain: String): Boolean
}

/**
 * Placeholder engine — performs NO network filtering and reports itself
 * unavailable ([isAvailable] = false). It keeps the blocking flow (apply /
 * remove / query) wired end to end so a real implementation can be connected
 * later without redesigning the UI or database architecture, while never
 * faking an actual block. Apply/remove fail loudly rather than silently
 * "succeeding", so no code path can accidentally rely on it as a real
 * blocker.
 */
class PlaceholderBlockingEngine : WebsiteBlockingEngine {

    override val isAvailable: Boolean = false

    override suspend fun applyBlock(domain: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("No blocking engine connected yet"))

    override suspend fun removeBlock(domain: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("No blocking engine connected yet"))

    override suspend fun isBlocked(domain: String): Boolean = false
}
