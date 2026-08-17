package com.shortscap.app.web.vpn

/**
 * Parses the queried domain out of a raw UDP DNS request payload.
 *
 * Only what is needed for domain blocking is read: the DNS header (to find
 * the question count) and the question's QNAME (the requested domain). No
 * response content, payload data, or user traffic is inspected or stored.
 *
 * The parser is bounds-checked and returns null for anything that is not a
 * well-formed single-question DNS query, so malformed/garbage packets are
 * never acted on.
 */
object DnsRequestParser {

    private const val HEADER_LENGTH = 12
    private const val MAX_LABELS = 127

    /**
     * Extracts the (lowercased) queried domain from [payload], or null when
     * the payload is not a well-formed DNS query. Example: the question
     * "WWW.Example.COM" → `"www.example.com"`.
     */
    fun parseDomain(payload: ByteArray): String? {
        if (payload.size < HEADER_LENGTH + 5) return null

        // Header: bytes 0-1 ID, 2-3 flags, 4-5 QDCOUNT (must be >= 1).
        val questionCount = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
        if (questionCount < 1) return null

        // ---- Question section: QNAME (length-prefixed labels) ----
        val labels = mutableListOf<String>()
        var offset = HEADER_LENGTH
        while (true) {
            if (offset >= payload.size) return null
            val len = payload[offset].toInt() and 0xFF
            offset += 1

            if (len == 0) {
                // Root terminator — a valid query must have at least one label.
                if (labels.isEmpty()) return null
                break
            }
            // Compression pointers (0xC0) are not expected in a client query's
            // question name; treat as malformed rather than following offsets.
            if (len and 0xC0 != 0) return null
            if (len > 63 || offset + len > payload.size) return null

            val label = String(payload, offset, len, Charsets.US_ASCII).lowercase()
            // Only plausible hostname characters are accepted.
            if (!label.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null

            labels += label
            offset += len
            if (labels.size > MAX_LABELS) return null
        }

        // QTYPE (2) + QCLASS (2) must follow the name.
        if (payload.size < offset + 4) return null
        return labels.joinToString(".")
    }
}
