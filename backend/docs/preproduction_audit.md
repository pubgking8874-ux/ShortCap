# ShortsCap — Phase 20 Final Pre-Production Audit & Gap Analysis

**Date:** August 16, 2026 · **Type:** audit-first, evidence-based · **Code changed: NONE**
**Scope:** complete system — Android, FastAPI backend, MySQL/SQLAlchemy/Alembic, sync,
Shorts HUD, security hardening, release configuration.

This audit compared **documented behavior vs actual implementation** by inspecting
code/tests/configuration and running every available automated check. Nothing was
modified; findings are reported, not patched. Anything without direct evidence is
marked **NOT VERIFIED** rather than assumed.

---

## Executive summary

The ShortsCap codebase is in **strong shape for an internal pre-production stage**:

- The **backend is complete and thoroughly verified** for its data-layer scope:
  all 9 verification scripts pass (556 checks), Alembic is consistent
  (current = head = `657ba9f4d4f8`), all 25 tables exist, user isolation /
  validation / idempotency / error masking are in place, and Phase 19 security
  controls hold (29/29).
- The **Android app builds cleanly** in debug and release (R8 + resource
  shrinking), 40/40 unit tests pass, and the UI/settings/HUD surface is
  implemented.
- **The critical gap is physical validation and two Android runtime halves:**
  real app-usage data collection and real-time web enforcement are **not
  implemented** (explicit, honest seams in the code — the app does not fake
  them). All device-dependent behavior (HUD overlay, detection, notifications,
  background service, video playback, permission flows) is **implemented but
  NOT physically tested** — no real-device run has been performed.
- **No P0 release blocker** was found from static/build evidence. Two **P1**
  items are real and should be fixed before a release candidate (see Blockers).

---

## 1. Phase-by-phase status (documented vs actual)

| Phase | Status | Evidence |
| --- | --- | --- |
| 2 — FastAPI + Uvicorn | **COMPLETE** | `app.main:app` starts; `/`, `/health/db`, `/docs` verified live |
| 3 — MySQL + DB connection | **COMPLETE** | `/health/db` connected; direct `SELECT 1` + `SHOW TABLES` via engine |
| 4 — SQLAlchemy models | **COMPLETE** | 24 models → 24 tables present (verified by table list) |
| 5 — Alembic + tables | **COMPLETE** | `alembic current` = `alembic heads` = `657ba9f4d4f8`; no pending migrations |
| 6 — user_settings | **COMPLETE** | `GET/PUT /settings` verified (sync_contracts 86/86) |
| 7 — Settings data layer | **COMPLETE** | Backend endpoints verified; Android Settings UI + sync client implemented |
| 8 — Study data layer | **COMPLETE** (backend); **NOT VERIFIED** (device) | verify_study 56/56; Android local timer (`StudyModels` clock-derived `remainingMillis`) implemented; no physical session run |
| 9 — Monitoring data layer | **PARTIAL** | Backend 55/55; **Android real app-usage collection NOT IMPLEMENTED** (no `UsageStatsManager`; Activity screen uses mock data; the Accessibility service forwards foreground metadata only) |
| 10 — Cross-platform Shorts architecture | **COMPLETE** (architecture) | Registry/adapters/aggregator/budget unit-tested (ShortsMonitoringPipelineTest); **real detection covers only YouTube Shorts (window-class); all other platforms report UNKNOWN — NOT device-verified** |
| 11 — Shorts integration/schema | **COMPLETE** | Migration `657ba9f4d4f8` applied; verify_shorts 67/67 |
| 12 — Web data layer | **PARTIAL** | Backend 72/72; **Android real-time enforcement NOT IMPLEMENTED** (`PlaceholderBlockingEngine.isAvailable = false`; web UI manages a local rule list only) |
| 13 — Reports / Insights | **COMPLETE** | verify_reports 61/61 with direct-SQL verification; aggregations over stored rows |
| 14A — Score specification | **COMPLETE** | `docs/your_score_spec.md` exists; weights 40/25/20/10/5 |
| 14B — Score engine | **COMPLETE** | verify_score 83/83; deterministic, normalized 0–100, component breakdown |
| 15A — Rank specification | **COMPLETE** | `docs/rank_leaderboard_spec.md` exists |
| 15B — Rank engine | **COMPLETE** | verify_rank 47/47; competition ranking, tie-break, opt-in, privacy fields |
| 16 — Android ↔ Backend Sync | **PARTIAL** | Sync client (queue/manager/syncers/read clients) contract-verified (86/86); **queue is in-memory (lost on process death)**; backend `/sync` router is a placeholder — contracts run against the data-layer endpoints |
| 19 — Security Hardening | **COMPLETE** | verify_security 29/29; findings from Phase 19 re-verified (see §7) |
| Post-16 — Shorts HUD | **COMPLETE** (implementation); **NOT VERIFIED** (device) | See §2 |

