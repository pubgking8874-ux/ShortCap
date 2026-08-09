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
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.shortscap.app.MainActivity
import com.shortscap.app.R
import com.shortscap.app.accessibility.AccessibilityServiceStatus
import com.shortscap.app.permissions.PermissionRepository

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
        isRunning = true
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
