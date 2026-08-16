# ShortsCap — Phase 19 Security Audit & Hardening

**Status:** Completed (audit + controlled hardening pass — **August 16, 2026**).
**Scope:** OWASP MASVS used as the mobile security baseline; the FastAPI
backend and remote API were assessed separately.
**Honest disclaimer:** This document does **not** claim the project is "fully
secure". Production-grade security (real authentication, TLS/domain, WAF,
Play Integrity enforcement, secret management) is **deferred** and explicitly
listed below. This phase made the architecture *ready* for those controls and
removed the verified weaknesses that could be fixed safely now.

---

## Executive summary

The audit found **no CRITICAL** issues and **no secrets** anywhere in the
repository. The codebase was already in strong shape: user isolation is
applied consistently (the caller identity always comes from the dependency,
never the payload), every list endpoint bounds pagination, domain and enum
inputs are validated at the schema layer, sync operations are idempotent,
database access is fully parameterized (no string-built SQL), and database
errors are already masked from clients.

The fixes applied in this phase:

1. **Development identity fails closed** — `X-Dev-User-Id` is now disabled in
   production configuration (backend) and never sent by release Android
   builds (client), so the temporary dev identity cannot become an
   authentication bypass.
2. **Environment-aware CORS** with a wildcard guard (`"*"` rejected outside
   development) and a **configurable trusted-host** setting ready for
   deployment.
3. **Minimal API security headers** (`X-Content-Type-Options`, `X-Frame-Options`,
   `Referrer-Policy`) and **sanitized request logging** (method/path/status/
   duration only; DEBUG-only; never headers, bodies or query strings).
4. **Android network security config** — release builds block all cleartext
   traffic; the local-development HTTP exception exists only in the debug
   variant.
5. **Android backup disabled** (`allowBackup="false"`) so on-device usage
   data is not extractable via backup.
6. **R8 resource shrinking enabled** and a documented `proguard-rules.pro`
   (R8 is treated as *resilience*, not secrecy).
7. **Reproducible env template** (`.env.example`) documenting the new
   security settings.

---

## Findings

### CRITICAL

None.

### HIGH

| ID | Component | File | Finding |
| --- | --- | --- | --- |
| H-01 | Backend identity | `app/routers/deps.py`, `app/config.py` | The temporary development identity (`X-Dev-User-Id`) was accepted in **any** environment, including `APP_ENV=production`. Anyone who could reach a deployed instance could impersonate any user ID and read/write that user's data. |

- **Attack / impact:** full cross-user data access (settings, schedules,
  sessions, usage, events, websites, reports) in any deployment that forgot
  to remove the header.
- **Fix applied (safe now):** `DEV_IDENTITY_ENABLED` setting — when unset it
  derives from `APP_ENV` and **fails closed in production**; the dependency
  and `ensure_dev_user` now raise `403` when disabled. `Settings(...)` is
  unit-checked in `scripts/verify_security.py`.
- **Verification:** `verify_security.py` static checks
  (`dev identity DISABLED in production config`).
- **Deferred dependency:** real authentication — **Cognito is a required
  production dependency** (see below). This control is a boundary, not a
  replacement for auth.

### MEDIUM

| ID | Component | File | Finding |
| --- | --- | --- | --- |
| M-01 | Backend CORS | `app/main.py`, `app/config.py` | No CORS configuration existed at all. A future web client or browser-based tooling would have no policy, and a naive later addition could use `allow_origins=["*"]`. |
| M-02 | Backend host validation | `app/main.py`, `app/config.py` | No trusted-host / Host-header validation existed, leaving the door open to DNS-rebinding style issues in deployments. |
| M-03 | Android network | `app/src/main/res/xml/network_security_config.xml` (new), `app/src/main/AndroidManifest.xml` | Local development uses cleartext `http://10.0.2.2:8000` with no explicit network policy. Without a config, API 28+ blocks all cleartext (dev sync fails on-device) and a naive fix could have enabled cleartext globally for production too. |

