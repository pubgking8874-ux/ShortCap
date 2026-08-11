package com.shortscap.app.web

/**
 * Layer 1 — local format validation.
 *
 * Runs synchronously (no network) on the normalized hostname produced by
 * [DomainNormalizer]. Only inputs that pass this check may proceed to the
 * DNS/reachability layer ([DomainVerifier]); everything else is rejected
 * immediately as "Invalid website address" without any network activity.
 */
object DomainValidator {

    /**
     * A hostname made of dot-separated labels: each label is 1–63 chars of
     * letters, digits or hyphens (never starting/ending with a hyphen), and
     * at least one dot is required so a bare single label ("localhost")
     * and malformed values ("youtube..com") are rejected.
     */
    private val DOMAIN_REGEX =
        Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$")

    /**
     * True when [domain] is syntactically a valid website domain, e.g.
     * "youtube.com" or "news.example.co.uk". False for "youtube..com",
     * "hello@youtube.com", random text, or anything blank.
     */
    fun isValidDomain(domain: String): Boolean = domain.isNotBlank() && DOMAIN_REGEX.matches(domain)
}
