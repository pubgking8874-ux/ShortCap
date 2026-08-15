package com.shortscap.app.network

/**
 * BackendConfig — the single, centralized place for the backend base URL and
 * the temporary development identity (Phase 16 synchronization layer).
 *
 * Environment:
 *  - [LOCAL_DEV] is the default: the Android emulator reaches the Windows
 *    host machine via **10.0.2.2** (NOT 127.0.0.1 — inside the emulator that
 *    is the emulator itself). The FastAPI dev server runs on the host at
 *    port 8000 (`.venv\Scripts\python -m uvicorn app.main:app --reload`).
 *  - [STAGING] / [PRODUCTION] are reserved placeholders (HTTPS); a future
 *    build-flavor / runtime config flips [environment] before any call.
 *
 * TEMPORARY DEVELOPMENT IDENTITY (NOT PRODUCTION AUTH):
 * The backend identifies the caller with the `X-Dev-User-Id` header until
 * AWS Cognito replaces the identity boundary. Android sends the header
 * through [HttpBackendApi] only — never a fake login screen, no JWT, no OTP.
 * Cognito will replace [devUserId] + [DEV_USER_ID_HEADER] later without
 * touching the endpoints or syncers.
 */
object BackendConfig {

    /** Which backend the app talks to. */
    enum class Environment {
        /** Local FastAPI dev server on the host machine (emulator: 10.0.2.2). */
        LOCAL_DEV,
        /** Reserved — future staging deployment (HTTPS). */
        STAGING,
        /** Reserved — future production deployment (HTTPS). */
        PRODUCTION,
    }

    /** Active environment. Flip this once per app process (e.g. at startup). */
    @Volatile
    var environment: Environment = Environment.LOCAL_DEV

    /** Base URL for the active environment — no hardcoded URLs elsewhere. */
    val baseUrl: String
        get() = when (environment) {
            // 10.0.2.2 = the Windows host as seen FROM the Android emulator.
            Environment.LOCAL_DEV -> "http://10.0.2.2:8000"
            // Reserved: real deployments must use HTTPS.
            Environment.STAGING -> "https://staging.shortscap.example"
            Environment.PRODUCTION -> "https://api.shortscap.example"
        }

    /** HTTP timeouts (ms) for every backend call. */
    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 15_000

    /** Temporary development identity header — see the class docstring. */
    const val DEV_USER_ID_HEADER = "X-Dev-User-Id"

    /** The development user id sent with every request (Cognito replaces it). */
    @Volatile
    var devUserId: String = "1"
}
