package com.shortscap.app.web.domain

/**
 * Domain Blocking Foundation — model.
 *
 * A minimal record of ONE blocked domain — exactly the information the future
 * Local VPN/DNS filtering engine needs to enforce a block. The [domain] is
 * the canonical normalized representation (see
 * [com.shortscap.app.web.DomainNormalizer]): a bare lowercase hostname with
 * no scheme, "www." prefix, port, path, query or fragment, so DNS-layer
 * matching is exact and duplicate-free.
 *
 * Fields are intentionally minimal — nothing beyond what the engine requires.
 */
data class BlockedDomain(
    /** Canonical normalized domain, e.g. "example.com". */
    val domain: String,
    /** Epoch-millis timestamp of when the domain was blocked. */
    val createdAt: Long,
    /** Whether the block is currently enforced (future pause toggle). */
    val enabled: Boolean = true,
)
