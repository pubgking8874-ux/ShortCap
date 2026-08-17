package com.shortscap.app.web.vpn

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsAnswerParserTest {

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun nameBytes(domain: String): ByteArray {
        val out = ByteArrayOutputStream()
        domain.trimEnd('.').split(".").forEach { label ->
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)
        return out.toByteArray()
    }

    /** Builds a DNS response with one question and the given answer records. */
    private fun response(question: String, vararg answers: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        writeShort(out, 0x1234) // ID
        writeShort(out, 0x8180) // flags (response, RD, RA)
        writeShort(out, 1) // QDCOUNT
        writeShort(out, answers.size) // ANCOUNT
        writeShort(out, 0) // NSCOUNT
        writeShort(out, 0) // ARCOUNT
        out.write(nameBytes(question))
        writeShort(out, 1) // QTYPE A
        writeShort(out, 1) // QCLASS IN
        answers.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun answerA(ip: InetAddress): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xC0); out.write(0x0C) // name pointer → question
        writeShort(out, 1) // type A
        writeShort(out, 1) // class IN
        writeShort(out, 0); writeShort(out, 60) // TTL 60
        writeShort(out, 4) // rdlength
        out.write(ip.address)
        return out.toByteArray()
    }

    private fun answerAaaa(ip: InetAddress): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xC0); out.write(0x0C)
        writeShort(out, 28) // type AAAA
        writeShort(out, 1)
        writeShort(out, 0); writeShort(out, 60)
        writeShort(out, 16) // rdlength
        out.write(ip.address)
        return out.toByteArray()
    }

    @Test
    fun `extracts A and AAAA answers`() {
        val a = InetAddress.getByName("93.184.216.34")
        val aaaa = InetAddress.getByName("2606:2800:220:1:248:1893:25c8:1946")
        val payload = response("example.com", answerA(a), answerAaaa(aaaa))
        val addresses = DnsAnswerParser.parseAddresses(payload)
        assertEquals(2, addresses.size)
        assertTrue(addresses.contains(a))
        assertTrue(addresses.contains(aaaa))
    }

    @Test
    fun `returns empty when there are no answers`() {
        val payload = response("example.com")
        assertTrue(DnsAnswerParser.parseAddresses(payload).isEmpty())
    }

    @Test
    fun `returns empty for garbage`() {
        assertTrue(DnsAnswerParser.parseAddresses(ByteArray(0)).isEmpty())
        assertTrue(DnsAnswerParser.parseAddresses(ByteArray(12)).isEmpty())
        assertTrue(DnsAnswerParser.parseAddresses(ByteArray(100) { 0x7F }).isEmpty())
    }

    @Test
    fun `skips non-address answers`() {
        // CNAME answer (type 5) should be skipped; A answer still found.
        val a = InetAddress.getByName("93.184.216.34")
        val cname = ByteArrayOutputStream()
        cname.write(0xC0); cname.write(0x0C)
        writeShort(cname, 5) // type CNAME
        writeShort(cname, 1)
        writeShort(cname, 0); writeShort(cname, 60)
        writeShort(cname, 2) // rdlength
        cname.write(0x00); cname.write(0x00) // root name
        val payload = response("example.com", cname.toByteArray(), answerA(a))
        val addresses = DnsAnswerParser.parseAddresses(payload)
        assertEquals(1, addresses.size)
        assertTrue(addresses.contains(a))
    }

    @Test
    fun `buildQuery is a valid DNS query for DnsRequestParser`() {
        val query = DnsAnswerParser.buildQuery("example.com", 1)
        assertEquals("example.com", DnsRequestParser.parseDomain(query))
        val v6 = DnsAnswerParser.buildQuery("WWW.Example.COM", 28)
        assertEquals("www.example.com", DnsRequestParser.parseDomain(v6))
    }
}
