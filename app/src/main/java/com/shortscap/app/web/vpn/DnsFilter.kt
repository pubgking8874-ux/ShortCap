package com.shortscap.app.web.vpn

/**
 * Decides whether a queried domain is blocked, using the blocked-domain set
 * loaded from [com.shortscap.app.web.domain.BlockedDomainRepository].
 *
 * Matching is exact + subdomain-aware: blocking `example.com` also blocks
 * `www.example.com` and `sub.example.com`, but never `notexample.com` or
 * `example.com.evil.com`. The blocked set is supplied as a lambda so the VPN
 * engine can refresh it live when the user blocks/unblocks a domain.
 */
class DnsFilter(private val blockedDomains: () -> Set<String>) {

    /** True when [rawDomain] (or any subdomain of it) is in the blocked set. */
    fun matches(rawDomain: String): Boolean {
        val domain = rawDomain.trim().lowercase().trimEnd('.')
        if (domain.isEmpty()) return false
        return blockedDomains().any { blocked ->
            val b = blocked.trim().lowercase().trimEnd('.')
            b.isNotEmpty() && (domain == b || domain.endsWith(".$b"))
        }
    }
}
