package com.shortscap.app.study

import android.content.Context
import android.util.Log
import com.shortscap.app.sounds.SoundEffectCategory
import com.shortscap.app.sounds.SoundTriggerer

/**
 * StudySessionAlerts — the single idempotent "Study Session End" alert path.
 *
 * The session end is detected independently by multiple real-world callers:
 * the AppViewModel ticker (app open, incl. backgrounded-but-alive process),
 * the AppViewModel resume/expiry check, the init-restore on app reopen, and
 * the foreground MonitoringService (process kept alive with no UI at all).
 *
 * [fireEndAlert] collapses every one of those detections into exactly ONE
 * delivery per session: it plays the user's currently selected "Study Session
 * End" sound exactly once (via SoundTriggerer → STUDY_SESSION_END, never any
 * other category) and posts the Android notification exactly once. Idempotency
 * lives in the persisted [StudyPreferenceStore.endAlertFired] flag, so a
 * process restart or the service continuing to run can never re-trigger the
 * same session's alert.
 *
 * It deliberately does NOT clear or mutate the session: the ViewModel still
 * owns state restoration (restriction modes, summary) and clearing.
 */
object StudySessionAlerts {

    private const val TAG = "StudySessionAlerts"

    /**
     * Delivers the end alert for the persisted active session if it has
     * finished and has not been delivered already. Returns true when it fired.
     * Safe to call on any thread and as often as a loop wants; it is a cheap
     * prefs read when there is nothing to fire.
     */
    fun fireEndAlert(context: Context): Boolean {
        val store = StudyPreferenceStore(context)
        val stored = store.loadActiveSession() ?: return false
        if (!stored.session.finished) return false
        if (stored.endAlertFired) return false

        // Play the user's selected Study Session End sound exactly once. The
        // sound honours the Sound & Effects master switch via SoundTriggerer;
        // the notification is independent of it. A sound failure must never
        // stop the notification (and neither must ever crash or block Study.
        runCatching {
            SoundTriggerer.play(context, SoundEffectCategory.STUDY_SESSION_END)
        }.onFailure { t ->
            Log.w(TAG, "Study Session End sound failed: ${t.message}", t)
        }
        StudySessionNotifier.postSessionEnded(context)

        // Mark BEFORE any caller can read the store again — the one-and-only
        // delivery guarantee for this session.
        store.markEndAlertFired()
        return true
    }
}