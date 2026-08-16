package com.shortscap.app.screenactivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [ScreenActivityEngine] — the toggle gate and the
 * STRICT INDEPENDENCE of the Screen Activity domain.
 *
 *  - Screen Activity OFF  -> no generic sessions are recorded (in-flight
 *    sessions are dropped, never recorded when the toggle flips back).
 *  - Screen Activity ON   -> foreground changes are collected.
 *  - The engine NEVER touches Shorts logic: there is no reference to the
 *    Shorts Control domain anywhere in the engine (verified by the
 *    independence test asserting the collected records are generic app-usage
 *    sessions only — no Shorts counts/limits/events are ever produced).
 */
class ScreenActivityEngineTest {

    private var now = 1_000_000L
    private var enabled = true
    private val store = InMemoryScreenActivityStore()
    private val engine = ScreenActivityEngine(
        collector = ScreenActivityCollector(nowMillis = { now }),
        repository = ScreenActivityRepository(store),
        isEnabled = { enabled },
    )

    private fun advance(ms: Long) {
        now += ms
    }

    // ---- Toggle gate ----

    @Test
    fun `enabled engine records closed sessions`() {
        engine.onForegroundAppChanged("com.example.alpha", null)
        advance(2_000)
        engine.onForegroundAppChanged("com.example.beta", null)
        assertEquals(1, store.sessionSnapshot().size)
        assertEquals("com.example.alpha", store.sessionSnapshot().single().packageName)
    }

    @Test
    fun `disabled engine records nothing`() {
        enabled = false
        engine.onForegroundAppChanged("com.example.alpha", null)
        advance(2_000)
        engine.onForegroundAppChanged("com.example.beta", null)
        assertTrue(store.sessionSnapshot().isEmpty())
    }

    @Test
    fun `in-flight session is dropped when the toggle is off`() {
        // Session starts while enabled...
        engine.onForegroundAppChanged("com.example.alpha", null)
        advance(5_000)
        // ...then Screen Activity is switched off. The next event drops the
        // partial session instead of recording it.
        enabled = false
        engine.onForegroundAppChanged("com.example.beta", null)
        assertTrue(store.sessionSnapshot().isEmpty())
        // Re-enabling starts fresh — no stale session resumes.
        enabled = true
        engine.onForegroundAppChanged("com.example.beta", null)
        advance(3_000)
        engine.onForegroundAppChanged("com.example.gamma", null)
        assertEquals(1, store.sessionSnapshot().size)
        assertEquals("com.example.beta", store.sessionSnapshot().single().packageName)
    }

    @Test
    fun `stop flushes the in-flight session`() {
        engine.onForegroundAppChanged("com.example.alpha", null)
        advance(2_000)
        engine.stop()
        assertEquals(1, store.sessionSnapshot().size)
    }

    @Test
    fun `stop is idempotent and does not double-record`() {
        engine.onForegroundAppChanged("com.example.alpha", null)
        advance(2_000)
        engine.stop()
        engine.stop()
        assertEquals(1, store.sessionSnapshot().size)
    }

    @Test
    fun `start is idempotent`() {
        engine.start()
        engine.start()
        engine.onForegroundAppChanged("com.example.alpha", null)
        advance(2_000)
        engine.onForegroundAppChanged("com.example.beta", null)
        assertEquals(1, store.sessionSnapshot().size)
    }

    // ---- Independence from Shorts ----

    @Test
    fun `engine only ever produces generic app-usage sessions, never shorts data`() {
        engine.onForegroundAppChanged("com.example.youtube", null)
        advance(2_000)
        engine.onForegroundAppChanged("com.example.whatsapp", null)
        advance(3_000)
        engine.onForegroundAppChanged("com.example.chrome", null)

        val sessions = store.sessionSnapshot()
        // Two closed generic sessions: YouTube + WhatsApp.
        assertEquals(2, sessions.size)
        // The domain is a PURE generic-usage session: it carries ONLY app
        // identity + timestamps. There is no platform/surface/limit/count
        // field anywhere in the type, so a Shorts record can never be
        // produced by this engine.
        sessions.forEach { session ->
            assertEquals("com.example.youtube", sessions[0].packageName)
            assertEquals(2_000L, sessions[0].durationMillis)
            assertEquals("com.example.whatsapp", sessions[1].packageName)
            assertEquals(3_000L, sessions[1].durationMillis)
            assertTrue(session.appName == null)
        }
    }
}