**M-01 fix applied:** environment-aware `CORSMiddleware` from
`CORS_ALLOW_ORIGINS` (comma-separated, empty = none). A wildcard raises
`ValueError` at startup outside the development environment. Native Android
clients are unaffected (no `Origin` header).
**M-02 fix applied:** configurable `ALLOWED_HOSTS` wired to Starlette's
`TrustedHostMiddleware`; empty = not enforced yet (ready for deployment
without code changes). No future AWS domain names are hard-coded.
**M-03 fix applied:** `network_security_config.xml` — the **release** (main)
variant sets `cleartextTrafficPermitted="false"`; the **debug** variant
(`src/debug/res/xml/`) permits cleartext so the local dev endpoint keeps
working. The exception is isolated to debug builds and never ships in a
release APK. Staging/production base URLs remain HTTPS placeholders.

### LOW

| ID | Component | File | Finding |
| --- | --- | --- | --- |
| L-01 | Android privacy/storage | `app/src/main/AndroidManifest.xml` | `android:allowBackup="true"` allowed app data (usage history, settings, sync state) to be extracted via device backup/ADB. |
| L-02 | Android release hardening | `app/build.gradle.kts`, `app/proguard-rules.pro` (new) | R8 minification was on, but resource shrinking was off and there was no explicit keep-rules file to document the release hardening. |
| L-03 | Backend logging | `app/middleware/logging.py` (new) | Request logging was a placeholder; there was no sanitized access log for developers. |
| L-04 | Backend env template | `backend/.env.example` | The template was a redacted placeholder and did not document the security settings. |

**L-01 fix applied:** `android:allowBackup="false"`.
**L-02 fix applied:** `isShrinkResources = true`; minimal documented
`proguard-rules.pro` (keeps `BuildConfig`). R8 is treated as resilience, not
secrecy — decompilation is never fully prevented.
**L-03 fix applied:** sanitized `RequestLoggingMiddleware` — logs method,
path, status, duration only; never headers, bodies or query strings;
DEBUG-only.
**L-04 fix applied:** `.env.example` regenerated with placeholder values and
the Phase 19 keys (`DEV_IDENTITY_ENABLED`, `CORS_ALLOW_ORIGINS`,
`ALLOWED_HOSTS`).

### INFO (verified, no code change required or safe now)

| ID | Component | File | Finding / disposition |
| --- | --- | --- | --- |
| I-01 | Backend deps | `backend/requirements.txt` | Versions are unpinned. Installed set is current (FastAPI 0.141, Uvicorn 0.52, Pydantic 2.13, SQLAlchemy 2.0, Starlette 1.6). **Disposition:** pin at deployment; no upgrade needed now. |
| I-02 | Backend DB | `app/database.py` | `echo=settings.DEBUG` prints SQL in dev only; production is clean. **Disposition:** keep; documented. |
| I-03 | Backend pagination | `app/routers/study.py` | Study sessions/events lists are unbounded (no pagination) unlike monitoring/shorts/web/rank. Per-user, moderate volume. **Disposition:** defer client-coordinated pagination to avoid breaking the API contract; revisit pre-deployment. |
| I-04 | Backend events | services/repos | Event endpoints are append-only; replaying a sync is idempotent (per-day upserts), but re-submitting an event creates a duplicate row. **Disposition:** accepted product semantics; a client dedupe key can be added with the sync layer rework. |
| I-05 | Backend surface | `app/routers/auth.py`, `devices.py`, `notifications.py`, `sync.py`, `users.py` | Placeholder routers are **not mounted** in `app/main.py` — no attack surface. **Disposition:** verified. |
| I-06 | Android logging | `app/src/main/java/...` | Only 7 `Log.w` error-only calls (sound/notification failures); no tokens, headers, payloads or personal data logged. **Disposition:** verified clean. |
| I-07 | Android permissions | `app/src/main/AndroidManifest.xml` | Permissions are minimal and each has a documented purpose; only the launcher activity is exported; the Accessibility service is exported but protected by the signature-level `BIND_ACCESSIBILITY_SERVICE` permission; `MonitoringService` is not exported. **Disposition:** verified. |
| I-08 | Android storage | app | SharedPreferences only (preferences, HUD position/appearance, crash-reporter trace); no Room/DataStore, no tokens stored. Ordinary preferences do not require encryption (per scope). **Disposition:** verified. |
| I-09 | Android overlay | `app/.../hud` | Shorts HUD is presentation-only, checks `SYSTEM_ALERT_WINDOW` before showing, releases playback on hide, and never logs or transmits data. **Disposition:** verified. |
| I-10 | Android accessibility | `app/.../accessibility` | The service reads only package + window-class metadata, stores nothing, and never retrieves/logs/transmits screen content. **Disposition:** verified. |
| I-11 | Android signing | repo | No keystores, signing configs, or signing secrets in the repository; release currently builds with the default debug signing. **Disposition:** Play App Signing is planned for store distribution (documented below); keys must never enter Git. |
| I-12 | Android endpoints | `app/.../network/BackendConfig.kt` | Staging/production URLs are explicit HTTPS placeholders (`.example`); nothing routes to a real production host accidentally. **Disposition:** verified. |
| I-13 | Android crash aid | `app/.../CrashReporter.kt` | Temporary on-device crash-trace dialog persists stack traces in SharedPreferences (dev diagnostic). **Disposition:** remove once the crash it diagnoses is fixed; does not leave the app. |

