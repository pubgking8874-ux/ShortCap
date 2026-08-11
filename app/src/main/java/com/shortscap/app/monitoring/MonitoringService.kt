package com.shortscap.app.monitoring

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.shortscap.app.MainActivity
import com.shortscap.app.R
import com.shortscap.app.accessibility.AccessibilityServiceStatus
import com.shortscap.app.permissions.PermissionRepository
import com.shortscap.app.study.BreakCycle
import com.shortscap.app.study.StudySessionAlerts

/**
 * MonitoringService — the foreground service behind ShortsCap's continuous
 * monitoring.
 *
 * It keeps the monitoring PROCESS alive after the user leaves ShortsCap or
 * removes it from Recents (subject to Android/OEM restrictions), so
 * monitoring never depends on MainActivity staying open. The actual
 * monitoring signal still comes from the Accessibility Service (a
 * system-managed component); this service keeps the process resident and
 * shows the persistent notification.
 *
 * Lifecycle / reliability:
 *  - [onStartCommand] returns START_STICKY, so the system restarts the
 *    service after killing it; the restart self-validates its prerequisites
 *    and stops itself if monitoring is off or a required permission was
 *    revoked.
 *  - [onTaskRemoved] (swiped from Recents) does NOT stop monitoring — it
 *    schedules a quick AlarmManager restart so the service comes back.
 *  - The "monitoring enabled" flag is persisted, so a sticky restart can
 *    decide correctly without the UI being open.
 *
 * The service starts ONLY when the required monitoring permissions are
 * granted ([start] checks [canRun]); it never pretends to run otherwise.
 */
class MonitoringService : Service() {

    /**
     * Background Study Session watch — while this foreground service is
     * running (Study Mode forces monitoring on for the whole session), it
     * checks the persisted active session on a light cadence. When the
     * session's wall-clock end time has passed, it delivers the "Study
     * Session Ended" alert exactly once (sound + Android notification) via the
     * persisted-idempotent StudySessionAlerts — so the end event is real and
     * background-capable even when ShortsCap's UI is never opened (e.g. the
     * user is in YouTube / Chrome / Instagram / WhatsApp, or the process was
     * restarted START_STICKY with no Activity at all). The ViewModel still
     * owns restriction-state restoration and session clearing when the app
     * next comes forward; the flag prevents any duplicate delivery.
     */
    private val sessionEndWatcher = object : Runnable {
        override fun run() {
            if (isRunning) {
                // Background break cycle: starts/ends breaks (BREAK_START /
                // BREAK_END sounds) against the same persisted state as the
                // ViewModel ticker, so the sounds fire exactly once even with
                // ShortsCap's UI never open.
                BreakCycle.check(this@MonitoringService)
                StudySessionAlerts.fireEndAlert(this@MonitoringService)
                handler.postDelayed(this, SESSION_END_CHECK_MILLIS)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
        isRunning = true
        handler.removeCallbacks(sessionEndWatcher)
        handler.post(sessionEndWatcher)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Self-healing guard: if monitoring was switched off, or a required
        // permission was revoked while we were stopped, exit cleanly instead
        // of running a meaningless foreground service.
        if (!canRun(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isRunning) {
            isRunning = true
            startForegroundCompat()
            // A sticky/restart re-entry re-arms the watcher too.
            handler.removeCallbacks(sessionEndWatcher)
            handler.post(sessionEndWatcher)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Removing ShortsCap from Recents must NOT intentionally stop
        // monitoring — schedule a quick restart (if still required).
        if (canRun(this)) scheduleRestart()
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(sessionEndWatcher)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.monitoring_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brain)
            .setContentTitle(getString(R.string.monitoring_notification_title))
            .setContentText(getString(R.string.monitoring_notification_text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent())
            .build()

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleRestart() {
        val restartIntent = Intent(this, MonitoringService::class.java)
        val pending = PendingIntent.getService(
            this,
            1,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarm = getSystemService(AlarmManager::class.java)
        // Inexact wake-up (no SCHEDULE_EXACT_ALARM needed) — good enough for a
        // monitoring restart; never throws, so the app can't crash here.
        runCatching {
            alarm.set(AlarmManager.RTC, System.currentTimeMillis() + 1_000L, pending)
        }
    }

    companion object {
        const val CHANNEL_ID = "monitoring"
        const val NOTIFICATION_ID = 1

        /** Cadence of the background Study Session watch — 1s keeps the end
         *  alert prompt without being a hot loop. */
        const val SESSION_END_CHECK_MILLIS = 1_000L

        private const val PREFS_NAME = "monitoring_service_prefs"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"

        /** True while this process' service is in the foreground. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** Persists whether monitoring is switched on (survives process death). */
        fun saveMonitoringEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MONITORING_ENABLED, enabled)
                .apply()
        }

        /** The persisted \"monitoring is on\" flag (default: on). */
        fun isMonitoringEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MONITORING_ENABLED, true)

        /**
         * Starts the service — but ONLY when the required monitoring
         * permissions are granted. A missing prerequisite means monitoring
         * cannot run, so no foreground service is created (nothing faked).
         */
        fun start(context: Context) {
            if (!canRun(context)) return
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
            }
        }

        /** Stops the service (monitoring switched off / permission revoked). */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, MonitoringService::class.java)) }
        }

        /** The prerequisites for the foreground monitoring service. */
        fun canRun(context: Context): Boolean =
            isMonitoringEnabled(context) &&
                PermissionRepository.isUsageAccessGranted(context) &&
                AccessibilityServiceStatus.isEnabled(context)
    }
}
