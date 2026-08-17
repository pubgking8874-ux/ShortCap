package com.shortscap.app.web.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsFilterTest {

    private fun filter(vararg blocked: String): DnsFilter = DnsFilter { blocked.toSet() }

    @Test
    fun `matches the blocked domain itself`() {
        assertTrue(filter("example.com").matches("example.com"))
    }

    @Test
    fun `matches subdomains of a blocked domain`() {
        val f = filter("example.com")
        assertTrue(f.matches("www.example.com"))
        assertTrue(f.matches("sub.example.com"))
        assertTrue(f.matches("deep.sub.example.com"))
    }

    @Test
    fun `does not accidentally block unrelated domains`() {
        val f = filter("example.com")
        assertFalse(f.matches("example.org"))
        assertFalse(f.matches("notexample.com"))
        assertFalse(f.matches("example.com.evil.com"))
        assertFalse(f.matches("www.example.com.evil.com"))
        assertFalse(f.matches("myexample.com"))
    }

    @Test
    fun `matching is case and trailing-dot insensitive`() {
        val f = filter("example.com")
        assertTrue(f.matches("EXAMPLE.COM"))
        assertTrue(f.matches("WWW.Example.COM"))
        assertTrue(f.matches("example.com."))
    }

    @Test
    fun `handles multiple blocked domains`() {
        val f = filter("example.com", "foo.org")
        assertTrue(f.matches("x.foo.org"))
        assertFalse(f.matches("foo.org.evil.net"))
        assertTrue(f.matches("foo.org"))
    }

    @Test
    fun `blocking a subdomain does not block its parent`() {
        val f = filter("www.example.com")
        assertTrue(f.matches("www.example.com"))
        assertFalse(f.matches("example.com"))
        assertFalse(f.matches("other.example.com"))
    }

    @Test
    fun `empty or blank input is never blocked`() {
        val f = filter("example.com")
        assertFalse(f.matches(""))
        assertFalse(f.matches("   "))
    }
}
