package com.shortscap.app.shorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * InstalledShortApplicationRegistry — pure, JVM-only coverage of the
 * supported-platform source of truth and the discovery builder. The
 * Android-backed discover() delegates to the same builder after a
 * PackageManager installed check, so these tests cover the exact matching /
 * ordering / enabled / lock logic the screen renders.
 */
class InstalledShortApplicationRegistryTest {

    // ------------------------------------------------------------------
    // Source of truth — derived from the EXISTING platform registry
    // ------------------------------------------------------------------

    @Test
    fun `supported packages come from the platform registry - not a second list`() {
        assertEquals(
            setOf("com.google.android.youtube"),
            InstalledShortApplicationRegistry.supportedPackages[ShortPlatform.YOUTUBE],
        )
        assertEquals(
            setOf("com.instagram.android"),
            InstalledShortApplicationRegistry.supportedPackages[ShortPlatform.INSTAGRAM],
        )
        assertEquals(
            setOf("com.ss.android.ugc.aweme", "com.zhiliaoapp.musically"),
            InstalledShortApplicationRegistry.supportedPackages[ShortPlatform.TIKTOK],
        )
        assertEquals(
            setOf("com.snapchat.android"),
            InstalledShortApplicationRegistry.supportedPackages[ShortPlatform.SNAPCHAT],
        )
        assertEquals(
            setOf("com.facebook.katana"),
            InstalledShortApplicationRegistry.supportedPackages[ShortPlatform.FACEBOOK],
        )
        assertEquals(
            setOf("in.mohalla.video"),
            InstalledShortApplicationRegistry.supportedPackages[ShortPlatform.MOJ],
        )
        assertEquals(
            setOf("com.twitter.android", "com.twitter.android.lite"),
            InstalledShortApplicationRegistry.supportedPackages[ShortPlatform.X],
        )
        assertEquals(
            setOf("com.linkedin.android"),
            InstalledShortApplicationRegistry.supportedPackages[ShortPlatform.LINKEDIN],
        )
        // The generic fallback adapter (no package names) is not a platform.
        assertFalse(InstalledShortApplicationRegistry.supportedPackages.containsKey(ShortPlatform.UNKNOWN))
    }

    @Test
    fun `platform ids match the canonical Short Control structure`() {
        assertEquals("youtube_shorts", InstalledShortApplicationRegistry.platformId(ShortPlatform.YOUTUBE))
        assertEquals("instagram_reels", InstalledShortApplicationRegistry.platformId(ShortPlatform.INSTAGRAM))
        assertEquals("tiktok", InstalledShortApplicationRegistry.platformId(ShortPlatform.TIKTOK))
        assertEquals("snapchat_spotlight", InstalledShortApplicationRegistry.platformId(ShortPlatform.SNAPCHAT))
        assertEquals("facebook_reels", InstalledShortApplicationRegistry.platformId(ShortPlatform.FACEBOOK))
        assertEquals("moj", InstalledShortApplicationRegistry.platformId(ShortPlatform.MOJ))
        assertEquals("x", InstalledShortApplicationRegistry.platformId(ShortPlatform.X))
        assertEquals("linkedin", InstalledShortApplicationRegistry.platformId(ShortPlatform.LINKEDIN))
        // Round-trip.
        assertEquals(ShortPlatform.YOUTUBE, InstalledShortApplicationRegistry.platformForId("youtube_shorts"))
        assertEquals(ShortPlatform.TIKTOK, InstalledShortApplicationRegistry.platformForId("tiktok"))
        assertNull(InstalledShortApplicationRegistry.platformForId("random_app"))
    }

    @Test
    fun `ordering is the deterministic registry order`() {
        assertEquals(
            listOf(
                ShortPlatform.YOUTUBE,
                ShortPlatform.INSTAGRAM,
                ShortPlatform.TIKTOK,
                ShortPlatform.SNAPCHAT,
                ShortPlatform.FACEBOOK,
                ShortPlatform.MOJ,
                ShortPlatform.X,
                ShortPlatform.LINKEDIN,
            ),
            InstalledShortApplicationRegistry.platformOrder,
        )
    }

    // ------------------------------------------------------------------
    // Pure builder — installed discovery
    // ------------------------------------------------------------------

    private val enabledAll = InstalledShortApplicationRegistry.platformOrder.associate {
        InstalledShortApplicationRegistry.platformId(it) to true
    }

