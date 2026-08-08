package com.shortscap.app.study

/**
 * FocusPasscodeRepository — backend seam for the Exit Passcode
 * recovery flow (mirrors the SettingsRepository / StudyRepository pattern).
 *
 * Today this holds the LOCAL MOCK OTP implementation (in-memory, per-flow):
 * requestOtp() generates a random 6-digit code that the UI surfaces as a
 * "Demo code" line so the flow stays fully testable without a backend. When
 * the backend connects, these calls become:
 *
 *   requestOtp  → POST /focus-passcode/otp/request  { method, contact }
 *                 (backend generates the code and SENDS it via email/SMS —
 *                  it must never return the code to the client)
 *   resendOtp   → POST /focus-passcode/otp/resend
 *   verifyOtp   → POST /focus-passcode/otp/verify  { code }
 *
 * Swapping the mock for the real API requires NO UI changes — the screens
 * only call requestOtp / resendOtp / verifyOtp.
 *
 * Security rules honored from day one (no fake permanent OTP):
 *  - codes are random 6-digit values, single-use, valid 5 minutes;
 *  - a wrong code keeps the recovery flow active (no lockout UI lies);
 *  - the passcode itself is never stored here — see
 *    [FocusPasscodePreferenceStore] (salted hash only).
 */
class FocusPasscodeRepository {

    private var pendingOtp: String? = null
    private var otpExpiresAt: Long = 0L

    /** 5-minute validity window for every generated code. */
    private val otpTtlMillis = 5 * 60_000L

    /**
     * Requests a code for [method] + [contact]. Returns the generated code
     * ONLY for the local mock (the UI shows it as a demo line); the real
     * backend sends it over email/SMS instead.
     */
    fun requestOtp(method: FocusRecoveryMethod, contact: String): String {
        val code = (100_000..999_999).random().toString()
        pendingOtp = code
        otpExpiresAt = System.currentTimeMillis() + otpTtlMillis
        // Future backend: POST /focus-passcode/otp/request {method, contact}
        // — never return the code to the client in production.
        return code
    }

    /** Sends the SAME pending code again with a fresh expiry (mock). */
    fun resendOtp(): String? {
        val code = pendingOtp ?: return null
        otpExpiresAt = System.currentTimeMillis() + otpTtlMillis
        return code
    }

    /** True only for the currently pending, unexpired code. */
    fun verifyOtp(code: String): Boolean =
        pendingOtp != null && System.currentTimeMillis() < otpExpiresAt && code == pendingOtp

    /** Invalidates the pending code (after success / flow exit). */
    fun clearOtp() {
        pendingOtp = null
        otpExpiresAt = 0L
    }
}
