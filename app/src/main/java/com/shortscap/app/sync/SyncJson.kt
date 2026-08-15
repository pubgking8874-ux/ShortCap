package com.shortscap.app.sync

/**
 * Minimal, dependency-free JSON builder for sync payloads.
 *
 * The syncers produce record payloads with this so the sync core (and its
 * unit tests) never depend on Android runtime JSON (org.json) — the payloads
 * are plain strings that HttpBackendApi sends verbatim. Only flat objects
 * and string/number/boolean/null/array-of-scalars values are needed by the
 * backend schemas the sync layer writes to.
 */
object SyncJson {

    /** Builds a flat JSON object: `{"a":1,"b":"x","c":null,"d":[1,2]}`. */
    fun objectOf(vararg pairs: Pair<String, Any?>): String {
        val fields = pairs
            .filter { it.second != null }
            .joinToString(",") { (key, value) -> "\"${escape(key)}\":${encode(value)}" }
        return "{$fields}"
    }

    private fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${escape(value)}\""
        is Boolean -> if (value) "true" else "false"
        is Int, is Long -> value.toString()
        is Double -> if (value.isFinite()) value.toString() else "null"
        is Float -> if (value.isFinite()) value.toString() else "null"
        is List<*> -> value.joinToString(",", "[", "]") { encode(it) }
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"${escape(k.toString())}\":${encode(v)}"
        }
        else -> "\"${escape(value.toString())}\""
    }

    private fun escape(raw: String): String = buildString {
        raw.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
