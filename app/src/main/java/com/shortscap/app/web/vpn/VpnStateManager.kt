package com.shortscap.app.web.vpn

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService

/**
 * VpnStateManager — the lifecycle + permission seam for [LocalVpnService].
 *
 * Handles VPN start / stop / already-running / permission state / restart,
 * without ever re-prompting for VPN permission once it has been granted
 * ([VpnService.prepare] returns null when granted). The user's intent to run
 * the VPN is persisted in SharedPreferences (the app's existing local
 * persistence pattern) so a future UI can restore/reflect it after a restart.
 *
 * This is strictly the Web Blocking Engine's manager — it does not touch
 * Shorts Control, Shorts Detection, the HUD, Screen Activity, app detection
 * or the Accessibility service.
 */
object VpnStateManager {

    enum class StartResult {
        /** The VPN was started. */
        STARTED,

        /** The VPN is already running — nothing was restarted. */
        ALREADY_RUNNING,

        /** VPN permission is required first (see [permissionIntent]). */
        NEEDS_PERMISSION,
    }

    private const val PREFS_NAME = "shortscap_vpn"
    private const val KEY_ENABLED = "vpn_enabled"

    /**
     * The system consent intent when VPN permission is still needed, or null
     * when permission has already been granted (never re-requested then).
     */
    fun permissionIntent(context: Context): Intent? = VpnService.prepare(context)

    /**
     * Starts the local VPN. Returns [StartResult.NEEDS_PERMISSION] when the
     * system consent is required first; the caller launches
     * [permissionIntent] and calls this again on RESULT_OK.
     */
    fun start(context: Context): StartResult {
        if (permissionIntent(context) != null) return StartResult.NEEDS_PERMISSION
        if (LocalVpnService.isRunning) return StartResult.ALREADY_RUNNING
        context.startService(Intent(context, LocalVpnService::class.java))
        prefs(context).edit().putBoolean(KEY_ENABLED, true).apply()
        return StartResult.STARTED
    }

    /** Stops the local VPN cleanly (no-op when it is not running). */
    fun stop(context: Context) {
        context.stopService(Intent(context, LocalVpnService::class.java))
        prefs(context).edit().putBoolean(KEY_ENABLED, false).apply()
    }

    /** True while the tunnel is established. */
    fun isRunning(): Boolean = LocalVpnService.isRunning

    /** Whether the user last left the VPN enabled (persisted intent). */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /**
     * Pushes the latest [com.shortscap.app.web.domain.BlockedDomainRepository]
     * rules into the running filter (no-op effect when the VPN is off —
     * rules are reloaded at next start).
     */
    suspend fun refreshBlockedDomains(context: Context) {
        LocalVpnService.syncBlockedDomains(context)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
