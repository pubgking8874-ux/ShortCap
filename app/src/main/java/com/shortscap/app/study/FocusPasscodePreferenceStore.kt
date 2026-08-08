package com.shortscap.app.study

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * FocusPasscodePreferenceStore — local storage for the Focus Exit Passcode.
 *
 * SECURITY: the passcode is NEVER stored as plain text. A per-install random
 * salt is generated once and persisted, and only
 * SHA-256(salt + passcode) is stored; verification compares hashes in
 * constant time (no early-exit timing leak). The future backend replaces
 * this with a proper server-side KDF (bcrypt/argon2) behind the same
 * verify/save calls — no UI changes required.
 *
 * Mirrors the ThemePreferenceStore / StudyPreferenceStore pattern
 * (SharedPreferences). Only the hashed credential lives here; recovery and
 * OTP state live in [FocusPasscodeRepository].
 */
class FocusPasscodePreferenceStore(context: Context) {

    private val prefs = context.getSharedPreferences("focus_exit_passcode_prefs", Context.MODE_PRIVATE)

    /** True once a passcode has been created (never cleared by Reset All). */
    fun isPasscodeSet(): Boolean = prefs.contains(KEY_HASH)

    /** Hashes and stores a NEW passcode (the old one becomes invalid). */
    fun savePasscode(passcode: String) {
        prefs.edit()
            .putString(KEY_HASH, hash(passcode))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Constant-time comparison against the stored hash. */
    fun verifyPasscode(passcode: String): Boolean {
        val stored = prefs.getString(KEY_HASH, null) ?: return false
        return constantTimeEquals(stored, hash(passcode))
    }

    /** Removes the stored credential (not used by the current flow). */
    fun clearPasscode() {
        prefs.edit().remove(KEY_HASH).remove(KEY_UPDATED_AT).apply()
    }

    // ---- Hashing (SHA-256 + per-install random salt) ----

    private fun hash(passcode: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt() + passcode).toByteArray(Charsets.UTF_8))
        return bytes.toHex()
    }

    /** Random 16-byte salt, generated once per install and persisted. */
    private fun salt(): String {
        prefs.getString(KEY_SALT, null)?.let { return it }
        val randomBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = randomBytes.toHex()
        prefs.edit().putString(KEY_SALT, saltHex).apply()
        return saltHex
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_HASH = "passcode_hash"
        const val KEY_SALT = "passcode_salt"
        const val KEY_UPDATED_AT = "passcode_updated_at"
    }
}
