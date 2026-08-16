package com.shortscap.app

import android.app.Application
import com.shortscap.app.db.ShortsCapDatabase
import com.shortscap.app.screenactivity.RoomScreenActivityStore
import com.shortscap.app.screenactivity.ScreenActivityEngine
import com.shortscap.app.shorts.RoomShortsLimitCycleStore
import com.shortscap.app.shorts.RoomShortsLocalStore
import com.shortscap.app.shorts.ShortsControlEngine
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
        ShortsMonitoringPipeline.installControlEngine(ShortsControlEngine.shared)
    }
}
