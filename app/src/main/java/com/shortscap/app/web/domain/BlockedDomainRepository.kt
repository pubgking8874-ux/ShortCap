package com.shortscap.app.web.domain

import com.shortscap.app.db.BlockedDomainDao
import com.shortscap.app.db.BlockedDomainEntity
import com.shortscap.app.web.DomainNormalizer
import com.shortscap.app.web.DomainValidator

/**
 * Domain Blocking Foundation — repository.
 *
 * The single seam between the rest of the app and the durable blocked-domain
 * list that the future Local VPN/DNS filtering engine will consume. Every
 * write is normalized to a canonical domain first (see [DomainNormalizer]),
 * so "https://WWW.Example.com/" and "example.com" resolve to the same stored
 * row, and duplicates are impossible by construction.
 *
 * Backed by the app's EXISTING Room database (ShortsCapDatabase →
 * [BlockedDomainDao]) — no separate database, no second persistence system.
 *
 * This phase only persists the domain list. No VPN, no DNS interception, no
 * actual network filtering — those are the future engine's job.
 */
class BlockedDomainRepository(private val dao: BlockedDomainDao) {

    /**
     * Blocks [rawInput] (any user-typed form: scheme, "www.", path, mixed
     * case). Normalizes the input to its canonical domain, validates it, and
     * persists it. Returns false when the input is not a valid website
     * domain or when it is already blocked (duplicate-safe — never a second
     * row).
     */
    suspend fun add(rawInput: String): Boolean {
        val domain = canonical(rawInput) ?: return false
        if (!DomainValidator.isValidDomain(domain)) return false
        val inserted = dao.insert(
            BlockedDomainEntity(
                domain = domain,
                createdAt = System.currentTimeMillis(),
                enabled = true,
            ),
        )
        return inserted > 0L
    }

    /** Lifts the block for [rawInput] (no-op when it is not blocked). */
    suspend fun remove(rawInput: String) {
        val domain = canonical(rawInput) ?: return
        dao.remove(domain)
    }

    /** Whether [rawInput] resolves to an entry in the blocked list. */
    suspend fun isBlocked(rawInput: String): Boolean {
        val domain = canonical(rawInput) ?: return false
        return dao.isBlocked(domain)
    }

    /** Every blocked domain, oldest first — the list the engine will consume. */
    suspend fun getAll(): List<BlockedDomain> =
        dao.all().map { it.toModel() }

    /** Canonical domain for [rawInput], or null when it is not a website address. */
    private fun canonical(rawInput: String): String? = DomainNormalizer.normalize(rawInput)

    private fun BlockedDomainEntity.toModel() = BlockedDomain(
        domain = domain,
        createdAt = createdAt,
        enabled = enabled,
    )
}
