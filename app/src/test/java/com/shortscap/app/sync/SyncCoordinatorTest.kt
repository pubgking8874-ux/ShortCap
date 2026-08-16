package com.shortscap.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Focused JVM tests for the P1-1 Android API-compatibility fix: [utcDateKey]
 * must produce exactly the same UTC calendar date bucket as the previous
 * `LocalDate.ofInstant(epochMillis, ZoneOffset.UTC)` call, while only using
 * java.time APIs available since Android API 26 (minSdk 26) — the old call
 * required API 34 and would crash with NoSuchMethodError on API 26–33.
 *
 * The timezone policy is unchanged: buckets are always the UTC calendar date
 * of the occurrence (the backend's documented naive-UTC convention), never
 * the device-local date.
 */
class SyncCoordinatorTest {

    /** Epoch millis for an ISO-8601 UTC instant (API 26-safe `Instant.parse`). */
    private fun epoch(isoUtc: String): Long = Instant.parse(isoUtc).toEpochMilli()

    @Test
    fun `buckets correctly across UTC midnight`() {
        // The exact boundary: 23:59:59Z belongs to the previous date,
        // 00:00:00Z belongs to the next.
        assertEquals("2026-08-16", utcDateKey(epoch("2026-08-16T23:59:59Z")))
        assertEquals("2026-08-17", utcDateKey(epoch("2026-08-17T00:00:00Z")))
    }

    @Test
    fun `buckets start and end of month correctly`() {
        assertEquals("2026-09-01", utcDateKey(epoch("2026-09-01T00:00:00Z")))
        assertEquals("2026-08-31", utcDateKey(epoch("2026-08-31T23:59:59Z")))
    }

    @Test
    fun `leap day is preserved`() {
        assertEquals("2024-02-29", utcDateKey(epoch("2024-02-29T12:00:00Z")))
    }

    @Test
    fun `bucketing stays UTC even across a DST transition in other zones`() {
        // 02:30 UTC on 2026-03-08 falls inside the US spring-forward hour;
        // the bucket must remain the UTC calendar date (policy unchanged).
        assertEquals("2026-03-08", utcDateKey(epoch("2026-03-08T02:30:00Z")))
    }

    @Test
    fun `epoch zero buckets to 1970-01-01`() {
        assertEquals("1970-01-01", utcDateKey(0L))
    }
}
