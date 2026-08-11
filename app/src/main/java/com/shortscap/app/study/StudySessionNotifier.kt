package com.shortscap.app.study

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shortscap.app.MainActivity
import com.shortscap.app.R

/**
 * StudySessionNotifier — posts the global Android "Study Session Ended"
 * notification through the real system notification manager.
 *
 * Compact, one-shot banner style (NOT full-screen): it is delivered whether
 * ShortsCap is open, in the background, or behind another app (YouTube,
 * Chrome, Instagram, WhatsApp, …), and tapping it opens ShortsCap — it never
 * launches or pulls the app forward on its own. It is dismissible by swipe or
 * the system X (no ONDGOING / no auto-launch).
 *
 * It respects the user's Android notification settings: when notifications are
 * disabled (or POST_NOTIFICATIONS is denied on Android 13+), [postSessionEnded]
 * returns false, logs the skip and lets the sound play independently — it
 * never crashes and never blocks the study-session flow.
 *
 * The Study Mode channel is created lazily on first post and reused thereafter.
 */
object StudySessionNotifier {

    private const val TAG = "StudySessionNotifier"

    /** The Study Mode / session-events notification channel. */
    const val CHANNEL_ID = "study_mode_session_events"

    /** Fixed id — re-posting the same end alert updates, never stacks. */
    private const val NOTIFICATION_ID = 2001

    private const val CONTENT_INTENT_REQUEST_CODE = 2001

    /**
     * Posts the end-of-session notification. Returns true when it was posted;
     * false (logged, never thrown) when notifications are disabled/denied or a
     * transient failure occurred — in that case the sound still plays.
     */
    fun postSessionEnded(context: Context): Boolean {
        // The single OS gate: false when the user disabled notifications or
        // denied POST_NOTIFICATIONS (Android 13+) for the whole app.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled — Study Session Ended notification skipped (sound unaffected)")
            return false
        }
        ensureChannel(context)
        return try {
            val contentIntent = PendingIntent.getActivity(
                context,
                CONTENT_INTENT_REQUEST_CODE,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_brain)
                .setContentTitle(context.getString(R.string.study_session_ended_title))
                .setContentText(context.getString(R.string.study_session_ended_text))
                // Compact banner, swipe-away / system-dismissable, one-shot.
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to post Study Session Ended notification: ${t.message}", t)
            false
        }
    }

    /** Creates the Study Mode channel once (idempotent; used by all later posts). */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.study_session_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }
}