---

## Security controls implemented (this phase)

| Control | Where | Verification |
| --- | --- | --- |
| Dev identity fails closed in production | `config.py` + `routers/deps.py` | `verify_security.py` static checks |
| Dev identity never sent by release builds | `BackendConfig.kt` + `HttpBackendApi.kt` (`BuildConfig.DEBUG` gate) | `verify_security.py` static checks |
| Environment-aware CORS, `"*"` banned outside dev | `main.py` + `config.py` | `verify_security.py` static + live (no ACAO for unconfigured origin) |
| Configurable trusted hosts | `main.py` (`TrustedHostMiddleware`) | startup wiring |
| Security headers (nosniff / frame / referrer) | `middleware/security.py` | `verify_security.py` live check |
| Sanitized access logging (DEBUG-only) | `middleware/logging.py` | code review |
| Android release blocks cleartext; dev exception isolated to debug | `res/xml/network_security_config.xml` (main + debug) | `verify_security.py` static check |
| Android backup disabled | `AndroidManifest.xml` | code review |
| R8 resource shrinking + documented keep rules | `build.gradle.kts` + `proguard-rules.pro` | `assembleRelease` |
| Repro env template with security keys | `.env.example` | code review |
| Security verification suite | `scripts/verify_security.py` | runs in regression |

## Security controls deferred

| Control | Reason | Where it will land |
| --- | --- | --- |
| Real authentication (Cognito) | Explicitly out of scope this phase | Replaces `X-Dev-User-Id` at `routers/deps.py` + `BackendConfig` |
| TLS / domain / HSTS | Deployment layer (proxy / load balancer), not app code | AWS deployment phase |
| Rate limiting | Infrastructure is better suited (API Gateway / WAF); a local in-process limiter would be fragile and provide false assurance | AWS WAF / API Gateway |
| Play Integrity enforcement | Requires Google Play project credentials + backend infrastructure | Tokens obtained on Android at app start; verified server-side before sensitive ops |
| Production secret management | No production secrets exist yet | AWS Secrets Manager at deployment |
| Requirements pinning | Deployment-time decision to avoid churn now | `requirements.txt` pin at deployment |
| Study list pagination | Requires client contract change | Coordinate with the sync rework |

## Required production dependencies

- **Cognito (REQUIRED):** until real authentication is implemented and wired
  at `app/routers/deps.py`, the development identity boundary is the only
  identity mechanism — production authentication is **not** secure yet and
  must not be claimed as such.
- **AWS (REQUIRED at deployment):** hosting, RDS, TLS/domain, WAF.
- **Play Integrity (REQUIRED for leaderboard/rank abuse resistance):**
  obtain Integrity tokens on Android (at startup and/or before high-value
  sync) and verify them server-side before account/device operations,
  high-value synchronization and rank/leaderboard participation.

## Remaining risks

- Until Cognito lands, any deployment must keep `APP_ENV != production`
  semantics correct (or explicitly set `DEV_IDENTITY_ENABLED=false`) — the
  verify script guards the default, not an explicit misconfiguration.
- Event endpoints remain replayable (accepted product semantics; see I-04).
- Study list endpoints are unbounded (see I-03).
- R8 is resilience, not secrecy; an attacker with the APK can still reverse
  the client.

## Verification status

- `scripts/verify_security.py` — static + live security checks (see the
  Phase 19 section of `backend/README.md` for the latest run totals).
- Full regression: all existing verify scripts (study / monitoring / shorts /
  web / reports / score / rank / sync contracts) continue to pass.
- Android: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and
  `:app:assembleRelease` build successfully.
- **Database schema:** unchanged. **Alembic migration:** none created.
- **AWS modified:** NO. **Cognito implemented:** NO.
