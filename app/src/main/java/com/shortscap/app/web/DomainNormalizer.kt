package com.shortscap.app.web

/**
 * Extracts and normalizes a raw website input into a bare lowercase hostname.
 *
 * Handles the common variations a user may type or paste so that
 * "youtube.com", "www.youtube.com", "https://youtube.com" and
 * "https://WWW.Example.com/feed" all normalize to "example.com".
 *
 * The normalizer deliberately does NOT perform format validation — that is
 * [DomainValidator]'s job. It returns null only when the input cannot be
 * interpreted as a website address at all (empty, an e-mail address, or an
 * unsupported scheme such as ftp://).
 */
object DomainNormalizer {

    /**
     * Normalizes [input] into a bare hostname without scheme, "www." prefix,
     * port, path, query or fragment — lowercased and with any trailing
     * fully-qualified dot removed. Returns null when [input] is not a
     * website-style address (empty, contains '@', or uses an unsupported
     * scheme).
     */
    fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        // "hello@youtube.com" is an e-mail address, not a website address.
        if (s.contains('@')) return null

        var host = s
        val lower = s.lowercase()
        when {
            lower.startsWith("https://") -> host = host.substring(7)
            lower.startsWith("http://") -> host = host.substring(7)
            lower.contains("://") -> return null // unsupported scheme (ftp://, file://, ...)
        }

        // Strip repeated "www." prefixes: "www.www.example.com" -> "example.com".
        while (host.startsWith("www.")) host = host.removePrefix("www.")

        // Drop anything after the hostname: path, query, fragment.
        host = host.substringBefore('/').substringBefore('?').substringBefore('#').trim()

        // A trailing dot marks a fully-qualified domain name; drop it.
        while (host.endsWith('.')) host = host.dropLast(1)

        if (host.isEmpty()) return null
        return host.lowercase()
    }
}
