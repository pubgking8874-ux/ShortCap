package com.shortscap.app.screenactivity

/**
 * Screen Activity — the LOCAL persistence/sync boundary for generic
 * app/screen usage sessions.
 *
 * The engine records every closed foreground session here; a sync layer
 * drains the snapshot through the existing [com.shortscap.app.sync.SyncCoordinator]
 * (POST /monitoring/app-usage/sync) exactly like the Shorts store drains
 * shorts usage/events. The collector itself NEVER talks to the backend.
 *
 * STRICTLY INDEPENDENT of Shorts: this store holds ONLY generic app-usage
 * sessions (package + duration), never Shorts counts, limits or events.
 * Swap the in-memory implementation for the Room-backed store (installed at
 * app start) without touching the engine.
 */
interface ScreenActivityStore {

    /** Persists one closed usage session. */
    fun recordSession(session: ScreenActivitySession)

    /** Snapshot of unsynced sessions (in insertion order). */
    fun sessionSnapshot(): List<ScreenActivitySession>

    /** Clears local sessions (e.g. after a confirmed sync). */
    fun clear()
}

/**
 * In-memory local store — the default so the engine stays usable standalone
 * and in tests. [ShortsCapApp] installs the durable Room-backed store at app
 * start so sessions survive process death and restart.
 */
class InMemoryScreenActivityStore : ScreenActivityStore {

    private val sessions = mutableListOf<ScreenActivitySession>()

    override fun recordSession(session: ScreenActivitySession) {
        sessions += session
    }

    override fun sessionSnapshot(): List<ScreenActivitySession> = sessions.toList()

    override fun clear() {
        sessions.clear()
    }
}
