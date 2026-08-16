package com.shortscap.app.screenactivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM tests for [ScreenActivityCollector] — the session-tracking logic
 * of the Screen Activity domain. No Android runtime needed.
 *
 * Covers: session start/close, repeated-callback dedupe (a re-dispatched
 * window for the SAME package is never a second session), the minimum
 * duration floor (no fake zero/sub-threshold sessions), and flush-on-stop.
 */
class ScreenActivityCollectorTest {

    private var now = 1_000_000L
    private val collector = ScreenActivityCollector(nowMillis = { now })

    private fun advance(ms: Long) {
        now += ms
    }

    // ---- Session start / close ----

    @Test
    fun `first foreground change starts a session and returns nothing`() {
        val closed = collector.onForegroundAppChanged("com.example.alpha")
        assertNull(closed)
        assertEquals("com.example.alpha", collector.activePackage)
    }

    @Test
    fun `switching to another package closes the previous session`() {
        collector.onForegroundAppChanged("com.example.alpha")
        advance(2_000)
        val closed = collector.onForegroundAppChanged("com.example.beta")
        assertNotNull(closed)
        assertEquals("com.example.alpha", closed?.packageName)
        assertEquals(2_000L, closed?.durationMillis)
        // The new session is now active.
        assertEquals("com.example.beta", collector.activePackage)
    }

    @Test
    fun `session duration is derived from timestamps, never stored separately`() {
        collector.onForegroundAppChanged("com.example.alpha")
        advance(45_000)
        val closed = collector.closeActive()
        assertNotNull(closed)
        assertEquals(45_000L, closed?.durationMillis)
        assertNull(collector.activePackage)
    }

    // ---- Duplicate-callback dedupe ----

    @Test
    fun `repeated callback for the same package is not a second session`() {
        collector.onForegroundAppChanged("com.example.alpha")
        advance(1_000)
        // The accessibility service may re-dispatch the same window.
        assertNull(collector.onForegroundAppChanged("com.example.alpha"))
        advance(10_000)
        // Still ONE session — closing it yields the full uninterrupted time.
        val closed = collector.closeActive()
        assertNotNull(closed)
        assertEquals(11_000L, closed?.durationMillis)
    }

    @Test
    fun `same package re-dispatch does not reset the session clock`() {
        collector.onForegroundAppChanged("com.example.alpha")
        advance(5_000)
        collector.onForegroundAppChanged("com.example.alpha") // ignore
        advance(5_000)
        val closed = collector.closeActive()
        assertEquals(10_000L, closed?.durationMillis)
    }

    // ---- Minimum duration floor ----

    @Test
    fun `sub-threshold session is dropped, not recorded`() {
        collector.onForegroundAppChanged("com.example.alpha")
        advance(500) // below MIN_SESSION_MILLIS
        assertNull(collector.onForegroundAppChanged("com.example.beta"))
        // The blip was dropped entirely — no fake zero-duration session.
        assertEquals("com.example.beta", collector.activePackage)
    }

    @Test
    fun `exactly at the minimum duration is kept`() {
        collector.onForegroundAppChanged("com.example.alpha")
        advance(1_000)
        val closed = collector.closeActive()
        assertNotNull(closed)
        assertEquals(1_000L, closed?.durationMillis)
    }

    // ---- Flush / stop ----

    @Test
    fun `closeActive with no active session returns null`() {
        assertNull(collector.closeActive())
    }

    @Test
    fun `closing an active session twice returns null the second time`() {
        collector.onForegroundAppChanged("com.example.alpha")
        advance(2_000)
        assertNotNull(collector.closeActive())
        assertNull(collector.closeActive())
    }
}
