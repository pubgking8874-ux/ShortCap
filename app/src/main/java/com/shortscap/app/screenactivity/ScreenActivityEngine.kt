package com.shortscap.app.screenactivity

import com.shortscap.app.monitoring.MonitoringEventHub

/**
 * ScreenActivityEngine — the dedicated Android engine boundary for GENERAL
 * app/screen usage collection.
 *
 * Owns:
 *  - foreground app/session tracking (via [ScreenActivityCollector])
 *  - local persistence + backend sync (via [ScreenActivityRepository])
 *  - the Screen Activity ON/OFF gate (collection happens ONLY while enabled)
 *
 * Does NOT own: Shorts detection, Shorts counting, Shorts limit, Shorts HUD,
 * enforcement, Score or Rank. It is STRICTLY INDEPENDENT of Shorts Control:
 *  - Screen Activity OFF  -> generic collection stops, Shorts is untouched;
 *  - Shorts Control OFF   -> Screen Activity keeps working.
 *
 * It is a PASSIVE subscriber to [MonitoringEventHub] (the same foreground
 * event source the Shorts pipeline observes) — both domains share the event
 * INFRASTRUCTURE but keep completely separate business logic, state and sync
 * records. The engine never touches ShortControlEngine and ShortControlEngine
 * never touches this engine.
 */
class ScreenActivityEngine(
    private val collector: ScreenActivityCollector = ScreenActivityCollector(),
    private val repository: ScreenActivityRepository,
    isEnabled: () -> Boolean = { true },
) : MonitoringEventHub.MonitoringEventListener {

    /** The Screen Activity ON/OFF gate — re-checked on every event so a
     *  toggle change takes effect immediately without a second state system. */
    @Volatile
    private var gate: () -> Boolean = isEnabled

    private var subscribed = false

    /** Replaces the toggle gate (used when the shared engine is wired at
     *  app start with a Context-bound checker). */
    fun configureGate(isEnabled: () -> Boolean) {
        gate = isEnabled
    }

    /** Subscribes to foreground events (idempotent). */
    fun start() {
        if (subscribed) return
        MonitoringEventHub.subscribe(this)
        subscribed = true
    }

    /** Unsubscribes and closes any in-flight session (persisted via [repository]). */
    fun stop() {
        // Flush the in-flight session FIRST and unconditionally — the
        // collector only persists CLOSED sessions, so the final one would be
        // lost on stop if it were not closed here.
        collector.closeActive()?.let { repository.recordSession(it) }
        if (!subscribed) return
        MonitoringEventHub.unsubscribe(this)
        subscribed = false
    }

    /**
     * Foreground change — general app usage collection. Gated on the Screen
     * Activity toggle: when it is OFF, no session is started/recorded (and
     * any in-flight session is dropped — an app session while Screen Activity
     * is off is never recorded). Shorts detection/counting is NOT affected by
     * this gate (independent domain).
     */
    override fun onForegroundAppChanged(packageName: String, activityClassName: String?) {
        if (!gate()) {
            // Drop any in-flight session — Screen Activity is off, so the
            // partial session must not be recorded when the toggle flips back.
            collector.closeActive()
            return
        }
        collector.onForegroundAppChanged(packageName)?.let { repository.recordSession(it) }
    }

    /** Current unsynced sessions (read-only view for tests/debugging). */
    fun pendingSessions(): List<ScreenActivitySession> = repository.pendingSessions()

    /** Drains local sessions into the backend sync queue for [deviceId]. */
    fun drainToSync(deviceId: Int): Int = repository.drainToSync(deviceId)

    /** Clears local sessions after a confirmed sync. */
    fun clearSynced() = repository.clearSynced()

    companion object {
        /**
         * The store the shared engine records into. Defaults to the
         * in-memory store; [ShortsCapApp] installs the durable Room-backed
         * store at app start so pending Screen Activity sessions survive
         * process death and are drained after restart.
         */
        @Volatile
        private var sharedStore: ScreenActivityStore = InMemoryScreenActivityStore()

        /** Installs the durable store before the shared engine is first used. */
        fun installDurableStore(store: ScreenActivityStore) {
            sharedStore = store
        }

        /**
         * The app-wide Screen Activity engine. Created lazily so the durable
         * store installed at app start is picked up. The engine is started
         * by the Accessibility Service (it only receives foreground events
         * while the service is connected); the gate is bound to the persisted
         * Screen Activity toggle.
         */
        private val shared by lazy {
            ScreenActivityEngine(repository = ScreenActivityRepository(sharedStore))
        }

        /** The shared app-wide engine instance. */
        val sharedInstance: ScreenActivityEngine get() = shared

        /** Subscribes the shared engine to monitoring events (idempotent). */
        fun start(isEnabled: () -> Boolean) {
            shared.configureGate(isEnabled)
            shared.start()
        }

        /** Unsubscribes the shared engine (flushes the in-flight session). */
        fun stop() {
            shared.stop()
        }
    }
}
