package com.shortscap.app.web.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle

/**
 * Minimal no-UI entry point for the local VPN: grants the Android VPN
 * consent once and starts/stops [LocalVpnService].
 *
 * Used by the functional verification and by any future UI wiring; it has no
 * layout and finishes immediately. Driven via intent extras:
 *
 * ```
 * adb shell am start -n com.shortscap.app/.web.vpn.VpnPermissionActivity \
 *        --ez start true   # start the local VPN (requests permission once)
 * adb shell am start -n com.shortscap.app/.web.vpn.VpnPermissionActivity \
 *        --ez start false  # stop the local VPN
 * ```
 *
 * Permission is only requested when it has not been granted yet —
 * [VpnService.prepare] returns null once granted, so a second start never
 * shows the consent dialog again.
 */
class VpnPermissionActivity : Activity() {

    companion object {
        private const val EXTRA_START = "start"
        private const val REQUEST_VPN_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = intent?.getBooleanExtra(EXTRA_START, true) ?: true
        if (!start) {
            VpnStateManager.stop(this)
            finish()
            return
        }

        val permission = VpnService.prepare(this)
        if (permission != null) {
            startActivityForResult(permission, REQUEST_VPN_PERMISSION)
        } else {
            launch()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN_PERMISSION) {
            if (resultCode == RESULT_OK) launch()
            finish()
        }
    }

    private fun launch() {
        VpnStateManager.start(this)
        finish()
    }
}
