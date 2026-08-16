package com.shortscap.app.screenactivity

/**
 * ScreenActivityCollector — PURE session-tracking logic for general app/screen
 * usage. No Android runtime, no I/O: it consumes foreground-window changes
 * ([com.shortscap.app.monitoring.MonitoringEventHub.onForegroundAppChanged])
 * and emits closed [ScreenActivitySession]s.
 *
 * Session handling:
 *  - A foreground change to a NEW package closes the active session and
 *    starts the next one.
 *  - Repeated/duplicate callbacks for the SAME package are ignored (they are
 *    re-dispatches of the same window — never counted as new sessions).
 *  - Sessions shorter than [MIN_SESSION_MILLIS] are dropped (no fake
 *    zero-duration / sub-threshold sessions).
 *
 * The collector itself NEVER decides whether Screen Activity is enabled —
 * that gate lives in [ScreenActivityEngine]. It also NEVER touches Shorts
 * logic.
 */
class ScreenActivityCollector(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    companion object {
        /** Below this a foreground blip is not a real session (no fake rows). */
        const val MIN_SESSION_MILLIS = 1_000L
    }

    private var active: ScreenActivitySession? = null

    /** The package currently in the foreground (null when idle). */
    val activePackage: String?
        get() = active?.packageName

    /**
     * Handles a foreground-window change. Returns the CLOSED session when the
     * previous package was replaced by [packageName] (and it met the minimum
     * duration); returns null when there is nothing to close (same package
     * repeated callback, or no active session).
     */
    fun onForegroundAppChanged(packageName: String): ScreenActivitySession? {
        val current = active
        if (current != null && current.packageName == packageName) {
            // Same window re-dispatched — never a second session.
            return null
        }
        val closed = closeActive()
        active = ScreenActivitySession(
            packageName = packageName,
            startedAtMillis = nowMillis(),
        )
        return closed
    }

    /**
     * Closes the active session (if any) and returns it when it meets the
     * minimum duration; otherwise returns null and drops the blip.
     */
    fun closeActive(): ScreenActivitySession? {
        val current = active ?: return null
        active = null
        val session = current.copy(endedAtMillis = nowMillis())
        return if (session.durationMillis >= MIN_SESSION_MILLIS) session else null
    }
}
