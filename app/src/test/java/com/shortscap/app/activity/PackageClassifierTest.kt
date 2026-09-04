package com.shortscap.app.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.2 classification tests — the conservative package classifier used
 * by EVERY reporting surface (Activity + Home). Pure JVM: only the static
 * rules are asserted here (the context-aware launcher/IME pass needs a real
 * PackageManager and is covered by on-device runtime verification).
 */
class PackageClassifierTest {

    @Test
    fun `system ui is classified SYSTEM_UI and not reportable`() {
        assertEquals(
            PackageClassifier.Category.SYSTEM_UI,
            PackageClassifier.classify("com.android.systemui"),
        )
        assertFalse(PackageClassifier.isReportable(PackageClassifier.classify("com.android.systemui")))
    }

    @Test
    fun `gboard latin IME is INPUT_METHOD and not reportable`() {
        assertEquals(
            PackageClassifier.Category.INPUT_METHOD,
            PackageClassifier.classify("com.google.android.inputmethod.latin"),
        )
        assertFalse(PackageClassifier.isReportable(PackageClassifier.classify("com.google.android.inputmethod.latin")))
    }

    @Test
    fun `bare android system package is SYSTEM_SERVICE and not reportable`() {
        assertEquals(
            PackageClassifier.Category.SYSTEM_SERVICE,
            PackageClassifier.classify("android"),
        )
        assertFalse(PackageClassifier.isReportable(PackageClassifier.classify("android")))
    }

    @Test
    fun `google quick search box is excluded as a launcher search surface`() {
        assertEquals(
            PackageClassifier.Category.SYSTEM_SERVICE,
            PackageClassifier.classify("com.google.android.googlequicksearchbox"),
        )
        assertFalse(PackageClassifier.isReportable(PackageClassifier.classify("com.google.android.googlequicksearchbox")))
    }

    @Test
    fun `shortscap internal usage is not reportable`() {
        assertEquals(
            PackageClassifier.Category.SHORTSCAP_INTERNAL,
            PackageClassifier.classify("com.shortscap.app"),
        )
        assertFalse(PackageClassifier.isReportable(PackageClassifier.classify("com.shortscap.app")))
    }

    @Test
    fun `legitimate user apps stay reportable`() {
        listOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.android.chrome",
            "com.whatsapp",
            "com.facebook.katana",
            "com.snapchat.android",
        ).forEach { pkg ->
            assertTrue("$pkg must stay reportable", PackageClassifier.isReportable(PackageClassifier.classify(pkg)))
        }
    }

    @Test
    fun `chrome is not hidden by a com_android prefix rule`() {
        assertEquals(PackageClassifier.Category.UNKNOWN, PackageClassifier.classify("com.android.chrome"))
        assertTrue(PackageClassifier.isReportable(PackageClassifier.classify("com.android.chrome")))
    }

    @Test
    fun `unknown packages are conservatively kept`() {
        val category = PackageClassifier.classify("com.example.someapp")
        assertEquals(PackageClassifier.Category.UNKNOWN, category)
        assertTrue(PackageClassifier.isReportable(category))
    }

    // ---- Phase 1.3 — launcher + bare package artifacts ----

    @Test
    fun `launcher3 package is LAUNCHER and not reportable`() {
        // The observed "launcher3" Most Used Apps entry is the device's home
        // app com.android.launcher3 (confirmed from the device log).
        assertEquals(PackageClassifier.Category.LAUNCHER, PackageClassifier.classify("com.android.launcher3"))
        assertFalse(PackageClassifier.isReportable(PackageClassifier.classify("com.android.launcher3")))
    }

    @Test
    fun `bare launcher3 string is a system artifact and not reportable`() {
        // Some OEM accessibility events carry the bare "launcher3" string
        // (no dot) — not a valid installed package; classified as a system
        // artifact by the package-identity rule, never by display label.
        assertEquals(PackageClassifier.Category.SYSTEM_SERVICE, PackageClassifier.classify("launcher3"))
        assertFalse(PackageClassifier.isReportable(PackageClassifier.classify("launcher3")))
    }

    @Test
    fun `bare app string is a system artifact and not reportable`() {
        // A bare "app" package string (no dot) cannot be a real installed
        // application — same package-identity rule as "android".
        assertEquals(PackageClassifier.Category.SYSTEM_SERVICE, PackageClassifier.classify("app"))
        assertFalse(PackageClassifier.isReportable(PackageClassifier.classify("app")))
    }

    @Test
    fun `dotted package ending in app stays reportable`() {
        // If the "app" label actually came from a real dotted package, it is
        // a legitimate user application and must NOT be hidden.
        assertEquals(PackageClassifier.Category.UNKNOWN, PackageClassifier.classify("com.example.app"))
        assertTrue(PackageClassifier.isReportable(PackageClassifier.classify("com.example.app")))
    }

    @Test
    fun `android stays SYSTEM_SERVICE and not reportable`() {
        assertEquals(PackageClassifier.Category.SYSTEM_SERVICE, PackageClassifier.classify("android"))
        assertFalse(PackageClassifier.isReportable(PackageClassifier.classify("android")))
    }
}