## 2. Shorts HUD audit (post-Phase-16 work)

| Item | Status | Evidence |
| --- | --- | --- |
| Appearance setting (Settings → Appearance → Shorts HUD) | COMPLETE | `ShortsHudScreen` — exactly **Brain / Counter / ShortsCap**, radio selection, persisted via `ShortsHudSettingsStore` |
| Brain icon correctness | COMPLETE | `res/drawable/ic_brain_option.xml` (clean brain silhouette) used by the Brain option; `ic_brain.xml` unchanged (notification icon) |
| Brain / Counter / ShortsCap modes | COMPLETE | `ShortsHudContent` — video chip / `127 / 200` counter / logo chip |
| Default visibility | COMPLETE | Overlay shows only when `ShortFormSurfaceState` non-null (isShortForm + confidence ≥ 0.5); hidden otherwise |
| Overlay permission | COMPLETE | `Settings.canDrawOverlays` checked before every show; fails gracefully; no crash on revocation |
| Draggable + position persistence | COMPLETE | `ShortsHudOverlayManager` + normalized (0..1) storage; unit-tested (ShortsHudLogicTest) |
| Count / limit display + global count | COMPLETE | Global `ShortsBudgetTracker` total; limit from store (default 200); ratio-based brain states |
| Theme handling | COMPLETE | Reads `LocalScColors` via `ShortsCapTheme` in the overlay; videos never recolored |
| Lifecycle/media cleanup | COMPLETE | `BrainVideoView.release()` on dispose (HUD hidden / mode change); single player at a time; no network |
| Brain asset loading (4 final videos, exact order) | COMPLETE | `app/src/main/assets/shorts_brain/brain_1_healthy.mp4.mp4` (HEALTHY) → `brain_2_tired` → `brain_3_near_limit` → `brain_4_limit_reached` (LIMIT_REACHED); mapped in `BrainVideoAssets` (unit-tested); files copied unchanged from the user's `shorts_brain` folder |
| Real-device overlay rendering / video playback | **NOT VERIFIED** | No device/emulator run performed (see Real-Device Readiness) |

## 3. Automated test audit (all scripts discovered and run)

