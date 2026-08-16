package com.shortscap.app.accessibility

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * AccessibilityServiceStatus — regression coverage for the enabled-services
 * check. Android stores enabled accessibility services as a colon-separated
 * list of ComponentNames, and devices/OS versions differ between the LONG
 * form ("pkg/full.Class") and the SHORT relative form ("pkg/.relative.Class"
 * taken from the manifest). The check must treat BOTH as enabled — a raw
 * string equality against the long form silently reports "Disabled" on
 * devices that store the short form even though the system shows the service
 * ON.
 *
 * NATIVE SQLite mode mirrors the existing Robolectric tests: the real
 * ShortsCapApplication starts background Room coroutines that the default
 * legacy SQLite cannot share across threads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AccessibilityServiceStatusTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun setEnabledServices(value: String?) {
        // The real putString is shadowed by Robolectric into the settings
        // table; an empty value clears the setting (null-like for the check).
        Settings.Secure.putString(
            context().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            value.orEmpty(),
        )
    }

    @Test
    fun `long form component is recognized as enabled`() {
        setEnabledServices("com.shortscap.app/com.shortscap.app.accessibility.ShortsCapAccessibilityService")
        assertTrue(AccessibilityServiceStatus.isEnabled(context()))
    }

    @Test
    fun `short relative form component is recognized as enabled`() {
        // The manifest registers the service as ".accessibility.ShortsCapAccessibilityService";
        // some devices store exactly that relative form in the setting.
        setEnabledServices("com.shortscap.app/.accessibility.ShortsCapAccessibilityService")
        assertTrue(AccessibilityServiceStatus.isEnabled(context()))
    }

    @Test
    fun `other packages in the list never count`() {
        setEnabledServices(
            "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService",
        )
        assertFalse(AccessibilityServiceStatus.isEnabled(context()))
    }

    @Test
    fun `empty or missing setting means disabled`() {
        setEnabledServices(null)
        assertFalse(AccessibilityServiceStatus.isEnabled(context()))
        setEnabledServices("")
        assertFalse(AccessibilityServiceStatus.isEnabled(context()))
    }

    @Test
    fun `short form among other services is recognized`() {
        setEnabledServices(
            "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService" +
                ":com.shortscap.app/.accessibility.ShortsCapAccessibilityService",
        )
        assertTrue(AccessibilityServiceStatus.isEnabled(context()))
    }
}
