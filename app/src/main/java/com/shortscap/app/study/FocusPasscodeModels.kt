package com.shortscap.app.study

/**
 * Focus Exit Passcode — Study Mode protection & recovery models.
 *
 * The Focus Exit Passcode is the ONLY way to manually end an active Study
 * Mode session before its countdown finishes. It lives entirely inside the
 * Study Mode feature (General section) and controls ONLY the ability to end
 * a session early — it never touches blocking settings, restriction
 * configuration, Monitoring, Activity/History data or normal authentication.
 *
 * Backend-ready: the models below are the shapes the future backend uses
 * (hashing/OTP happen server-side); today they are backed by the local
 * mock implementation in [FocusPasscodeRepository] / [FocusPasscodePreferenceStore].
 */

/** Recovery contact method chosen on the Focus Passcode Recovery screen. */
enum class FocusRecoveryMethod { EMAIL, MOBILE }

/**
 * Entry point for the Focus Passcode flow overlay — the SAME verification
 * and recovery screens are used from BOTH the Home page and
 * General → Study Mode, so there is exactly one passcode UI in the app.
 * [SETUP] is the first-time create flow; [VERIFY] gates ending an active
 * Study Mode session (or confirms the passcode from the Study Mode row).
 */
enum class FocusPasscodeEntry { SETUP, VERIFY }

/**
 * Masks a recovery contact for display so the UI "clearly indicates whether
 * the code was sent to email or mobile" without exposing the full value:
 *   EMAIL  "j••••@gmail.com"   MOBILE  "•••• 7890"
 */
fun maskContact(method: FocusRecoveryMethod, contact: String): String = when (method) {
    FocusRecoveryMethod.EMAIL -> {
        val parts = contact.split("@")
        if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
            val local = parts[0]
            val dots = "•".repeat((local.length - 2).coerceIn(1, 4))
            "${local.first()}$dots${local.last()}@${parts[1]}"
        } else contact
    }
    FocusRecoveryMethod.MOBILE ->
        if (contact.length > 4) "•••• ${contact.takeLast(4)}" else "••••"
}

/** One selectable dial-code country for the recovery mobile verification. */
data class FocusCountry(
    val name: String,
    val dialCode: String,
    val flag: String,
    val maxNumberDigits: Int,
)

/** Small dial-code catalog (mirrors the auth mobile catalog; study-local). */
val FocusSupportedCountries = listOf(
    FocusCountry("India", "+91", "🇮🇳", 10),
    FocusCountry("United States", "+1", "🇺🇸", 10),
    FocusCountry("United Kingdom", "+44", "🇬🇧", 10),
    FocusCountry("Canada", "+1", "🇨🇦", 10),
    FocusCountry("Australia", "+61", "🇦🇺", 9),
    FocusCountry("UAE", "+971", "🇦🇪", 9),
)