Backend (against a live uvicorn server + live MySQL, per the project's script contract):

| Script | Result |
| --- | --- |
| `scripts/verify_study.py` | 56/56 PASS |
| `scripts/verify_monitoring.py` | 55/55 PASS |
| `scripts/verify_shorts.py` | 67/67 PASS |
| `scripts/verify_web.py` | 72/72 PASS |
| `scripts/verify_reports.py` | 61/61 PASS |
| `scripts/verify_score.py` | 83/83 PASS |
| `scripts/verify_rank.py` | 47/47 PASS |
| `scripts/verify_sync_contracts.py` | 86/86 PASS |
| `scripts/verify_security.py` | 29/29 PASS |
| **Total** | **556 / 556 PASS, 0 FAIL, 0 SKIP** |

Android unit tests: **40/40 PASS** (`SyncManagerTest`, `ShortsMonitoringPipelineTest`,
`ShortsHudLogicTest` — 17 HUD tests incl. brain-state thresholds and asset mapping).

**Coverage gaps (MISSING VERIFICATION COVERAGE):**
- No Android tests for Study timer logic, Monitoring service lifecycle, web
  domain list flows, or HUD overlay rendering (those require instrumented/device
  tests — `androidTest` exists as a dependency but no instrumented tests are
  written).
- No device/emulator runs at all (see §Real-Device Readiness).

## 4. Android build audit

| Check | Result |
| --- | --- |
| `:app:compileDebugKotlin` | PASS |
| `:app:testDebugUnitTest` | PASS (40/40) |
| `:app:assembleRelease` | PASS — R8 minification + resource shrinking active (`mapping.txt`, `resources.txt` present); unsigned APK 34.9 MB |
| `:app:lintDebug` | **FAILS — 2 errors, 88 warnings, 8 hints** (pre-existing; see Blockers P1-1 and P2) |
| Debug-only dev identity in release | Absent — `X-Dev-User-Id` gated on `BuildConfig.DEBUG` (verified in `BackendConfig`/`HttpBackendApi`) |
| Debug logging in release | Clean — 7 `Log.w` error-only calls, no secrets/payloads (Phase 19 re-verified) |
| Test endpoints in release | None — no test routes/activities; release network config blocks cleartext |

**Lint errors (both pre-existing, unrelated to recent phases):**
1. `NewApi` — `java.time.LocalDate.ofInstant` in `SyncCoordinator.kt:65` requires **API 34** (minSdk 26) → **latent `NoSuchMethodError` crash on API 26–33 devices when the Shorts local store is drained.** Real bug — see P1-1.
2. `HighAppVersionCode` — `versionCode = 2026072801` heuristic (value is legal, < 2.1B) — cosmetic (P3).

## 5. Backend audit

- Startup/imports: PASS (all routers import; uvicorn boots cleanly with Phase 19 middleware).
- `/health/db`: PASS (connected). `/docs`: PASS (Swagger served).
- Router registration: settings, study, monitoring, shorts, web, reports, score, rank mounted. `auth/users/devices/notifications/sync` routers are **placeholders, not mounted** (no attack surface).
- Configuration loading: pydantic-settings from `.env`; no hardcoded credentials.
- Security regression (Phase 19): all 29 checks pass — dev identity fail-closed, CORS guard, trusted hosts, headers, sanitized logging, error masking, user isolation, idempotent sync.

## 6. Database audit (inspect-only)

- Connection: PASS (engine + live MySQL).
- Alembic `current` = `heads` = **`657ba9f4d4f8`** — consistent, no pending migrations, no drift.
- Tables: **25** (24 approved + `alembic_version`) — exactly as documented.
- No destructive migration pending; no schema changes made (this phase or Phase 19).
- SQLAlchemy models are the source of truth; metadata consistent with the applied migration.

## 7. Security regression (Phase 19 re-check)

All Phase 19 findings still hold: secret scan clean, dev identity disabled in
production/release, `BuildConfig.DEBUG` gate present, release cleartext blocked
(debug-only exception isolated), R8 active, storage = SharedPreferences only,
logs sanitized, CORS env-aware, trusted hosts configurable, headers present,
user isolation verified, no error leakage, permissions/overlay/accessibility
audits clean, dependencies current. AWS WAF/Cognito remain deferred.

## 8. Configuration audit

| Source | Value | Classification |
| --- | --- | --- |
| Android `BackendConfig.LOCAL_DEV` (`http://10.0.2.2:8000`) | hardcoded dev host | **DEV ONLY** |
| Android `BackendConfig.STAGING/PRODUCTION` (`https://*.shortscap.example`) | placeholders | **STAGING READY** (config exists; endpoint not real) / **PRODUCTION READY: UNKNOWN** (no real endpoint) |
| Android `devIdentityEnabled = BuildConfig.DEBUG` | — | **DEV ONLY** |
| Backend `.env` (`APP_ENV=development`, `DEBUG=true`) | — | **DEV ONLY** |
| `DEV_IDENTITY_ENABLED` (unset → derived) | — | DEV ONLY (fails closed in production) |
| `CORS_ALLOW_ORIGINS` / `ALLOWED_HOSTS` (empty) | — | STAGING READY (documented, unenforced) |
| Secrets | none anywhere | — |

## 9. Real-device readiness

**STATUS: No real-device or emulator run has ever been performed.** The
following classification is based on implementation evidence, not hardware
simulation — nothing below is claimed device-verified.

| Feature | Classification |
| --- | --- |
| First launch / auth flow (mock) | READY FOR REAL DEVICE (needs device test) |
| Permissions (usage/accessibility/overlay/notifications/battery) | READY FOR REAL DEVICE (needs device test) |
| Study (timer, breaks, end alert) | NEEDS REAL DEVICE TEST |
| Monitoring (foreground service, START_STICKY, restart) | NEEDS REAL DEVICE TEST |
| Monitoring (real per-app usage collection) | **NOT IMPLEMENTED** |
| Shorts detection (YouTube window-class) | NEEDS REAL DEVICE TEST |
| Shorts detection (Instagram/TikTok/Snapchat/etc.) | **NOT IMPLEMENTED** (all report UNKNOWN) |
| Shorts counting (session-level, 3–5s rule) | NEEDS REAL DEVICE TEST |
| Shorts HUD (overlay, drag, video playback) | NEEDS REAL DEVICE TEST |
| Web blocking (rule list UI) | READY FOR REAL DEVICE |
| Web real-time DNS/browser enforcement | **NOT IMPLEMENTED** |
| Offline sync (in-memory queue) | NEEDS REAL DEVICE TEST; **does not survive restart** |
| Reports / Score / Rank (read clients) | NEEDS REAL DEVICE TEST (backend verified) |
| Notifications (study end, break sounds) | NEEDS REAL DEVICE TEST |
| App restart / device reboot recovery | PARTIAL — persisted flags survive; in-memory sync queue + Shorts local store are lost |

## 10. Pre-production blockers

**P0 — must fix before real-device test:** none identified from static/build
evidence. (First real-device test may surface P0s — permissions, overlay
rendering, crash-on-launch — which cannot be proven without running the app.)

**P1 — must fix before release candidate:**
- **P1-1 (crash):** `SyncCoordinator.kt:65` uses `java.time.LocalDate.ofInstant`
  (API 34) at minSdk 26 → `NoSuchMethodError` on API 26–33 devices when the
  Shorts store is drained. Fix: replace with `Instant.atZone(...).toLocalDate()`
  (API 26-safe) — small, safe, no behavior change.
- **P1-2 (data loss):** `SyncQueue` and `ShortsLocalStore` are **in-memory** —
  records captured offline are lost on process death, contradicting the
  offline-first contract (Phase 16 §14). Fix: persist the queue (SharedPreferences
  or Room) via the documented seam.
- **P1-3 (validation):** no physical run of the core flows on any device/emulator
  (first launch, permissions, HUD overlay, detection, notifications). This is a
  process blocker for release-candidate confidence, not a code defect.

**P2 — can wait until post-RC:** 88 lint warnings + 8 hints cleanup; lint
baseline; instrumented (`androidTest`) coverage for Study/monitoring/web/HUD;
real app-usage collection; real web enforcement engine (each is a feature, not a
defect fix).

**P3 — future enhancement:** `HighAppVersionCode` lint heuristic; expanded
platform detection; per-short counting via richer events; Room/DataStore for all
local stores; backup/restore; Play Integrity enforcement; rate limiting.

## 11. What is still missing

1. **Not implemented:** real Android app-usage collection (`UsageStatsManager`);
   real-time web/DNS enforcement (`PlaceholderBlockingEngine`); dedicated backend
   `/sync` router (auth/users/devices/notifications/sync routers are placeholders);
   Cognito auth; AWS deployment; Play Integrity; durable sync queue.
2. **Implemented but not physically tested:** every device-dependent flow
   (HUD overlay + video, detection, counting, notifications, background service,
   permission flows, study session end in background).
3. **Partially implemented:** monitoring (backend complete, Android data source
   missing); web (list CRUD complete, enforcement missing); sync (client complete,
   durable queue missing).
4. **AWS-dependent:** deployment, RDS, TLS/domain, WAF/rate limiting, secrets
   management.
5. **Cognito-dependent:** real authentication (a **required** production
   dependency — production auth must not be claimed secure until it lands).
6. **Production-security-dependent:** Play Integrity verification, rate limiting.
7. **Optional future improvements:** platform detection expansion, per-short
   counts, Room persistence, richer settings sync, instrumented test suite.

## 12. Recommended order of next work

1. Fix **P1-1** (API-34 `LocalDate.ofInstant` — one-line, safe).
2. Fix **P1-2** (persistent sync queue via the documented seam).
3. **First real-device/emulator pass** (debug build): first launch, permissions,
   Study, HUD, notifications, background behavior — record and fix what breaks.
4. Instrumented tests (`androidTest`) for the core flows to lock in device behavior.
5. Post-RC: lint cleanup, then features (usage collection, web enforcement,
   durable stores).
6. Deployment phase: AWS + Cognito (required before any production claim).

## 13. Release readiness scorecard

| Area | Score |
| --- | --- |
| Architecture | PASS |
| Backend | PASS |
| Database | PASS |
| Android (build/UI/settings) | PASS |
| Settings | PASS |
| Study | PARTIAL (backend complete; device test pending) |
| Monitoring | PARTIAL (backend complete; Android collection not implemented) |
| Shorts | PARTIAL (architecture complete; detection limited to YouTube, device test pending) |
| Web | PARTIAL (backend + list UI complete; enforcement not implemented) |
| Reports | PASS (backend) |
| Score | PASS |
| Rank | PASS |
| Sync | PARTIAL (client + contracts pass; queue not durable, no device test) |
| Security | PASS (Phase 19 verified) |
| Documentation | PARTIAL (minor discrepancies — see below) |
| Real-device readiness | **NOT VERIFIED** (no device run) |

**Documentation discrepancies (report only, not rewritten):**
- backend/README Phase 16 wording around "synchronization" implies a backend
  sync surface; the dedicated `/sync` router is a placeholder — the verified
  contracts run against the settings/study/monitoring/shorts/web data endpoints.
- READMEs describe Monitoring and Web blocking as implemented architecture;
  the Android data-collection and enforcement halves are honest seams
  (`MonitoringEventHub` funnel only; `PlaceholderBlockingEngine.isAvailable = false`).
  No README claims real device verification.

## 14. Final assessment

ShortsCap is **backend-complete, Android-UI-complete, and release-buildable**,
with a clean security posture, but it is **not production-ready and not yet
real-device-ready** in the strict sense: the highest-risk remaining work is
physical device validation plus the two missing Android runtime halves
(app-usage collection, web enforcement) and durable sync storage. The honest
label is **"ready for the first controlled real-device test pass"**, with P1-1
and P1-2 fixed first. Production deployment remains gated on AWS + Cognito.

---

*Phase 20 audit · no code changed · no database changed · no migration created ·
AWS/Cognito untouched · STOP after audit.*
