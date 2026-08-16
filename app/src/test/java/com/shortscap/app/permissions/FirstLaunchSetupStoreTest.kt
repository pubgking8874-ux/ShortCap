package com.shortscap.app.permissions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * FirstLaunchSetupStore — Robolectric persistence tests: the one-time
 * Permission Setup completion flag must survive process recreation (a NEW
 * store instance reads the same persisted value) and must default to false
 * on a fresh install. [resetForTesting] is test-only.
 *
 * NATIVE SQLite mode mirrors the existing Room tests: the real
 * ShortsCapApplication starts background Room coroutines (durable sync queue)
 * that Robolectric's default legacy SQLite cannot share across threads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FirstLaunchSetupStoreTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun freshStore(): FirstLaunchSetupStore =
        FirstLaunchSetupStore(context()).also { it.resetForTesting() }

    @Test
    fun `fresh install is not completed`() {
        assertFalse(freshStore().isCompleted())
    }

    @Test
    fun `markCompleted persists across store instances`() {
        val appContext = context()
        freshStore()
        FirstLaunchSetupStore(appContext).markCompleted()
        // A NEW instance reads the same persisted value — the process-restart
        // equivalent (the gate must never re-appear after completion).
        assertTrue(FirstLaunchSetupStore(appContext).isCompleted())
    }

    @Test
    fun `resetForTesting clears the completed flag`() {
        val appContext = context()
        freshStore()
        FirstLaunchSetupStore(appContext).markCompleted()
        freshStore()
        assertFalse(FirstLaunchSetupStore(appContext).isCompleted())
    }

    @Test
    fun `required setup permissions cover the monitoring-required contract`() {
        // The first-launch gate must include every permission monitoring needs,
        // so the setup flow can never finish while monitoring would be paused.
        assertTrue(SetupRequiredPermissionIds.containsAll(MonitoringRequiredPermissionIds))
        // And it must be a strict subset of the known permission catalog
        // (only permissions the application actually uses are listed).
        assertTrue(SetupRequiredPermissionIds.all { it in PermissionId.entries })
    }
}
