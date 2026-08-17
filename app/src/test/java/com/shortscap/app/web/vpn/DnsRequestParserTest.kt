package com.shortscap.app.web.vpn

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsRequestParserTest {

    /** Builds a well-formed single-question UDP DNS query payload. */
    private fun query(domain: String, qdCount: Int = 1): ByteArray {
        val labels = domain.split(".")
        val body = ByteArrayOutputStream()
        // Header: ID 0x1234, flags 0x0100 (standard query), QDCOUNT.
        body.write(0x12); body.write(0x34)
        body.write(0x01); body.write(0x00)
        body.write((qdCount ushr 8) and 0xFF); body.write(qdCount and 0xFF)
        body.write(0); body.write(0) // ANCOUNT
        body.write(0); body.write(0) // NSCOUNT
        body.write(0); body.write(0) // ARCOUNT
        labels.forEach { label ->
            body.write(label.length)
            body.write(label.toByteArray(Charsets.US_ASCII))
        }
        body.write(0) // root terminator
        body.write(0); body.write(1) // QTYPE = A
        body.write(0); body.write(1) // QCLASS = IN
        return body.toByteArray()
    }

    @Test
    fun `extracts bare query domain`() {
        assertEquals("example.com", DnsRequestParser.parseDomain(query("example.com")))
    }

    @Test
    fun `extracts multi-level subdomain`() {
        assertEquals("www.example.com", DnsRequestParser.parseDomain(query("www.example.com")))
        assertEquals("sub.example.com", DnsRequestParser.parseDomain(query("sub.example.com")))
    }

    @Test
    fun `lowercases mixed-case query`() {
        assertEquals("www.example.com", DnsRequestParser.parseDomain(query("WWW.Example.COM")))
    }

    @Test
    fun `rejects non-dns payloads`() {
        assertNull(DnsRequestParser.parseDomain(ByteArray(0)))
        assertNull(DnsRequestParser.parseDomain(ByteArray(4)))
        assertNull(DnsRequestParser.parseDomain(ByteArray(12)))
    }

    @Test
    fun `rejects zero question count`() {
        assertNull(DnsRequestParser.parseDomain(query("example.com", qdCount = 0)))
    }

    @Test
    fun `rejects truncated question`() {
        val full = query("example.com")
        assertNull(DnsRequestParser.parseDomain(full.copyOfRange(0, full.size - 3)))
        assertNull(DnsRequestParser.parseDomain(full.copyOfRange(0, 20)))
    }

    @Test
    fun `rejects invalid label characters`() {
        // Label with a byte outside the allowed hostname charset.
        val payload = query("example.com")
        payload[13] = 0x2A.toByte() // '*' inside the first label
        assertNull(DnsRequestParser.parseDomain(payload))
    }
}