    @Test
    fun `only installed supported platforms appear - unknown apps never match`() {
        val installed = setOf("com.google.android.youtube", "com.instagram.android")
        val entries = InstalledShortApplicationRegistry.buildEntries(
            isInstalled = { installed.contains(it) },
            enabledByPlatformId = enabledAll,
            locked = false,
        )

        assertEquals(2, entries.size)
        assertEquals(ShortPlatform.YOUTUBE, entries[0].platform)
        assertEquals("com.google.android.youtube", entries[0].packageName)
        assertEquals(ShortPlatform.INSTAGRAM, entries[1].platform)

        // A random installed app (not a supported platform) is NOT listed.
        val withRandom = InstalledShortApplicationRegistry.buildEntries(
            isInstalled = { installed.contains(it) || it == "com.random.game" },
            enabledByPlatformId = enabledAll,
            locked = false,
        )
        assertEquals(2, withRandom.size)
        assertTrue(withRandom.none { it.packageName == "com.random.game" })
    }

    @Test
    fun `nothing installed - no entries`() {
        val entries = InstalledShortApplicationRegistry.buildEntries(
            isInstalled = { false },
            enabledByPlatformId = enabledAll,
            locked = false,
        )
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `platform variants - first installed known package wins`() {
        // TikTok lite/musically alias installed -> TikTok listed with the
        // installed alias, once.
        val entries = InstalledShortApplicationRegistry.buildEntries(
            isInstalled = { it == "com.zhiliaoapp.musically" },
            enabledByPlatformId = enabledAll,
            locked = false,
        )
        assertEquals(1, entries.size)
        assertEquals(ShortPlatform.TIKTOK, entries[0].platform)
        assertEquals("com.zhiliaoapp.musically", entries[0].packageName)
    }

    @Test
    fun `enabled state maps from the persisted platform config`() {
        val config = mapOf(
            "youtube_shorts" to true,
            "instagram_reels" to false,
        )
        val entries = InstalledShortApplicationRegistry.buildEntries(
            isInstalled = {
                it == "com.google.android.youtube" || it == "com.instagram.android" || it == "com.linkedin.android"
            },
            enabledByPlatformId = config,
            locked = false,
        )

        assertEquals(3, entries.size)
        assertTrue(entries.first { it.platform == ShortPlatform.YOUTUBE }.enabled)
        assertFalse(entries.first { it.platform == ShortPlatform.INSTAGRAM }.enabled)
        // Platform with no config entry defaults to enabled (participates).
        assertTrue(entries.first { it.platform == ShortPlatform.LINKEDIN }.enabled)
    }

    @Test
    fun `lock state propagates to every entry`() {
        val entries = InstalledShortApplicationRegistry.buildEntries(
            isInstalled = { it == "com.google.android.youtube" },
            enabledByPlatformId = enabledAll,
            locked = true,
        )
        assertEquals(1, entries.size)
        assertTrue(entries[0].locked)
        // Unlocked default.
        val unlocked = InstalledShortApplicationRegistry.buildEntries(
            isInstalled = { it == "com.google.android.youtube" },
            enabledByPlatformId = enabledAll,
            locked = false,
        )
        assertFalse(unlocked[0].locked)
    }

    @Test
    fun `entry marks supported true and installed true for discovered apps`() {
        val entries = InstalledShortApplicationRegistry.buildEntries(
            isInstalled = { it == "com.facebook.katana" },
            enabledByPlatformId = enabledAll,
            locked = false,
        )
        assertEquals(1, entries.size)
        assertTrue(entries[0].installed)
        assertTrue(entries[0].supported)
        assertNull(entries[0].appLabel) // label is resolved by discover() only
    }

    @Test
    fun `installed app is never omitted just because it is locked`() {
        // Locked entries remain fully visible (name, icon, states) — the
        // lock only freezes the toggle, it never hides the application.
        val entries = InstalledShortApplicationRegistry.buildEntries(
            isInstalled = {
                it == "com.google.android.youtube" || it == "com.tiktok.alias.unknown"
            },
            enabledByPlatformId = enabledAll,
            locked = true,
        )
        assertEquals(1, entries.size)
        assertTrue(entries[0].locked)
        assertTrue(entries[0].installed)
    }
}
