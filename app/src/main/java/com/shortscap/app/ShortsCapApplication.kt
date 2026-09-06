package com.shortscap.app

import android.app.Application
import com.shortscap.app.activity.ActivityAppNames
import com.shortscap.app.activity.ActivityRepository
import com.shortscap.app.activity.PackageClassifier
import com.shortscap.app.activity.UserAppCatalogResolver
import com.shortscap.app.db.ShortsCapDatabase
import com.shortscap.app.screenactivity.RoomScreenActivityStore
import com.shortscap.app.screenactivity.ScreenActivityEngine
import com.shortscap.app.shorts.RoomShortsLimitCycleStore
import com.shortscap.app.shorts.RoomShortsLocalStore
import com.shortscap.app.shorts.ShortsControlEngine
import com.shortscap.app.shorts.ShortsEnforcementTestHarness
import com.shortscap.app.shorts.ShortsMonitoringPipeline
import com.shortscap.app.sync.RoomSyncQueue
import com.shortscap.app.sync.SyncCoordinator

/**
 * P1-2 — Durable Offline Sync Queue & Process-Restart Recovery.
 *
 * Application startup wiring: builds the single Room database and installs
 * the durable queue + Shorts store BEFORE any activity/service can touch
 * them, so:
 *
 *  - the offline sync queue is persisted to disk (survives process death,
 *    app restart, device reboot) instead of the in-memory default, and
 *  - Shorts usage/events waiting for backend sync survive restart too.
 *
 * Records interrupted mid-send (SYNCING) are returned to the retryable
 * PENDING state when the Room queue is (re)created (RoomSyncQueue init).
 *
 * P1-5 — installs the AUTHORITATIVE Shorts control engine backed by the
 * same Room database, so the active 24-hour limit cycle (count, limit,
 * start, expiry, warning/limit state) survives app restart, process death
 * and force-stop. The shared pipeline feeds every valid Short to it; the
 * HUD and Short Control page read its state instead of owning counts.
 */
class ShortsCapApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val database = ShortsCapDatabase.getInstance(this)
        SyncCoordinator.installDurableQueue(RoomSyncQueue(database.syncQueueDao()))
        ShortsMonitoringPipeline.installDurableStore(RoomShortsLocalStore(database.shortsStoreDao()))
        // Screen Activity: the durable Room-backed store for generic
        // app-usage sessions (independent of the Shorts domain). The engine
        // itself is started/stopped by the Accessibility Service (it only
        // runs while the monitoring service is connected).
        ScreenActivityEngine.installDurableStore(RoomScreenActivityStore(database.screenActivityDao()))
        // P1-5: the authoritative 24-hour cycle state machine.
        ShortsControlEngine.install(
            ShortsControlEngine(store = RoomShortsLimitCycleStore(database.shortsLimitCycleDao()))
        )
        // Shorts Limit 24-hour-cycle reset: heal any stale/corrupt persisted
        // enforcement cycle BEFORE any consumer reads it. The observed
        // "8489:34:56" countdown came from a removed debug "pause" that had
        // persisted an expiry ~1 year in the future (the original expiry was
        // kept only in memory, so it was unrecoverable after restart). The
        // repair converts such a row IN PLACE into the CONFIGURED state — the
        // saved limit and the daily monitoring counters are preserved, the
        // phantom ACTIVE cycle / consumed count are zeroed — so the Shorts
        // Limit page starts clean (READY_TO_ACTIVATE, count 0) and a fresh
        // ACTIVATE starts an exact 24-hour cycle from zero.
        ShortsControlEngine.shared.repairCorruptCycle()
        ShortsMonitoringPipeline.installControlEngine(ShortsControlEngine.shared)
        // Phase 1 reporting: Activity aggregates REAL persisted data from the
        // two existing tables (shorts_usage + screen_activity_usage). Before
        // this install the reporting layer reports zeros, never fake values.
        ActivityRepository.installDataSource(
            ActivityRepository.RoomDataSource(
                shortsDao = database.shortsStoreDao(),
                screenActivityDao = database.screenActivityDao(),
            )
        )
        // Phase 1.4: the pure Activity reporting path shares the SAME
        // device-package exclusions as Home — the real launcher + every
        // enabled input method, resolved once by package identity (never
        // display labels).
        ActivityRepository.installDeviceNonReportablePackages(
            PackageClassifier.resolveDeviceNonReportablePackages(this)
        )
        // Phase 1.7: the device-local USER-APP catalog is the single
        // eligibility gate for Activity/Home reporting — a recorded
        // foreground package is reportable only when Android's own
        // PackageManager reports it as an installed, launchable, user-facing
        // application (no device-specific blacklist). Application identity,
        // display label and icon all come from Android metadata, never from
        // backend `appName`.
        val userAppCatalog = UserAppCatalogResolver.resolve(this)
        ActivityRepository.installUserAppCatalog(userAppCatalog.packages)
        ActivityAppNames.installDeviceLabels(userAppCatalog.labels)
        // Phase 4A.6 — DEBUG test-harness process-restart recovery: restore any
        // in-flight controlled enforcement test (persisted in a dedicated
        // DEBUG-only SharedPreferences namespace) and re-register its count
        // listener BEFORE normal Shorts monitoring begins, so a running test
        // survives process recreation without pressing START TEST again.
        // No-op in release builds (DEBUG-gated inside the harness).
        ShortsEnforcementTestHarness.attach(applicationContext)
    }
}
