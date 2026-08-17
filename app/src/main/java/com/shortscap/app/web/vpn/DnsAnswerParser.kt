package com.shortscap.app.web.vpn

import java.io.ByteArrayOutputStream
import java.net.InetAddress

/**
 * Minimal DNS *response* helpers used by [LocalVpnService] for IP-level
 * blocking: extracts the A/AAAA addresses from a resolver's answer so the
 * engine can route + drop packets to the IPs of blocked domains. Also builds
 * the A/AAAA queries used to resolve blocked domains (via a protected socket,
 * bypassing the tunnel).
 *
 * The request-side parser ([DnsRequestParser]) is untouched; this reads only
 * answer IP addresses — never response content, and nothing is stored.
 */
internal object DnsAnswerParser {

    private const val TYPE_A = 1
    private const val TYPE_AAAA = 28

    /**
     * Parses [payload] (a DNS response) and returns every A and AAAA answer
     * address for the first question, following name-compression pointers.
     * Empty when malformed or when there are no address answers.
     */
    fun parseAddresses(payload: ByteArray): List<InetAddress> {
        if (payload.size < 12) return emptyList()
        val questionCount = readShort(payload, 4)
        val answerCount = readShort(payload, 6)
        if (answerCount < 1) return emptyList()

        var offset = 12
        repeat(questionCount) {
            offset = skipName(payload, offset) ?: return emptyList()
            if (offset + 4 > payload.size) return emptyList()
            offset += 4 // QTYPE + QCLASS
        }

        val result = mutableListOf<InetAddress>()
        repeat(answerCount) {
            offset = skipName(payload, offset) ?: return result
            if (offset + 10 > payload.size) return result
            val type = readShort(payload, offset)
            val rdLength = readShort(payload, offset + 8)
            val dataStart = offset + 10
            if (dataStart + rdLength > payload.size) return result
            when (type) {
                TYPE_A -> if (rdLength == 4) {
                    result += InetAddress.getByAddress(payload.copyOfRange(dataStart, dataStart + 4))
                }
                TYPE_AAAA -> if (rdLength == 16) {
                    result += InetAddress.getByAddress(payload.copyOfRange(dataStart, dataStart + 16))
                }
            }
            offset = dataStart + rdLength
        }
        return result
    }

    /** Builds a standard single-question DNS query for [domain] and [type] (A or AAAA). */
    fun buildQuery(domain: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x00); out.write(0x5C) // ID (fixed — matching is by socket)
        out.write(0x01); out.write(0x00) // flags: standard query, RD
        out.write(0x00); out.write(0x01) // QDCOUNT = 1
        out.write(0x00); out.write(0x00) // ANCOUNT
        out.write(0x00); out.write(0x00) // NSCOUNT
        out.write(0x00); out.write(0x00) // ARCOUNT
        domain.trimEnd('.').split(".").forEach { label ->
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0x00) // root terminator
        out.write((type ushr 8) and 0xFF); out.write(type and 0xFF)
        out.write(0x00); out.write(0x01) // QCLASS = IN
        return out.toByteArray()
    }

    /**
     * Skips a (possibly compressed) DNS name starting at [start]; returns the
     * offset just past the name, or null when malformed.
     */
    private fun skipName(payload: ByteArray, start: Int): Int? {
        var offset = start
        var afterName = start
        var jumped = false
        var guard = 0
        while (guard++ < 64) {
            if (offset >= payload.size) return null
            val len = payload[offset].toInt() and 0xFF
            when {
                len == 0 -> {
                    offset++
                    return if (jumped) afterName else offset
                }
                len and 0xC0 == 0xC0 -> {
                    if (offset + 1 >= payload.size) return null
                    if (!jumped) afterName = offset + 2
                    jumped = true
                    offset = ((len and 0x3F) shl 8) or (payload[offset + 1].toInt() and 0xFF)
                }
                else -> {
                    if (offset + 1 + len > payload.size) return null
                    offset += 1 + len
                }
            }
        }
        return null
    }

    private fun readShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
}
