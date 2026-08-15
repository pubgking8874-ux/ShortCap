# ShortsCap Backend

Server backend for the ShortsCap app (Python + FastAPI + SQLAlchemy + MySQL).

> **Status:** Phase 2 (running FastAPI server) + Phase 3 (database foundation +
> environment configuration) + Phase 4 (approved SQLAlchemy model suite — 24
> models) + Phase 5 (Alembic migration applied — the 24 MySQL tables now
> exist) + Phase 6 (settings data layer: repository + service + schemas +
> GET/PUT `/settings` API with a temporary dev identity) + Phase 7 (settings
> backend extended to monitoring / shorts / notifications / leaderboard /
> permissions) + Phase 8 (study data layer: study schedule / session / break
> / event APIs on the existing approved tables) + Phase 9 (monitoring data
> layer: app usage sync / monitoring events / summary on the existing
> approved tables) + Phase 10 (shorts data layer: shorts usage sync /
> shorts events / shorts summary on the existing approved tables) +
> Phase 11A (shorts usage schema update: `platform` + `surface` columns,
> new idempotency key, Alembic migration `657ba9f4d4f8`) + Phase 11B
> (Android: cross-platform Shorts detection integrated with the existing
> monitoring pipeline — registry → adapters → aggregator → global budget →
> local store) + Phase 12 (web data layer: blocked-website CRUD with domain
> normalization, website events, web summary on the existing approved
> tables) + Phase 13 (reporting / insights layer: read-only daily / weekly /
> monthly reports over existing historical data with previous-period
> comparison) + Phase 14A (Your Score specification & validation) +
> Phase 14B (Your Score engine — read-only `GET /score/daily|weekly|monthly`
> implementing the approved spec exactly) + Phase 15A (Rank / leaderboard
> specification & validation — ranking method, eligibility, tie-breaker,
> rank change designed and simulated) + Phase 15B (Rank / Leaderboard engine
> — read-only `GET /rank/weekly|monthly` implementing the approved spec
> exactly, consuming the Score Engine as the only score source; the board
> stays DYNAMIC, `leaderboard_scores` is not written) + Phase 16 (Android ↔
> backend synchronization: network layer, offline-first sync queue with
> retry/dedupe, settings/study/monitoring/shorts/web syncers, read-only
> Reports/Score/Rank clients, temporary dev identity; Android remains the
> real-time authority — study timer, monitoring, Shorts detection, web
> blocking). Auth, OAuth, and the
> remaining routers are implemented in later phases, one at a time.

## Reserved technology stack

- **Python** — language (3.14)
- **FastAPI** — API framework
- **SQLAlchemy 2.x** — ORM
- **pydantic-settings / python-dotenv** — env-driven configuration
- **Alembic** — database migrations (Phase 5: configured, initial migration applied)
- **MySQL** — database (local dev MySQL 8.0.43; AWS RDS for production)
- **PyMySQL** — MySQL driver

## Quick start

```powershell
cd backend
.venv\Scripts\python -m pip install -r requirements.txt
Copy-Item .env.example .env   # or edit the existing .env
# Set DB_PASSWORD in .env to the local MySQL root password (never committed)
.venv\Scripts\python -m uvicorn app.main:app --reload

# Database migrations (Phase 5):
.venv\Scripts\python -m alembic upgrade head   # apply pending migrations
.venv\Scripts\python -m alembic current        # show applied revision
```

Open <http://127.0.0.1:8000/> and <http://127.0.0.1:8000/docs>.

## Database health check

HTTP endpoint (does not expose any credentials):

```text
GET /health/db
```

- Configured + reachable: `{"status": "connected", "database": "shortscap_db"}`
- Not reachable: HTTP 503 `{"status": "not_connected", "database": "shortscap_db"}`

Script variant (real connection check, honest result — never fake success):

```powershell
.venv\Scripts\python -m scripts.check_db
```

With a blank or invalid `DB_PASSWORD` it reports `not_configured` — expected
until local MySQL / AWS RDS credentials are set in `.env`.

## Environment configuration

- `backend/.env` — local environment (git-ignored). Holds the database config:
  `DB_HOST=127.0.0.1`, `DB_PORT=3306`, `DB_USER=root`, `DB_NAME=shortscap_db`,
  and `DB_PASSWORD` (already configured in the working copy — never committed).
- `backend/.env.example` — committed template; never contains real secrets.
- Root and backend `.gitignore` both ignore `.env` / `.env.*`.

## Structure

```
backend/
├── app/                     # FastAPI application
│   ├── main.py              # entry point + /health/db endpoint (running FastAPI app)
│   ├── config.py            # pydantic-settings, env-driven (Phase 3)
│   ├── database.py          # SQLAlchemy engine/session/Base/get_db (Phase 3)
│   ├── models/              # SQLAlchemy models — 24 approved models (Phase 4)
│   ├── schemas/             # Pydantic schemas (settings domain implemented; rest placeholders)
│   ├── routers/             # API routes (settings router implemented; rest placeholders)
│   ├── services/            # business services (settings domain implemented; rest placeholders)
│   ├── engines/             # server-side processing engines (placeholders)
│   ├── repositories/        # database access layer (settings domain implemented; rest placeholders)
│   ├── auth/                # OTP / Google / JWT / passwords (placeholders)
│   ├── middleware/          # security + logging middleware (placeholders)
│   └── utils/               # datetime / validation / response helpers
├── scripts/
│   └── check_db.py          # real MySQL connectivity check (Phase 3)
├── migrations/              # Alembic environment (Phase 5)
│   ├── env.py               # wired to app.database.Base.metadata + settings URL
│   ├── script.py.mako       # migration script template
│   └── versions/            # revision scripts (70d943e5af25 — initial schema)
├── tests/                   # pytest package layout (empty)
├── requirements.txt
├── .env                     # local env (git-ignored, not committed)
├── .env.example
├── alembic.ini
└── README.md
```

## Implemented so far

- **Phase 2 — running server:** FastAPI app at `app/main.py` with a
  `GET /` health response; verified via Uvicorn + Swagger `/docs`.
- **Phase 3 — database foundation & environment configuration:**
  - `app/config.py` — `pydantic-settings` `Settings` (env-driven; `.env`
    supported). No credentials in source.
  - `app/database.py` — SQLAlchemy `create_engine` (MySQL via PyMySQL,
    `pool_pre_ping`, `pool_recycle`), `Base` (declarative), `SessionLocal`,
    FastAPI `get_db()` dependency, and `check_database_connection()` that
    performs a real query without exposing the password/URL.
  - `app/main.py` — adds `GET /health/db` (200 `connected` or 503
    `not_connected`; never leaks credentials).
  - `app/models/user.py` — first model `User` (table `users`). *(No tables are
    created.)*
  - `scripts/check_db.py` — connectivity check (honest `success` /
    `not_configured` result).
  - `.env` — created with local DB config (password blank, for manual entry).
  - `requirements.txt` — `fastapi`, `uvicorn`, `pydantic-settings`,
    `SQLAlchemy`, `PyMySQL` (python-dotenv ships with pydantic-settings).
- **Phase 4 — approved database models (SQLAlchemy):**
  - All **24 approved models** are implemented as SQLAlchemy 2.x models in
    `app/models/` and registered on the single shared `Base` metadata via
    `app/models/__init__.py`. Relationships, foreign keys, unique
    constraints and indexes are defined per the approved schema (see
    [Database Models](#database-models) below).
  - The `users` model was updated to the approved schema (email/phone both
    optional but unique, `profile_image_url`, `status` with default
    `"active"`).
  - **Verified:** all 24 models import cleanly, mappers configure without
    errors, the FastAPI app still starts, `/docs` returns 200 and
    `/health/db` returns `connected`.
  - **No tables were created, altered, or dropped in Phase 4** — Alembic
    migration and table creation happened in Phase 5 (below).
- **Phase 5 — Alembic migration & actual MySQL tables:**
  - Alembic **1.19.1** installed and configured: `migrations/env.py` uses the
    SAME declarative `Base` as the app (`target_metadata = Base.metadata`,
    all 24 models imported) and the SAME env-driven database URL from
    `app.config.settings` — nothing hardcoded, no secrets printed.
  - Initial migration **`70d943e5af25` — "create approved schema tables"**
    generated via `alembic revision --autogenerate`, reviewed (only CREATE
    TABLE/INDEX for the 24 approved tables, no destructive ops), then
    applied with `alembic upgrade head`.
  - **MySQL `shortscap_db` now contains the 24 approved tables** (plus
    Alembic's own `alembic_version` tracking table). Verified with
    `SHOW TABLES`, `DESCRIBE` of the key tables, foreign-key and index
    checks, `alembic current` (`70d943e5af25 (head)`) and `alembic history`.
  - FastAPI restart verified: `GET /`, `/health/db` (connected) and `/docs`
    all still work. `alembic` added to `requirements.txt`.
- **Phase 6 — settings data layer (GET/PUT /settings):**
  - First vertical slice: Android Settings → FastAPI API → Settings Service →
    Settings Repository → SQLAlchemy → MySQL. No new tables, no schema
    changes, no new migration (the approved 24-table schema is reused).
  - See the [Phase 6 — Settings Data Layer](#phase-6--settings-data-layer)
    section below for the full detail.
- **Phase 8 — study data layer (schedules / sessions / breaks / events):**
  - Full vertical slice for the four existing study tables
    (`study_schedules`, `study_sessions`, `break_sessions`, `study_events`):
    study schedule CRUD, session start/end/cancel, break start/end, session
    history and study event history. No new tables, no schema changes, no
    new migration.
  - See the [Phase 8 — Study Data Layer](#phase-8--study-data-layer)
    section below for the full detail.
- **Phase 9 — monitoring data layer (app usage / events / summary):**
  - Full vertical slice for the existing monitoring tables
    (`app_usage`, `monitoring_events`): idempotent app-usage sync, usage
    history with filters + pagination, monitoring event submission and
    history, and a basic read-only summary. No new tables, no schema
    changes, no new migration.
  - See the [Phase 9 — Monitoring Data Layer](#phase-9--monitoring-data-layer)
    section below for the full detail.
- **Phase 10 — shorts data layer (usage / events / summary):**
  - Full vertical slice for the existing shorts tables (`shorts_usage`,
    `shorts_events`): idempotent daily shorts-usage sync, usage history with
    filters + pagination, shorts event submission and history, and a basic
    read-only summary. Shorts settings were already covered by the Settings
    layer (Phase 7) and are reused, not duplicated. No new tables, no
    schema changes, no new migration.
  - See the [Phase 10 — Shorts Data Layer](#phase-10--shorts-data-layer)
    section below for the full detail.
- **Phase 11A — shorts usage database schema update (platform + surface):**
  - Added `platform` / `surface` (VARCHAR(50) NOT NULL) to `shorts_usage` so
daily Shorts usage can be stored separately per cross-platform
platform/surface; new logical idempotency key
(user, device, platform, surface, usage_date) enforced by a unique
constraint. One Alembic migration: **`657ba9f4d4f8`** (applied, verified).
  - See the [Phase 11A — Shorts Usage Database Schema Update](#phase-11a--shorts-usage-database-schema-update)
    section below for the full detail.
- **Phase 11B — Android: integrate cross-platform Shorts detection with the
  monitoring pipeline:**
  - Connected `MonitoringEventHub` → `ShortPlatformRegistry` → platform
    adapters → `ShortDetectionResult` → `ShortUsageAggregator` (3–5 second
    rule) → `ShortsBudgetTracker` (one global budget across platforms) →
    `ShortsLocalStore` (local usage/event records pending a future sync
    layer). No backend changes in this phase.
  - See the [Phase 11B — Cross-Platform Shorts Detection Integration](#phase-11b--cross-platform-shorts-detection-integration)
    section below for the full detail.
- **Phase 12 — web data layer (blocked websites / events / summary):**
  - Full vertical slice for the existing web tables (`blocked_websites`,
    `website_events`): blocked-website CRUD with centralized domain
    normalization + duplicate prevention, a blocked-domain check endpoint,
    website event submission and history with filters + pagination, and a
    basic read-only summary. No new tables, no schema changes, no new
    migration.
  - See the [Phase 12 — Web Data Layer](#phase-12--web-data-layer)
    section below for the full detail.
- **Phase 13 — reporting / insights layer (daily / weekly / monthly reports):**
  - Read-only reports computed from existing historical data
    (`study_sessions` / `break_sessions`, `app_usage` /
    `monitoring_events`, `shorts_usage`, `website_events`) via SQL
    aggregation — daily / weekly (ISO week, Mon–Sun) / monthly, with
    per-domain metrics (study / monitoring / shorts / web), Shorts platform
    breakdown, top apps, a daily trend and previous-period comparison
    (zero-guarded: no fake percentages). No report tables, no schema
    changes, no new migration.
  - See the [Phase 13 — Reports / Insights](#phase-13--reports--insights)
    section below for the full detail.
- **Phase 14A — Your Score specification (spec + validation only):**
  - The full mathematical specification (weights, formulas, caps, missing
    data, inactivity, anti-gaming, leaderboard compatibility) lives in
    `docs/your_score_spec.md` and is validated by
    `scripts/score_spec_simulation.py` (6 profiles A–F, sensitivity,
    distribution, anti-gaming). **The score engine is NOT implemented.**
    No schema change, no Android change, no Rank.
  - See the [Phase 14A — Your Score Specification](#phase-14a--your-score-specification)
    section below for the full detail.
- **Phase 14B — Your Score engine (implemented):**
  - Production read-only score engine implementing the approved Phase 14A
    spec exactly: `GET /score/daily|weekly|monthly` with component
    breakdown, activity/coverage info and deterministic explanations.
    Weights 40/25/20/10/5, inactivity gate, coverage scaling, anti-gaming
    (≥ 300 s sessions, volume cap, day-based consistency), no score
    storage, no leaderboard/rank.
  - See the [Phase 14B — Your Score Engine](#phase-14b--your-score-engine)
    section below for the full detail.
- **Phase 15A — Rank / leaderboard specification (spec + validation only):**
  - The full leaderboard design (competition ranking, deterministic
    tie-breaker, eligibility incl. opt-in + score status, rank change,
    winner = rank 1, dynamic-vs-snapshot, API contract) lives in
    `docs/rank_leaderboard_spec.md` and is validated by
    `scripts/rank_spec_simulation.py` (cases A–H + determinism +
    fairness). **The Rank engine is now implemented in Phase 15B.** No
    schema change, no Android change.
  - See the [Phase 15A — Rank / Leaderboard Specification](#phase-15a--rank--leaderboard-specification)
    section below for the full detail.
- **Phase 15B — Rank / Leaderboard engine (implemented):**
  - Production read-only leaderboard engine implementing the approved Phase
    15A spec exactly: `GET /rank/weekly|monthly` with competition ranking,
    deterministic tie-break ordering, opt-in + score-status eligibility,
    winner / top three from the same ranked pass, rank change vs the
    previous equivalent period, pagination with global ranks, and
    privacy-safe entries (rank / display_name / score / user_id only). The
    board is DYNAMIC — scores come from the Score Engine (batch scoring,
    no N+1), `leaderboard_scores` is not written, no caching, no rank
    storage.
  - See the [Phase 15B — Rank / Leaderboard Engine](#phase-15b--rank--leaderboard-engine)
    section below for the full detail.
- **Phase 16 — Android ↔ Backend Synchronization (implemented):**
  - Android network layer (`BackendConfig` base URL with emulator host
    `10.0.2.2` + centralized dev identity header, `BackendApi` /
    `HttpBackendApi` single HTTP client, 1:1 DTOs, `ApiResult`
    success/error handling) and an offline-first sync core (`SyncModels` /
    `SyncQueue` / `SyncManager` with PENDING → SYNCING → SYNCED/FAILED
    states, bounded retry + backoff for transient errors, dedupe keys).
  - Domain syncers for settings, study, monitoring, shorts (platform +
    surface retained) and web events, wired into the existing repository
    seams (`SettingsRepository`, `StudyRepository`, `ShortsMonitoringPipeline`,
    `WebRepository`) with graceful fallback when the backend is unreachable;
    read-only clients for Reports / Your Score / Rank.
  - Conflict policy: local user change is authoritative immediately;
    backend response confirms persistence; server values used on refresh
    sync. Android remains the real-time authority (study timer, monitoring,
    Shorts detection, web blocking); backend remains authoritative for
    persisted history, Reports, Score and Rank.
  - Verification: `backend/scripts/verify_sync_contracts.py` (86 checks) +
    Android `SyncManagerTest` (10 tests) — `:app:compileDebugKotlin` clean,
    `:app:testDebugUnitTest` 20/20. No backend schema change (Alembic still
    `657ba9f4d4f8 (head)`).
  - See the [Phase 16 — Android ↔ Backend Synchronization](#phase-16--android--backend-synchronization)
    section below for the full detail.

## Connection status

- Local MySQL 8.0.43 installed, `MySQL80` service running, database
  `shortscap_db` created, and the backend `.env` is configured —
  `GET /health/db` returns `connected` (HTTP 200) and
  `scripts/check_db.py` reports `success`.
- **AWS RDS production: NOT CONFIGURED** (no RDS instance provisioned).

## Database Models

- **ORM:** SQLAlchemy 2.x (declarative models, one shared `Base` in
  `app/database.py`).
- **Database:** MySQL 8.0.43 (local dev); `shortscap_db` is the current
  development database.
- The approved initial schema contains **24 models**.
- Models were created in **Phase 4** (model definitions only); the actual
  MySQL tables were created in **Phase 5** via Alembic — see
  [Database Migration](#database-migration).

### Models (24)

| Model | Table |
| --- | --- |
| `User` | `users` |
| `UserProfile` | `user_profiles` |
| `AuthIdentity` | `auth_identities` |
| `OtpVerification` | `otp_verifications` |
| `Device` | `devices` |
| `UserSettings` | `user_settings` |
| `PermissionState` | `permission_states` |
| `StudySchedule` | `study_schedules` |
| `StudySession` | `study_sessions` |
| `BreakSession` | `break_sessions` |
| `StudyEvent` | `study_events` |
| `MonitoringSettings` | `monitoring_settings` |
| `AppUsage` | `app_usage` |
| `MonitoringEvent` | `monitoring_events` |
| `ShortsSettings` | `shorts_settings` |
| `ShortsUsage` | `shorts_usage` |
| `ShortsEvent` | `shorts_events` |
| `BlockedWebsite` | `blocked_websites` |
| `WebsiteEvent` | `website_events` |
| `NotificationPreference` | `notification_preferences` |
| `NotificationEvent` | `notification_events` |
| `Feedback` | `feedback` |
| `LeaderboardSetting` | `leaderboard_settings` |
| `LeaderboardScore` | `leaderboard_scores` |

Key constraints (per approved schema): `users.email` and `users.phone` are
optional-but-unique; `devices.device_uuid` is unique; the one-to-one rows
(`user_profiles`, `user_settings`, `monitoring_settings`, `shorts_settings`,
`notification_preferences`, `leaderboard_settings`) have a unique `user_id`;
`blocked_websites.normalized_domain` is indexed and unique per user. OTP rows
store only `otp_hash` — never plain OTP values. `leaderboard_scores` has no
`rank` column (rank is derived later from score ordering).

## Phase 6 — Settings Data Layer

First vertical slice of the backend: the **`user_settings`** domain end to end.

| Layer | File | What it does |
| --- | --- | --- |
| Router | `app/routers/settings.py` | `GET /settings` + `PUT /settings` (partial update). Reads the temporary dev identity from the `X-Dev-User-Id` header |
| Service | `app/services/settings/user_settings.py` | Business logic: default creation on first use, partial-update semantics, `get_settings` / `update_settings` / `ensure_settings` |
| Repository | `app/repositories/settings/user_settings.py` | DB ops only: `get_by_user_id`, `create_default`, `update`, `upsert` — one row per user (unique `user_id`) |
| Schemas | `app/schemas/settings.py` | `UserSettingsResponse` (full payload) + `UserSettingsUpdate` (all-optional partial update) |
| Wiring | `app/main.py` | `include_router(settings_router)` + a generic `SQLAlchemyError` handler (never leaks internals) |

### API

- `GET /settings` — returns the user's current settings; creates the app's
  safe defaults the first time (`theme: dark`, `language: en`,
  `notifications_enabled: true`, `sound_enabled: true`).
- `PUT /settings` — partial update: **only the supplied values change**,
  unspecified values are preserved. Returns the updated settings.
- Validation (mirrors the app, no invented values): `theme` ∈
  `dark | light | system` (Android `ThemeMode`), `language` ∈
  `en | hi | ur | zh | es` (Android `AppLanguage` BCP-47 codes),
  `timezone` = valid IANA name (requires `tzdata` on Windows).
- Errors: missing/invalid dev user ID → `400`; invalid setting value → `422`;
  database errors → `500` with a generic message (no passwords, URLs, or
  stack traces exposed).

### TEMPORARY DEVELOPMENT USER IDENTITY (NOT PRODUCTION AUTH)

AWS Cognito is planned for a later phase. Until then the settings API
identifies the user via the **`X-Dev-User-Id`** request header
(e.g. `X-Dev-User-Id: 1`). A minimal `users` row is auto-created for the dev
ID so the settings row's foreign key is satisfied.

This is **development only** and is **NOT a production security mechanism** —
it grants no privileges and must be removed when real authentication lands.
It is isolated in `app/routers/settings.py` (`get_dev_user_id` +
`_ensure_dev_user`) so Cognito integration replaces it without touching the
endpoints.

### MySQL persistence

Verified end to end: `GET` → default row created; `PUT` → row updated;
`GET` again → saved values returned; `SELECT * FROM user_settings` shows the
persisted row (e.g. user 1: `light / hi / Asia/Kolkata / on / on`). This is
the basis for future device synchronization.

### Current status

- Repository / Service / Schemas / Router: implemented and verified.
- Android is **NOT connected yet** — client synchronization is the next step
  after backend verification.
- Monitoring, Study, Shorts, Rank, AWS and Cognito are **not** part of this
  phase.

## Phase 7 — Settings Backend

The Phase 6 pattern (Router → Service → Repository → SQLAlchemy → MySQL) is
now applied to the remaining settings domains. Every domain follows the same
architecture; no new tables and no schema changes were made.

| Domain | Endpoints | Files (services / repositories) |
| --- | --- | --- |
| Monitoring settings | `GET` / `PUT /settings/monitoring` | `services/settings/monitoring.py`, `repositories/settings/monitoring.py` |
| Shorts settings | `GET` / `PUT /settings/shorts` | `services/settings/shorts.py`, `repositories/settings/shorts.py` |
| Notification preferences | `GET` / `PUT /settings/notifications` | `services/settings/notification.py`, `repositories/settings/notification.py` |
| Leaderboard settings | `GET` / `PUT /settings/leaderboard` | `services/settings/leaderboard.py`, `repositories/settings/leaderboard.py` |
| Permission states | `GET` / `PUT /settings/permissions` | `services/settings/permission.py`, `repositories/settings/permission.py` |

All schemas live in `app/schemas/settings.py`; all routes in
`app/routers/settings.py` (same router, same temporary dev identity).

### Behavior & validation

- **GET** returns the user's current settings; the first call creates the
  model-default row (`monitoring` on / on / strict off; `shorts` enabled /
  limits unset; notifications on / on / on; leaderboard enabled / not opted
  in). Permissions GET returns an empty list until something is synced — no
  defaults are invented.
- **PUT** is a partial update: only supplied values change, unspecified
  values are preserved.
- Validation mirrors the Android app: booleans are real booleans; Shorts
  numeric limits are non-negative; `display_name` ≤ 100 chars; permission
  keys are the app's real `PermissionId` values (`USAGE_ACCESS`,
  `ACCESSIBILITY`, `OVERLAY`, `NOTIFICATIONS`, `BATTERY_OPTIMIZATION`,
  `STORAGE_MEDIA`, `SYSTEM_AUDIO_ACCESS`); invalid input → `422`.
- **Permission states are a sync mirror only** — the Android system remains
  the real source of truth; this stores the last-known synchronized state.
- **Leaderboard settings are participation/display preferences only** — no
  score / rank / winner calculation.

### MySQL persistence

Verified per domain for dev user 3 (`SELECT …`):
`monitoring_settings` strict=on, monitoring=off; `shorts_settings` limits
45 / 30; `notification_preferences` study=off; `leaderboard_settings`
opted-in as "Rahul"; `permission_states` USAGE_ACCESS=enabled,
ACCESSIBILITY=disabled. `GET` after `PUT` returns exactly these values.

### Temporary development identity

The same `X-Dev-User-Id` header as Phase 6 (isolated in
`app/routers/settings.py`) — **temporary development only, NOT a production
authentication mechanism**. Cognito integration is planned for a later phase
and will replace it without touching the endpoints.

### Not part of this phase

Monitoring / Shorts detection & enforcement engines, notification delivery,
leaderboard ranking, AWS, Cognito, and Android connectivity.

## Phase 8 — Study Data Layer

Phase 8 implements the backend **study data layer** — study schedules, study
sessions, break sessions and study events — using the **existing approved
tables** (`study_schedules`, `study_sessions`, `break_sessions`,
`study_events`). No new tables were created, no schema was changed, and no
Alembic migration was added.

| Layer | Files | What it does |
| --- | --- | --- |
| Router | `app/routers/study.py` | All `/study/*` endpoints; reads the temporary dev identity from the `X-Dev-User-Id` header (shared `app/routers/deps.py`) |
| Schemas | `app/schemas/study.py` | Input/output models: `StudyScheduleCreate/Update/Response`, `StudySessionStart/End/Response`, `BreakSessionResponse`, `StudyEventResponse` |
| Services | `app/services/study/{schedule,session,break_session,event,errors}.py` | Ownership checks, state transitions, server-side duration calculation, event creation |
| Repositories | `app/repositories/study/{schedule,session,break_session,event}.py` | DB operations only (`create` / `get` / `list` / `update` / `delete`, filters) |
| Wiring | `app/main.py` | `include_router(study_router)` under the existing `SQLAlchemyError` handler |

### Study Schedule API

- `POST /study/schedules` — create a schedule (`title` required; positive
  `duration_minutes`; non-negative `reminder_minutes`; `days_of_week` accepts
  weekday names like `"Mon"` / `"Monday"`, stored comma-separated and
  returned as a list).
- `GET /study/schedules` — list the current user's schedules.
- `GET /study/schedules/{schedule_id}` — one schedule (404 for other users').
- `PUT /study/schedules/{schedule_id}` — partial update (only supplied
  fields change).
- `DELETE /study/schedules/{schedule_id}` — delete (204).

### Study Session API

- `POST /study/sessions/start` — start a session: `started_at = server now`,
  `planned_duration_seconds` (optional, positive), `status = active`, plus a
  `STUDY_STARTED` event. Optional `schedule_id` / `device_id` must belong to
  the user. **The backend does not run a real-time timer** — it only persists
  state and history; Android remains responsible for real-time timing and UI.
- `POST /study/sessions/{session_id}/end` — end an ACTIVE session:
  `ended_at = server now`, `actual_duration_seconds = ended_at - started_at`
  (server timestamps, never the client duration), `status = completed`,
  `STUDY_ENDED` event. `{"cancelled": true}` explicitly represents
  cancellation (`status = cancelled`, `STUDY_CANCELLED` event).
- `POST /study/sessions/{session_id}/cancel` — cancel an ACTIVE session
  (`status = cancelled`, `STUDY_CANCELLED`).
- `GET /study/sessions` — session history (newest first) with optional
  filters: `status`, `schedule_id`, `date_from` / `date_to` (on `started_at`).
- `GET /study/sessions/{session_id}` — one session (404 for other users').

### Break Session API

- `POST /study/sessions/{session_id}/breaks/start` — start a break inside an
  ACTIVE session (`started_at = server now`, `status = active`,
  `BREAK_STARTED` event). Rejects breaks on non-active sessions and
  overlapping active breaks for the same session.
- `POST /study/breaks/{break_id}/end` — end an ACTIVE break
  (`ended_at = server now`, `duration_seconds` from server timestamps,
  `status = completed`, `BREAK_ENDED` event). Already-completed breaks cannot
  be ended twice.

### Study Event history

- `GET /study/events` — the current user's study events (newest first) with
  optional filters: `event_type` (one of `STUDY_STARTED`, `STUDY_ENDED`,
  `STUDY_CANCELLED`, `BREAK_STARTED`, `BREAK_ENDED` — `STUDY_REMINDER` is
  reserved for the future reminder engine), `session_id`, `date_from` /
  `date_to`. Only events for actual backend actions are created; `metadata_json`
  holds small non-sensitive values (e.g. `actual_duration_seconds`), never
  secrets.

### State rules & validation

- Safe transitions only: `active → completed` / `active → cancelled`;
  breaks only while the session is `active`; no ending a completed session or
  break twice; no overlapping active breaks.
- Validation errors → **422** (schema layer); missing / cross-user records →
  **404**; invalid state transitions → **400**; database errors → **500** with
  a generic message (never internals).
- Timestamps: timezone-consistent **UTC** (naive) everywhere, matching the
  `func.now()` server defaults — see `app/utils/datetime.py` (`utcnow()`).

### Repository / service / router architecture

Same pattern as the Settings layer: Router → Pydantic Schema → Service →
Repository → SQLAlchemy Model → MySQL. Repositories contain database
operations only; services own ownership validation, state transitions,
duration calculation and event creation; routers contain no database queries;
no raw SQL lives in services.

### MySQL persistence

Verified end to end: every action creates the correct row in
`study_schedules`, `study_sessions`, `break_sessions` and `study_events`
with correct foreign keys, server timestamps and durations, and each action
produces the matching event row. See `scripts/verify_study.py`.

### Current development identity

The same temporary `X-Dev-User-Id` header as the settings API, now shared via
`app/routers/deps.py` — the **single Cognito replacement point** for the whole
backend. Development only; NOT a production security mechanism.

### Real-time timers remain Android-side

The backend is a persistence layer only — no background timers run inside
FastAPI. Android keeps its existing countdown / break-reminder / UI logic;
the backend stores the study state and history those sessions produce.

### Cognito

Not implemented (planned for a later phase) — see the development identity
note above.

### Verification

- `scripts/verify_study.py` — exercises the full study flow (schedule →
  session → break → end → history → events) plus the invalid-state cases
  against a running server and a live MySQL database, and reports
  PASS/FAIL per item. Start the server first:

  ```powershell
  cd backend
  .venv\Scripts\python -m uvicorn app.main:app --reload
  .venv\Scripts\python -m scripts.verify_study
  ```

- Manual testing is also available through Swagger at
  <http://127.0.0.1:8000/docs> (send the `X-Dev-User-Id` header, e.g. `1`).

### Not part of this phase

Android connectivity (the Android `StudyRepository` calls these APIs in a
later phase), the study schedule engine, break-reminder delivery, real-time
timers, and any new study features.

## Phase 9 — Monitoring Data Layer

Phase 9 implements the backend **monitoring data layer** — app usage
summaries, monitoring events and a basic summary — using the **existing
approved tables** (`app_usage`, `monitoring_events`; `monitoring_settings`
was already handled by the Settings layer in Phase 7 and is reused, not
recreated). No new tables were created, no schema was changed, and no Alembic
migration was added.

| Layer | Files | What it does |
| --- | --- | --- |
| Router | `app/routers/monitoring.py` | All `/monitoring/*` endpoints; reads the temporary dev identity from the `X-Dev-User-Id` header (shared `app/routers/deps.py`) |
| Schemas | `app/schemas/monitoring.py` | Input/output models: `AppUsageRecord` / `AppUsageResponse`, `MonitoringEventCreate` / `MonitoringEventResponse`, `MonitoringSummary` |
| Services | `app/services/monitoring/{usage,event,errors}.py` | Device ownership, timestamp normalization (naive UTC), idempotent sync coordination, summary aggregation |
| Repositories | `app/repositories/monitoring/{usage,event}.py` | DB operations only (create / lookup / upsert / filtered list / aggregate) |
| Wiring | `app/main.py` | `include_router(monitoring_router)` under the existing `SQLAlchemyError` handler |

### App usage synchronization

- `POST /monitoring/app-usage/sync` — accepts **one or a batch** of aggregated
daily usage summaries (`device_id`, `package_name`, `app_name`, `usage_date`,
`duration_seconds`, `launch_count`) and persists them to `app_usage` for the
current user. The user identity always comes from the development header — a
client-supplied `user_id` is never trusted.
- **Idempotent duplicate handling:** the schema has no unique constraint on
(user, device, package, date), so the repository performs a careful
lookup-then-upsert — re-syncing the same summary **overwrites** its aggregate
values (last sync wins) instead of inserting duplicate rows. No schema change
was needed.
- **Validation:** `duration_seconds` / `launch_count` ≥ 0; `package_name` must
be a valid Android package shape (e.g. `com.example.app`); `app_name`, when
provided, must not be blank → invalid input returns **422**.

### Monitoring history

- `GET /monitoring/app-usage` — the current user's usage history (newest date
first) with filters: `device_id`, `package_name`, `date_from` / `date_to` (on
`usage_date`), plus simple `page` / `page_size` pagination (default 50, max
100). Only the caller's own rows are returned.

### Monitoring events

- `POST /monitoring/events` — persist one event (`device_id`, `event_type`,
optional `app_package` / `metadata_json` / `occurred_at`). Supported event
types map to existing Android concepts and are limited to:
  `MONITORING_STARTED`, `MONITORING_STOPPED`, `LIMIT_WARNING`,
  `LIMIT_REACHED`, `APP_RESTRICTED` — no invented taxonomy.
- `GET /monitoring/events` — the current user's events (newest first) with
filters: `event_type`, `device_id`, `app_package`, `start_date` / `end_date`
(on `occurred_at`), plus `page` / `page_size` pagination.
- **Timestamps:** `occurred_at` defaults to the server's current UTC time;
aware datetimes are normalized to the backend's naive-UTC convention before
storage (`app/utils/datetime.py`) — no silent timezone reinterpretation.

### Monitoring summary

- `GET /monitoring/summary` — read-only summary via DB aggregation:
  `total_app_usage_seconds`, `total_launches`, `monitored_apps_count`
  (distinct packages) and `event_count`. Deliberately minimal — weekly /
  monthly reports, Your Score, leaderboard and ranking are later phases.

### Device ownership

Monitoring data must reference a device that exists AND belongs to the
current user — an unknown device or another user's device returns **404**
(never exposing other users' records).

### Repository / service / router architecture

Same pattern as Settings and Study: Router → Pydantic Schema → Service →
Repository → SQLAlchemy Model → MySQL. Repositories contain database
operations only; services own device ownership, validation, timestamp
normalization and sync coordination; routers contain no database queries; no
raw SQL lives in services.

### Android remains the real-time monitoring authority

The backend is a data / synchronization API only. There is **no** real-time
monitoring loop, no server-side app detection, no polling of Android, no
WebSocket monitoring and no server-side timers. Android detects usage,
collects duration/launch counts, handles restrictions/notifications locally
and syncs observed summaries here; the backend validates, stores and serves
history for future Reports and Your Score/Rank calculations.

### MySQL persistence

Verified end to end: every sync/event action creates the correct row in
`app_usage` / `monitoring_events` with correct foreign keys, ownership,
timestamps and (for usage) idempotent overwrite behavior; events match the
submitted actions. See `scripts/verify_monitoring.py`.

### Current development identity

The same temporary `X-Dev-User-Id` header (shared `app/routers/deps.py`) —
**development only, NOT a production authentication mechanism**. Cognito is
planned for a later phase and will replace only the identity/authentication
boundary.

### Verification

- `scripts/verify_monitoring.py` — full monitoring flow (usage sync → history
  → events → summary), duplicate-sync behavior, invalid-input cases, user
  isolation, direct MySQL row checks, and a regression pass over the existing
  Settings and Study endpoints. Start the server first:

  ```powershell
  cd backend
  .venv\Scripts\python -m uvicorn app.main:app --reload
  .venv\Scripts\python -m scripts.verify_monitoring
  ```

- Manual testing is also available through Swagger at
  <http://127.0.0.1:8000/docs> (send the `X-Dev-User-Id` header, e.g. `1`).

### Not part of this phase

Android connectivity, the real-time device-monitoring engine, restriction /
notification enforcement, offline buffering, weekly/monthly reports,
Your Score / leaderboard / ranking, and any new monitoring features.

## Phase 10 — Shorts Data Layer

Phase 10 implements the backend **shorts data layer** — daily Shorts usage
summaries, Shorts events and a basic summary — using the **existing approved
tables** (`shorts_usage`, `shorts_events`; `shorts_settings` was already
handled by the Settings layer in Phase 7 and is reused, not recreated). No
new tables were created, no schema was changed, and no Alembic migration was
added.

| Layer | Files | What it does |
| --- | --- | --- |
| Router | `app/routers/shorts.py` | All `/shorts/*` endpoints; reads the temporary dev identity from the `X-Dev-User-Id` header (shared `app/routers/deps.py`) |
| Schemas | `app/schemas/shorts.py` | Input/output models: `ShortsUsageRecord` / `ShortsUsageResponse`, `ShortsEventCreate` / `ShortsEventResponse`, `ShortsSummary` |
| Services | `app/services/shorts/{usage,event,errors}.py` | Device ownership, timestamp normalization (naive UTC), idempotent sync coordination, summary aggregation |
| Repositories | `app/repositories/shorts/{usage,event}.py` | DB operations only (create / lookup / upsert / filtered list / aggregate) |
| Wiring | `app/main.py` | `include_router(shorts_router)` under the existing `SQLAlchemyError` handler |

### Shorts usage synchronization

- `POST /shorts/usage/sync` — accepts **one or a batch** of aggregated daily
Shorts usage summaries (`device_id`, `usage_date`, `shorts_count`,
`duration_seconds`, `warning_triggered`, `limit_reached`) and persists them
to `shorts_usage` for the current user. The user identity always comes from
the development header — a client-supplied `user_id` is never trusted.
- **Idempotent duplicate handling:** the schema has no unique constraint on
(user, device, usage_date), so the repository performs a careful
lookup-then-upsert (the same strategy as Monitoring) — re-syncing the same
day's summary **overwrites** its values (last sync wins) instead of inserting
duplicate rows. No schema change was needed.
- **Validation:** `shorts_count` / `duration_seconds` ≥ 0; `usage_date` must
be a valid date → invalid input returns **422**.
- **Warning / limit state:** `warning_triggered` and `limit_reached` are
persisted **exactly as supplied**. The data layer does NOT decide whether a
user reached their limit — the Android enforcement system remains
authoritative for real-time limit state; a future server-side
scoring/analytics layer may use these fields.

### Shorts usage history

- `GET /shorts/usage` — the current user's Shorts usage history (newest date
first) with filters: `device_id`, `date_from` / `date_to` (on `usage_date`),
plus simple `page` / `page_size` pagination (default 50, max 100). Only the
caller's own rows are returned.

### Shorts events

- `POST /shorts/events` — persist one event (`device_id`, `event_type`,
optional `occurred_at` / `duration_seconds` / `metadata_json`). Supported
event types map 1:1 to actual Android Shorts behaviors and are limited to:
  `SHORT_STARTED` (a Short started), `SHORT_COUNTED` (the 3–5 second counting
  logic counted a Short), `SHORT_ENDED` (a Short ended),
  `WARNING_TRIGGERED` (the app's `SHORTS_LIMIT_WARNING` behavior),
  `LIMIT_REACHED` (the app's `SHORTS_LIMIT_REACHED` behavior) — no invented
taxonomy.
- `GET /shorts/events` — the current user's events (newest first) with
filters: `event_type`, `device_id`, `start_date` / `end_date` (on
`occurred_at`), plus `page` / `page_size` pagination.
- **Timestamps:** `occurred_at` defaults to the server's current UTC time;
aware datetimes are normalized to the backend's naive-UTC convention before
storage (`app/utils/datetime.py`) — no silent timezone reinterpretation.

### Shorts summary

- `GET /shorts/summary` — read-only summary via DB aggregation:
  `total_shorts_count`, `total_duration_seconds`, `average_daily_shorts`,
  `average_daily_duration` (totals ÷ distinct usage days), `warning_count`
  and `limit_reached_count`. Deliberately minimal — weekly / monthly
  reports, Your Score, Rank and leaderboard are later phases.

### Device ownership & user isolation

Shorts data must reference a device that exists AND belongs to the current
user — an unknown device or another user's device returns **404** (never
exposing other users' records). All GET operations return only the current
user's data; a user cannot request another user's usage, events or summaries.

### Repository / service / router architecture

Same pattern as Settings, Study and Monitoring: Router → Pydantic Schema →
Service → Repository → SQLAlchemy Model → MySQL. Repositories contain
database operations only; services own device ownership, validation,
timestamp normalization and sync coordination; routers contain no database
queries; no raw SQL lives in services.

### Android remains responsible for real-time Shorts detection

The backend is a data / synchronization API only. There is **no** real-time
Shorts detection, no server-side counting loop, no device control and no
timers. Android identifies Shorts activity, detects start/end, applies the
3–5 second counting logic, enforces limits, triggers warning/reached events
and buffers locally when offline; the backend validates, stores and serves
the synchronized history for later Reports and Your Score/Rank.

### MySQL persistence

Verified end to end: every sync/event action creates the correct row in
`shorts_usage` / `shorts_events` with correct foreign keys, ownership, dates,
durations, counts, warning/limit states and (for usage) idempotent overwrite
behavior; events match the submitted actions. See `scripts/verify_shorts.py`.

### Current development identity

The same temporary `X-Dev-User-Id` header (shared `app/routers/deps.py`) —
**development only, NOT a production authentication mechanism**. Cognito is
planned for a later phase and will replace only the identity/authentication
boundary. AWS deployment is planned even later.

### Verification

- `scripts/verify_shorts.py` — full Shorts flow (usage sync → history →
  events → summary), duplicate-sync behavior, invalid-input cases, device
  ownership, user isolation, direct MySQL row checks, and a regression pass
  over the existing Settings, Study and Monitoring endpoints. Start the
  server first:

  ```powershell
  cd backend
  .venv\Scripts\python -m uvicorn app.main:app --reload
  .venv\Scripts\python -m scripts.verify_shorts
  ```

- Manual testing is also available through Swagger at
  <http://127.0.0.1:8000/docs> (send the `X-Dev-User-Id` header, e.g. `1`).

### Not part of this phase

Android connectivity, the real-time Shorts detection/enforcement engine,
warning/reached notification & sound delivery, offline buffering,
weekly/monthly reports, Your Score / Rank / leaderboard, scoring formulas,
and any new Shorts features.

## Phase 11A — Shorts Usage Database Schema Update

Phase 11A makes the `shorts_usage` table cross-platform ready: daily Shorts
usage can now be stored **separately per platform and surface** so
platform-specific daily aggregation is possible (the foundation the
cross-platform Shorts architecture needs).

**Why `platform` / `surface` were added:** the previous schema had a single
daily summary per (user, device, date) — it could not distinguish YouTube
Shorts from Instagram Reels, TikTok, Snapchat Spotlight, etc. Without the
columns, platform-specific reporting and the per-platform breakdown required
by the cross-platform architecture are impossible.

**New logical idempotency key:**

```
user_id + device_id + platform + surface + usage_date
```

Enforced by the unique constraint
`uq_shorts_usage_user_device_platform_surface_date` — re-syncing the same
daily summary for the same platform/surface can never create uncontrolled
duplicates (the repository lookup-then-upsert uses this exact key).

**Migration strategy for existing rows (documented, safe):** pre-architecture
rows receive the explicit marker value `UNKNOWN` for `platform` / `surface`
via `server_default` — historical platform/surface values are NEVER invented
or fabricated. If legacy rows would collide under the new key, the migration
keeps the newest row per key and removes older duplicates before creating
the unique constraint (defensive; the table was empty in practice).

**Backward compatibility:** `platform` / `surface` are OPTIONAL in the sync
payload — clients that do not send them get the `UNKNOWN` marker (never a
fabricated value). The response schema always returns them. Invalid
platform/surface values are rejected with 422.

### Files changed

| File | Change |
| --- | --- |
| `app/models/shorts_usage.py` | Added `platform` / `surface` columns (NOT NULL, default/`server_default` `UNKNOWN`) + `UniqueConstraint` on the 5-column key |
| `app/schemas/shorts.py` | `ShortsUsageRecord` gains optional `platform` / `surface` (validated Literals); `ShortsUsageResponse` always returns them |
| `app/repositories/shorts/usage.py` | Lookup/upsert key extended to (user, device, platform, surface, date) |
| `app/services/shorts/usage.py` | Normalizes omitted platform/surface to `UNKNOWN` |
| `migrations/versions/657ba9f4d4f8_*.py` | **New Alembic migration** — adds the 2 columns + unique constraint (reviewed; scoped to `shorts_usage` only) |

### Migration

- Revision: **`657ba9f4d4f8`** — "add platform and surface to shorts_usage"
  (down_revision `70d943e5af25`).
- Status: **applied** — `alembic current` reports `657ba9f4d4f8 (head)`;
  `alembic history` shows `70d943e5af25 -> 657ba9f4d4f8 (head)`.
- Verified in MySQL: `SHOW COLUMNS FROM shorts_usage` shows `platform`
  varchar(50) NOT NULL and `surface` varchar(50) NOT NULL; the unique index
  `uq_shorts_usage_user_device_platform_surface_date` exists on
  (user_id, device_id, platform, surface, usage_date).

### Compatibility implications

- Existing clients that sync without platform/surface keep working
  (stored as `UNKNOWN`).
- Same-day summaries for different platforms/surfaces are now SEPARATE rows
  (e.g. day 1 YouTube Shorts + day 1 Reels = 2 rows); the summary endpoint
  aggregates them into one global Shorts total (the global-budget concept).
- Settings / Study / Monitoring tables and endpoints are untouched.

### Verification

`scripts/verify_shorts.py` extended and re-run (67 checks): platform/surface
persisted, UNKNOWN default for omitted values, per-key idempotency (same
platform re-sync overwrites; different platform same day = separate row),
unique constraint present in MySQL, invalid platform → 422, plus the full
Phase 10 flow and Settings/Study/Monitoring regression.

## Phase 11B — Cross-Platform Shorts Detection Integration

Phase 11B connects the **Android** monitoring pipeline to the cross-platform
Shorts architecture (backend unchanged in this phase). Android remains the
real-time authority; detection is NOT moved to Python/FastAPI.

### Flow

```
MonitoringEventHub
  -> ShortPlatformRegistry (package -> adapter)
  -> ShortPlatformAdapter.detect()
  -> ShortDetectionResult
  -> ShortUsageAggregator (3–5 second rule)
  -> ShortsBudgetTracker (ONE global budget across platforms)
  -> ShortsLocalStore (local usage/event records -> future sync layer)
```

### Files (Android, `app/src/main/java/com/shortscap/app/`)

| File | Change |
| --- | --- |
| `monitoring/MonitoringEventHub.kt` | Listener + dispatch now carry the window **class name** (privacy-minimal metadata, same family as the package name — never content) |
| `accessibility/ShortsCapAccessibilityService.kt` | Passes `event.className` alongside the package; subscribes `ShortsMonitoringPipeline.start()` on service connect (still package/class metadata only — no window content) |
| `shorts/ShortsMonitoringPipeline.kt` | **New** — the passive orchestrator: tracks the foreground context, detects via the registry, aggregates, updates the global budget, writes local records |
| `shorts/ShortsLocalStore.kt` | **New** — local usage/event records (platform/surface/detection method/confidence/timestamp/duration preserved) + in-memory impl + sync seam |
| `shorts/ShortsMonitoringPipelineTest.kt` | **New** — 10 unit tests (see below) |

### Detection vs counting (kept separate)

- **Detector** (`ShortPlatformRegistry` + adapters): identifies platform /
surface, returns confidence + detection method.
- **Aggregator** (`ShortUsageAggregator`): applies the 3–5 second rule,
decides whether an item counts, updates count/duration, generates the local
usage record + event.
- Platform adapters contain NO counting logic; `MonitoringService` does not
own Shorts counting.

### 3–5 second rule (preserved, unchanged thresholds)

- Context left before ~2 seconds (`SHORT_SWIPE_RULE_MILLIS`) → NOT counted.
- Engagement reaching the 3–5 second threshold
  (`SHORT_MIN_ENGAGEMENT_MILLIS`) → counted as one Short with its full
duration.
- The pipeline evaluates each foreground context exactly once (on
transition), so duplicate window events never double-count.

### Global Shorts budget

Shorts from every platform accumulate into ONE budget — switching from
YouTube Shorts to Instagram Reels to TikTok never resets it
(verified by unit test: 4s YouTube + 4s Instagram = 8s global, per-platform
breakdown `{YOUTUBE: 4000, INSTAGRAM: 4000}`).

### Local vs backend (sync boundary)

Detector → Aggregator → **`ShortsLocalStore`** → future sync layer. The
detector NEVER talks to FastAPI directly; a controlled later integration
step drains the local store to the Phase 10 `shorts_usage` / `shorts_events`
APIs (which now accept `platform` / `surface` per Phase 11A).

### Supported platforms & honest limitations

- **Platforms architected:** YouTube, Instagram, TikTok, Snapchat, Facebook,
  Moj, X, LinkedIn (+ UNKNOWN). **Surfaces:** YouTube Shorts, Instagram
  Reels, Facebook Reels, TikTok short feed, Snapchat Spotlight, X short
  video, LinkedIn short video, Moj short video (+ UNKNOWN).
- **Honest detection status:** with the current signal set, only the YouTube
  Shorts surface is positively detected (via its window class). All other
  platforms report UNKNOWN/low confidence and are NEVER counted — no
  fabricated detections.
- **Counting granularity:** each continuous foreground session on a
  short-form surface counts as ONE Short (window-state events cannot see
  individual swipes), so duration-based usage stays accurate while per-short
  counts are session-level. Documented, not hidden.

### Testing

- `./gradlew :app:compileDebugKotlin` — compiles clean.
- `./gradlew :app:testDebugUnitTest` — **10/10 PASS**
  (`ShortsMonitoringPipelineTest`): known platform + short surface counted;
  known platform + non-short content not counted; unknown platform not
  counted; unknown surface not counted; swipe before threshold not counted;
  engagement beyond threshold counted once; platform switching keeps the
  global budget; global budget accumulates across platforms; duplicate
  events not double-counted; insufficient confidence not counted.
- Backend regression re-run: Settings / Study / Monitoring / Shorts verify
  scripts + `/health/db` + `/docs` all PASS (backend untouched in 11B).

### Not part of this phase

Actual backend synchronization from Android, ranking/score, reports,
AWS, Cognito, and UI changes (none made).

## Phase 12 — Web Data Layer

Phase 12 implements the backend **web data layer** — blocked-website
configuration with centralized domain normalization, synchronized website
events, website history and a basic summary — using the **existing approved
tables** (`blocked_websites`, `website_events`). No new tables were created,
no schema was changed, and no Alembic migration was added (the approved
schema already supports everything this phase needs).

| Layer | Files | What it does |
| --- | --- | --- |
| Router | `app/routers/web.py` | `/websites/blocked` CRUD + `/check`, `/web/events`, `/web/summary`; reads the temporary dev identity from the `X-Dev-User-Id` header (shared `app/routers/deps.py`) |
| Schemas | `app/schemas/web.py` | `BlockedWebsiteCreate/Update/Response`, `BlockedCheckResponse`, `WebsiteEventCreate/Response`, `WebSummary` |
| Services | `app/services/web/{blocked_website,event,errors}.py` | Domain normalization + validation, duplicate prevention (409), ownership checks, timestamp normalization (naive UTC), summary aggregation |
| Repositories | `app/repositories/web/{blocked_website,event}.py` | DB operations only (create / lookup / list / update / delete / filtered list / counts) |
| Utility | `app/utils/domain.py` | Single reusable domain normalizer/validator (mirrors the Android app's `web/DomainValidator.kt` rules) |
| Wiring | `app/main.py` | `include_router(blocked_websites_router)` + `include_router(web_events_router)` under the existing `SQLAlchemyError` handler |

### Domain normalization & validation

A single reusable utility, `app/utils/domain.py`, normalizes every
user-supplied URL/domain to one canonical bare domain:

- `https://youtube.com/`, `http://www.youtube.com`, `WWW.YouTube.com` and
  `youtube.com` **all normalize to `youtube.com`** (scheme stripped, case
  lowercased, leading `www.` removed, path/query/fragment removed, trailing
  dot/slash stripped).
- Malformed input is rejected with **422**: empty / whitespace-only strings,
  bare labels (`localhost`), pure IPv4 addresses and syntactically invalid
  domains. No DNS/network checks are performed — pure syntax validation that
  never depends on internet availability.
- The rules mirror the Android app's existing domain logic
  (`web/DomainValidator.kt` + `normalizeWebDomain`), so the backend accepts
  exactly what the app accepts.

### Blocked websites (CRUD)

- `POST /websites/blocked` — block a domain for the current user
  (`domain` required; optional `verification_status` — `pending` /
  `verified` / `failed`, informational only — and `is_blocked`, default
  `true`). The domain is normalized before storage; `normalized_domain` is
  the canonical form and the raw input is preserved in `domain`.
- `GET /websites/blocked` — list the user's blocked websites (oldest first).
- `GET /websites/blocked/{website_id}` — one row (404 when absent).
- `PUT /websites/blocked/{website_id}` — partial update (`is_blocked`,
  `verification_status`, `domain`). A supplied domain is re-normalized;
  changing it to a domain the user already has blocked returns **409**.
- `DELETE /websites/blocked/{website_id}` — remove one row (404 when absent).
- `GET /websites/blocked/check?domain=...` — answers whether the current
  user has the (normalized) domain blocked: `is_present` + `is_blocked`.
  Case/prefix-insensitive (same normalization).
- **Duplicate prevention:** `youtube.com`, `www.youtube.com` and
  `https://youtube.com/` are the SAME logical domain for a user — a second
  creation attempt returns **409** instead of inserting a duplicate row. The
  schema's unique constraint `uq_blocked_websites_user_domain` (user_id +
  normalized_domain) is the backstop.

### Website events

- `POST /web/events` — persist one event (`event_type`, optional `device_id`,
  `blocked_website_id`, `domain`, `occurred_at`). Supported event types are
  restricted to those actually useful to the app and are limited to:
  `BLOCK_ATTEMPT` (access to a blocked domain was attempted), `BLOCKED`
  (blocking happened) and `UNBLOCKED` (a domain was unblocked) — no invented
taxonomy. The `domain` is normalized before storage and, when supplied,
`device_id` / `blocked_website_id` must belong to the current user.
- `GET /web/events` — the user's event history (newest first) with filters:
  `event_type`, `device_id`, `domain` (normalized), `start_date` /
  `end_date` (on `occurred_at`), plus `page` / `page_size` pagination. This
  is the website history endpoint — no separate duplicate route was added.
- **Timestamps:** `occurred_at` defaults to the server's current UTC time;
  aware datetimes are normalized to the backend's naive-UTC convention
  before storage (`app/utils/datetime.py`) — no silent timezone
  reinterpretation.

### Web summary

- `GET /web/summary` — read-only summary via DB aggregation:
  `total_block_attempts`, `total_blocked_events`, `total_unblock_events`
  and `unique_blocked_domains` (distinct domains the user currently has
  blocked). Deliberately minimal — weekly / monthly reports, Your Score,
  Rank and leaderboard are later phases.

### Device ownership & user isolation

Events that reference a device must reference one that exists AND belongs to
the current user — an unknown device or another user's device returns **404**
(never exposing other users' records). The same applies to
`blocked_website_id` references. All GET/PUT/DELETE operations return only
the current user's data; a user cannot request another user's websites,
events or summaries.

### Repository / service / router architecture

Same pattern as Settings, Study, Monitoring and Shorts: Router → Pydantic
Schema → Service → Repository → SQLAlchemy Model → MySQL. Repositories
contain database operations only; services own domain normalization,
validation, duplicate prevention, ownership checks and timestamp
normalization; routers contain no database queries; no raw SQL lives in
services; normalization is centralized in `app/utils/domain.py`.

### Android remains responsible for real-time web blocking

The backend is a configuration / history API only. There is **no**
server-side browser monitoring, no server-side AccessibilityService, no
browser control, no blocking loop and no WebSocket-based real-time blocking.
Android detects web/domain activity, enforces blocking in real time, shows
the blocked-page UI and handles local restrictions; it syncs events to this
layer (`Android Web/Blocking Engine → local event → sync boundary →
POST /web/events → backend persistence`). If Android is offline, the backend
is simply not contacted; offline buffering is Android-side and out of scope
here.

### MySQL persistence

Verified end to end: every CRUD/event action creates/updates the correct row
in `blocked_websites` / `website_events` with correct foreign keys,
ownership, normalized domains, block status, event types and timestamps;
normalized duplicates are prevented; events match the submitted actions. See
`scripts/verify_web.py`.

### Current development identity

The same temporary `X-Dev-User-Id` header (shared `app/routers/deps.py`) —
**development only, NOT a production authentication mechanism**. Cognito is
planned for a later phase and will replace only the identity/authentication
boundary. AWS deployment is planned even later.

### Verification

- `scripts/verify_web.py` — full Web flow (blocked-website CRUD → duplicate
  prevention → domain normalization → website events → filters → summary),
  invalid-input cases, device/website ownership, user isolation, direct
  MySQL row checks, and a regression pass over the existing Settings,
  Study, Monitoring and Shorts endpoints. Start the server first:

  ```powershell
  cd backend
  .venv\Scripts\python -m uvicorn app.main:app --reload
  .venv\Scripts\python -m scripts.verify_web
  ```

- Manual testing is also available through Swagger at
  <http://127.0.0.1:8000/docs> (send the `X-Dev-User-Id` header, e.g. `1`).

### Not part of this phase

Android connectivity / real-time blocking engine, offline buffering,
Your Score / Rank / leaderboard, scoring formulas, and any new Web
features. (Reports are now implemented in Phase 13.)

## Phase 13 — Reports / Insights

Phase 13 implements the backend **reporting / insights layer** — read-only
daily, weekly and monthly reports calculated from EXISTING historical data
via SQL aggregation. No new tables, no schema changes, no Alembic migration
— the raw data remains the source of truth.

| Layer | Files | What it does |
| --- | --- | --- |
| Router | `app/routers/reports.py` | `GET /reports/daily`, `GET /reports/weekly`, `GET /reports/monthly`; reads the temporary dev identity from the `X-Dev-User-Id` header (shared `app/routers/deps.py`) |
| Schemas | `app/schemas/reports.py` | `ReportResponse` (period + study / monitoring / shorts / web sections + optional trend & comparison), `PeriodInfo`, `StudyMetrics`, `MonitoringMetrics`, `ShortsMetrics`, `WebMetrics`, `DailyTrendEntry`, `Comparison` |
| Service | `app/services/reporting.py` | `ReportingService` — period math, aggregation coordination, previous-period comparison, trend assembly, response assembly |
| Repository | `app/repositories/reports.py` | `ReportingRepository` — read-only SQL aggregations (SUM / COUNT / GROUP BY) over the existing tables; no writes |
| Wiring | `app/main.py` | `include_router(reports_router)` under the existing `SQLAlchemyError` handler |

### Report periods

- `GET /reports/daily?date=YYYY-MM-DD` — one UTC calendar day. `date`
  defaults to the server's current UTC date. Optional
  `include_comparison=false` drops the comparison block.
- `GET /reports/weekly?date=...` — the ISO week (Monday–Sunday) containing
  `date`, including a 7-entry `daily_trend` (Mon→Sun).
- `GET /reports/monthly?date=...` — the calendar month containing `date`,
  including a per-day `daily_trend` spanning the whole month.
- Dates are interpreted as UTC calendar dates (the backend's documented
  naive-UTC convention).

### Per-domain metrics

| Domain | Fields | Source tables |
| --- | --- | --- |
| study | `total_study_seconds`, `completed_sessions`, `cancelled_sessions`, `break_seconds`, `completed_breaks` | `study_sessions` (ended in period, terminal status; durations from `actual_duration_seconds` = `ended_at − started_at`), `break_sessions` (joined through the owning study session for user isolation) |
| monitoring | `total_app_usage_seconds`, `monitored_apps_count`, `monitoring_event_count`, `top_apps` (duration-ranked, max 5) | `app_usage`, `monitoring_events` |
| shorts | `total_shorts_count`, `total_duration_seconds`, `warning_count`, `limit_reached_count`, `platform_breakdown` | `shorts_usage` (daily summaries; warning/limit counts = days flagged) |
| web | `total_block_attempts`, `total_blocked_events`, `total_unblock_events`, `unique_blocked_domains` | `website_events` |

- **Study duration** comes from server-stored timestamps only
  (`actual_duration_seconds`), never client-supplied values, and the
  reporting layer runs no timers.
- **Shorts platform breakdown** reflects only platforms that actually have
data (Phase 11A `platform` / `surface` columns; pre-architecture rows appear
as the `UNKNOWN` marker). No platform is assumed to exist without data.
- **Top apps** are ranked purely by usage duration — an app is never
  labelled "distracting" because the schema has no category data for that.
- **No-data periods** return a valid all-zero structure (HTTP 200), never an
error, and days without data in a trend are honest zeros — missing
observations are never invented.

### Previous-period comparison

Every report can include a `comparison` block (default on) for the four
headline metrics — study time, Shorts time, app-usage time, block attempts —
comparing the current period with the previous equivalent one (previous day
/ previous ISO week / previous calendar month):

```json
{"study_seconds": {"current": 1200, "previous": 600, "change_percent": 100.0}}
```

`change_percent` is **None when the previous value is zero** (an explicit
not-applicable state) — a division by zero is never turned into a fake
percentage.

### Repository / service / router architecture

Router → Reporting Schema → Reporting Service → Reporting Repository →
SQLAlchemy → MySQL. Complex aggregations live in the dedicated read-only
`ReportingRepository` (SQL SUM / COUNT / GROUP BY — reports never load whole
tables into Python); the service owns period math, comparison and assembly;
no raw SQL lives in the service or router. Existing domain repositories are
not modified.

### User isolation

The user identity always comes from the development header — a
client-supplied `user_id` is never accepted. Every aggregation filters by
the current user, so reports can never include another user's data.

### No Score / Rank

Reports contain factual metrics only. **Your Score, Rank, leaderboard and
scoring formulas are intentionally NOT implemented** — a later Score Engine
will consume this data (or the underlying raw rows).

### Current development identity

The same temporary `X-Dev-User-Id` header (shared `app/routers/deps.py`) —
**development only, NOT a production authentication mechanism**. Cognito is
planned for a later phase and will replace only the identity/authentication
boundary. AWS deployment is planned even later.

### Verification

- `scripts/verify_reports.py` — seeds today's data through the real APIs
  (study session + break, shorts sync, app-usage sync + monitoring events,
  blocked website + web events) and seeds yesterday / previous-week /
  previous-month rows directly in MySQL, then verifies the daily / weekly /
  monthly values, platform breakdown, 7-day trend, previous-period
  comparisons (including the zero-guard), a no-data period, user isolation
  and direct-SQL cross-checks, plus a regression pass over Settings / Study
  / Monitoring / Shorts / Web. Start the server first:

  ```powershell
  cd backend
  .venv\Scripts\python -m uvicorn app.main:app --reload
  .venv\Scripts\python -m scripts.verify_reports
  ```

- Manual testing is also available through Swagger at
  <http://127.0.0.1:8000/docs> (send the `X-Dev-User-Id` header, e.g. `1`).

### Not part of this phase

Rank, leaderboard, scoring formulas, weekly/monthly report storage (no
summary tables), AWS deployment, Cognito, Android changes, new real-time
monitoring, and new data-collection engines. (Your Score is now specified in
Phase 14A — see below.)

## Phase 14A — Your Score Specification

**Status: SPECIFICATION AND VALIDATION ONLY. The production score engine is
NOT implemented and NOT deployed.** This phase designed, simulated and
documented the Your Score mathematical model; nothing in it modifies the
database, Android, the leaderboard, or any production scoring code.

| Deliverable | File | What it contains |
| --- | --- | --- |
| Specification | `docs/your_score_spec.md` | Full formal spec: range, weights, component formulas, caps, penalties, missing-data/inactivity rules, anti-gaming, aggregation, explanation output, leaderboard compatibility, required future changes |
| Validation | `scripts/score_spec_simulation.py` | Pure-Python implementation of the spec formulas + 6 profiles (A–F), sensitivity, distribution sweep, anti-gaming checks, read-only MySQL data-availability inspection |

### Score objective & factors

A 0–100 score summarizing productive behavior: study performance,
consistency, Shorts discipline, distracting-app control and web discipline.
It rewards **productive** behavior — a user who studies effectively
outperforms a user who merely uses the phone less — and **inactivity never
produces a perfect score**.

### Final recommended weights (validated)

| Component | Weight |
| --- | --- |
| Study performance | 40 |
| Shorts discipline | 25 |
| Distraction control | 20 |
| Web discipline | 10 |
| Consistency | 5 |

Formulas (summary — full math in the spec):

- **Study** = 0.6·completion + 0.4·capped volume. Only *meaningful* sessions
  (≥ 300 s, terminal status) count; volume caps at 150 min/day-equivalent
  (linear-to-cap — 12 h study earns no more than the target).
- **Shorts** = usage-weighted per-day discipline vs the user's configured
  daily limit (fallback 30 min): at/under limit → 1.0; 2× limit → 0;
  `warning_triggered`/`limit_reached` add small flag penalties.
- **Distraction** = usage-weighted phone-time moderation (4 h/day
  threshold). Explicit limitation: **no app categorization exists** — apps
  are NOT labelled distracting; only excessive total phone time is
  penalized (never rewarded below the full mark). A future categorization
  phase is required to make this app-aware.
- **Web** = 1.0 minus bounded persistence penalties (attempts capped 0.15,
  unblocks 0.10, repeat domains 0.10, floor 0.5). A single blocked attempt
  is NOT a sin — enforcement working is not punished; only persistence and
  giving in cost points.
- **Consistency** = min(1, active days / target) — days, never sessions.

### Aggregation & bands

Daily = one UTC day; weekly = ISO week; monthly = calendar month. Each
period's components are computed on the period's **aggregates directly**
(never summed daily scores), so the result is always 0–100. Bands: 90–100
Excellent, 75–89 Strong, 60–74 Moderate, 40–59 Needs improvement, 0–39 Poor
(keep for now; re-evaluate edges once real Android-synced data exists).

### Missing data & inactivity (validated rules)

- A component with no data contributes a **neutral 0.5** — never treated as
  perfect (no Shorts data ≠ perfect Shorts discipline) and never as zero.
- **Inactivity gate:** zero active days in the period → score 0 with
  `insufficient_data` status (doing nothing = 0, never 100).
- **Coverage:** fewer active days than required (3/week, 7/month) scales the
  score by active/required with a `partial_data` status — one good day
  cannot fabricate a good week.

### Anti-gaming (design-time)

Tiny sessions (< 300 s) excluded; volume capped; consistency counts days not
sessions; completion on meaningful sessions only; inactivity earns nothing;
Shorts discipline is limit-relative; web persistence backfires, single
encounters don't; distraction is a moderation penalty not a low-usage
reward. Future fraud rules (session-creation rate limits, open-without-
activity detection) are flagged but NOT built.

### Validation results (simulation)

| Profile | Score |
| --- | --- |
| A — high study, low Shorts, low distraction | 98 (Excellent) |
| B — low study, very low phone usage | 21 (Poor) |
| C — high Shorts, low study | 34 (Poor) |
| D — high study, moderate Shorts | 83 (Strong) |
| E — no meaningful activity | 0 (insufficient_data) |
| F — heavy study, extreme distraction | 75 (Strong) |

Fairness: A > D > F > C > B > E — studying beats mere low usage (A > B),
extreme distraction costs the full 20-point component (F < D), inactivity is
0, heavy Shorts ranks near the bottom. Sensitivity deltas are bounded
(+30 min study ≈ +1; crossing the Shorts limit ≈ +13; +1 violation day
≈ −8). Distribution sweep (150 grid combos): min 30, median 69, p90 89,
max 95 — no clustering at 100 or 0, useful separation.

### Current limitations

- **No app categorization** — distraction is phone-time moderation only.
- **Missing-data neutrality** is conservative: an active user who avoids
  Shorts entirely is scored neutrally, not rewarded (a future "avoidance
  credit" is recommended).
- **No real calibration data** — the dev DB holds no domain rows (verify
  scripts clean up), so validation used controlled realistic simulations;
  band edges and constants (limits, thresholds) must be recalibrated once
  the Android sync boundary lands.
- Planned-vs-actual study and schedule adherence are deferred (schedule
  "due-ness" needs recurrence computation).

### Leaderboard compatibility & schema impact

Weekly/monthly scores can later write into the existing `leaderboard_scores`
table (`period_type` = week/month, `period_start`/`period_end` = period
bounds) — the schema is **sufficient as-is** (no rank column; rank derived
later). **No schema change, no migration, no leaderboard logic in this
phase.**

### Implementation status (explicit)

**Score engine implementation is NOT yet deployed.** The future engine
phase adds a read-only `ScoreService` + `GET /score/*` endpoints returning
the explanation structure (per-component points, e.g. study 34/40), and
optionally writes weekly/monthly scores to `leaderboard_scores`. Until then
this spec is the reviewed contract for that phase.

### Verification

- `scripts/score_spec_simulation.py` — run with
  `.venv\Scripts\python -m scripts.score_spec_simulation`; prints the data
  availability inspection, profile scores, fairness/sensitivity/
  distribution/anti-gaming checks and exits non-zero on any failure. It is
  read-only (never writes to MySQL) and is NOT the production engine.

### Not part of this phase

Rank, leaderboard logic, schema changes, Android changes, AWS, Cognito, new
data collection. (The score engine is now implemented in Phase 14B.)

## Phase 14B — Your Score Engine

Phase 14B implements the **production Your Score engine** exactly per the
approved Phase 14A specification — no redesign, no new weights, no changed
range. The engine is read-only and deterministic; it never writes to the
database (scores are calculated on demand; `leaderboard_scores` is NOT
written) and contains no Rank/leaderboard logic.

| Layer | Files | What it does |
| --- | --- | --- |
| Router | `app/routers/score.py` | `GET /score/daily`, `GET /score/weekly`, `GET /score/monthly`; reads the temporary dev identity from the `X-Dev-User-Id` header (shared `app/routers/deps.py`) |
| Schemas | `app/schemas/score.py` | `ScoreResponse` (period — reuses `PeriodInfo` from reports — + score, status, components, activity, explanation) |
| Service | `app/services/scoring/score_service.py` | `ScoreService` — period math, data gathering, inactivity gate + coverage scaling, weighted assembly, clamp to 0–100, deterministic explanations |
| Components | `app/services/scoring/{study,shorts,distraction,web,consistency}_score.py` | Pure, deterministic component formulas (no DB access, no writes) |
| Queries | `app/services/scoring/queries.py` | Read-only SQL aggregations (grouped; no N+1) feeding the components |
| Constants | `app/services/scoring/constants.py` | The approved weights/constants (single source of truth, mirrors the spec) |
| Wiring | `app/main.py` | `include_router(score_router)` under the existing `SQLAlchemyError` handler |

### Score API

- `GET /score/daily?date=YYYY-MM-DD` — the current user's Your Score for
  one UTC day (default: today).
- `GET /score/weekly?date=...` — the ISO week (Mon–Sun) containing the date,
  computed on the week's aggregates directly (never summed daily scores), so
  the result stays in 0–100.
- `GET /score/monthly?date=...` — the calendar month containing the date,
  same direct aggregation.
- Every response carries the full breakdown so the client can explain the
  score:

```json
{"period": {"type": "weekly", "start_date": "2026-08-10", "end_date": "2026-08-16", "label": "2026-08-10 – 2026-08-16"},
 "score": 82, "status": "sufficient_data",
 "components": [{"name": "study", "value": 0.85, "status": "evaluated", "points": 34.0, "max": 40}, ...],
 "activity": {"active_days": 6, "required_days": 3, "coverage": 1.0},
 "explanation": {"summary": "Your Score: 82 (sufficient_data).",
                  "positives": [...], "negatives": [...]}}
```

### Approved components & weights (unchanged from Phase 14A)

| Component | Weight | Formula (summary) |
| --- | --- | --- |
| Study | 40 | 0.6·completion + 0.4·capped volume; meaningful sessions only (≥ 300 s, terminal); volume = min(1, total_min / (150·days)) |
| Shorts | 25 | usage-weighted per-day discipline vs the user's configured limit (fallback 30 min); 2× limit → 0; flag penalties 0.9/0.95 |
| Distraction | 20 | usage-weighted phone-time moderation (4 h/day threshold); enforcement-event penalty; NO app categorization (documented limitation) |
| Web | 10 | 1 − bounded persistence penalties (attempts ≤ 0.15, unblocks ≤ 0.10, repeats ≤ 0.10, floor 0.5); single blocked attempts not punished |
| Consistency | 5 | min(1, active_days / target) — days, never sessions; targets 1/5/20 |

### Missing data & inactivity (approved behavior)

- Missing component data → **neutral 0.5** (never perfect, never zero);
  shown as `status: "neutral"` in the breakdown.
- **Inactivity gate:** zero active days in the period → **score 0** with
  `status: "insufficient_data"` (doing nothing can never reach 100).
- **Coverage:** fewer active days than required (1 daily / 3 weekly /
  7 monthly) scales the score and returns `status: "partial_data"` with the
  coverage factor in `activity`.

### Anti-gaming (implemented per spec)

Sessions < 300 s excluded from study entirely; volume capped at 150
min/day-equivalent (12 h study earns no more than the target); consistency
counts distinct active days, never sessions; completion ratio on meaningful
sessions only; inactivity earns nothing; Shorts discipline is
limit-relative; web persistence (repeats/unblocks) costs points while single
encounters don't; distraction is a moderation penalty, not a low-usage
reward. (No aggressive fraud detection — future session-rate-limit rules
remain flagged.)

### Determinism, isolation, storage

- **Deterministic:** identical input data always produces the identical
  score, breakdown and explanation (verified by calling the same endpoint
  twice).
- **User isolation:** the score uses ONLY the current development user's
  data — the identity always comes from the header; another user's request
  returns 0 / `insufficient_data`, never their data.
- **No storage, no cache:** scores are calculated dynamically on each
  request (no cache unless performance testing later demonstrates a need);
  `leaderboard_scores` is not written; no rank, no leaderboard, no other
  users' scores anywhere.

### Verification

- `scripts/verify_score.py` — 83 checks: seeds controlled data at known
  dates, then compares every /score response against an **independent
  implementation of the approved formulas written in the script** (from the
  spec, not imported from the app). Covers: productive profile, imperfect /
  low-study–high-distraction day, high-Shorts week, high-study day,
  inactivity (0 / insufficient_data), missing-data neutrality, 0–100
  boundary, component breakdown (names/order/points/max), deterministic
  repeat, user isolation, and daily / weekly / monthly scores, plus a
  regression pass over Settings / Study / Monitoring / Shorts / Web /
  Reports.

  ```powershell
  cd backend
  .venv\Scripts\python -m uvicorn app.main:app --reload
  .venv\Scripts\python -m scripts.verify_score
  ```

- `scripts/score_spec_simulation.py` (Phase 14A) still passes unchanged —
  the engine matches the validated model.

### Not part of this phase

Weekly/monthly winners, other users' scores, score snapshot storage,
caching, AWS, Cognito, Android changes, schema changes (none made — Alembic
still `657ba9f4d4f8 (head)`). (Rank / leaderboard is now specified in Phase
15A — see below.)

## Phase 15A — Rank / Leaderboard Specification

**Status: SPECIFICATION AND VALIDATION ONLY. The production Rank engine is
NOT implemented and NOT deployed.** This phase designed, simulated and
documented the leaderboard architecture; nothing modifies production code,
the database, the approved score formula, or Android.

| Deliverable | File | What it contains |
| --- | --- | --- |
| Specification | `docs/rank_leaderboard_spec.md` | Full formal spec: source of truth, periods, eligibility matrix, competition ranking, deterministic tie-breaker, rank change, winner/top-3, display fields & privacy, dynamic-vs-snapshot, performance, proposed API contract, validation results |
| Validation | `scripts/rank_spec_simulation.py` | Pure-Python ranking + eligibility implementation validating cases A–H, determinism and fairness |

### Design decisions (final for this phase)

- **Source of truth:** the approved Your Score engine (Phase 14B) is the
  ONLY source of scores — `ScoreService.score(user, period)` → rank. No
  scoring formula is ever duplicated.
- **Eligibility:** a user is on the board only when opted in
  (`leaderboard_settings.is_opted_in = true` AND `is_enabled = true`; the
  default is NOT opted in, so nobody appears accidentally) AND their period
  score status is `sufficient_data` or `partial_data`. `insufficient_data`
  users are excluded (never ranked at 0); opted-out users are invisible.
- **Ranking method:** **competition ranking** — scores `[100, 100, 99]` →
  ranks `1, 1, 3` (ties share a rank, next rank skips). Winner = rank #1;
  Top 3 come from the SAME ranking pass (no separate podium algorithm).
- **Deterministic tie-breaker (ordering):** `(-score, -study points,
  -consistency points, user_id asc)` — ties share a rank, but podium and
  pagination order are fully deterministic (never DB-retrieval-random).
- **Rank change:** `previous_period_rank − current_period_rank` (positive =
  improved); `null` when the previous equivalent period has no data or the
  user was not eligible then (never an invented value).
- **Periods:** weekly = ISO week, monthly = calendar month — identical to
  the Score Engine and Reports (naive-UTC).
- **Dynamic vs snapshot:** first implementation is DYNAMIC (scores computed
  on demand via the Score Engine; `leaderboard_scores` is NOT written).
  Snapshotting + caching are deferred until a measured performance
  requirement justifies them.
- **Privacy:** entries expose only `rank`, `display_name` (fallback
  `"User {id}"` when empty), `score` and an opaque `user_id` — never email,
  phone, name, gender or profile fields.
- **API contract:** `GET /rank/weekly` and `GET /rank/monthly` (shared
  handler, path-based periods like the Reports API) returning period,
  `your_rank` / `your_score` / `your_score_status` / `rank_change`,
  `total_participants`, `winner`, `top_three`, paginated `entries`. Maps
  directly to the future Android Rank screen (Your Rank / Score / Change,
  Top-3 podium, full board, This Week / This Month).

### Validation results (simulation)

All cases PASS: A (10 users unique scores → ranks 1..10), B (ties 100,100,99
→ competition ranks 1,1,3 with deterministic study-points tie-break), C
(current user ranked 15th is identifiable outside page 1), D (opted-out user
excluded, gets no invented rank), E (`insufficient_data` user excluded,
`partial_data` eligible), F (rank 8 → 3 ⇒ `rank_change +5`), G (rank 4 → 6
⇒ `rank_change −2`), H (previous period empty ⇒ `rank_change null`).
Determinism: every case produces byte-identical output on repeat runs.
Fairness: higher score always ranks at-or-above lower score.

### Database impact

**No schema change required.** `leaderboard_settings` already carries
opt-in / enable / display_name; `leaderboard_scores` (no `rank` column;
rank is derived from score ordering) is untouched and not written in this
phase.

### Implementation status (explicit)

**The Rank engine is now IMPLEMENTED in Phase 15B** (see below) exactly per
this specification: read-only `RankService` + `GET /rank/weekly|monthly`,
consuming the Score Engine as the only score source. Score snapshotting into
`leaderboard_scores` and caching remain deferred until a measured
performance requirement justifies them.

### Verification

- `scripts/rank_spec_simulation.py` — run with
  `.venv\Scripts\python -m scripts.rank_spec_simulation`; validates cases
  A–H plus determinism and fairness, exits non-zero on any failure.
  Read-only, never touches MySQL, NOT the production engine.

### Not part of this phase

Leaderboard responses, score snapshots, caching, Android changes, AWS,
Cognito, schema changes. (The Rank engine itself is Phase 15B — below.)

## Phase 15B — Rank / Leaderboard Engine

Phase 15B implements the **production Rank / Leaderboard engine** exactly
per the approved Phase 15A specification (`docs/rank_leaderboard_spec.md`).
The Score Engine (Phase 14B) remains the **ONLY source of score values** —
this layer never computes or copies score formulas; it only ranks scores.

| Layer | Files | What it does |
| --- | --- | --- |
| Router | `app/routers/rank.py` | `GET /rank/weekly` + `GET /rank/monthly` (shared handler); reads the temporary dev identity from the `X-Dev-User-Id` header (shared `app/routers/deps.py`) |
| Service | `app/services/rank.py` | `RankService`: eligibility resolution, competition ranking, deterministic tie-break ordering, winner / top three from the same pass, current-user rank, rank change vs previous period, pagination, privacy-safe views |
| Repository | `app/repositories/rank.py` | Read-only: `eligible_user_ids()` (opt-in + enabled) and `display_names()` — no writes |
| Batch scoring | `app/services/scoring/batch.py` | `batch_scores()` — one grouped-SQL pass per period for ALL users, reusing the EXACT same `assemble_score` helpers as `GET /score/*` (no N+1, no formula drift) |
| Schemas | `app/schemas/rank.py` | `RankEntry` (rank / display_name / score / user_id), `RankPagination`, `RankResponse` per the Phase 15A contract |
| Wiring | `app/main.py` | `include_router(rank_router)` under the existing `SQLAlchemyError` handler |

### API

- `GET /rank/weekly?date=YYYY-MM-DD&page=1&page_size=20` — this ISO week's
  board (Monday–Sunday, naive UTC — identical period interpretation to the
  Score Engine and Reports).
- `GET /rank/monthly?date=YYYY-MM-DD&page=1&page_size=20` — this calendar
  month's board, same contract.
- Response: `period`, `your_rank`, `your_score`, `your_score_status`,
  `rank_change`, `total_participants`, `winner`, `top_three` (first three
  rows of the same ranked list), paginated `entries` with global ranks, and
  `pagination` (`page` / `page_size` / `total_pages`).

### Behavior (approved Phase 15A rules)

- **Eligibility:** a user appears only when `leaderboard_settings` has
  `is_opted_in = true` AND `is_enabled = true` AND their period score status
  is `sufficient_data` / `partial_data`. Opted-out, disabled and
  `insufficient_data` users are excluded (never ranked at 0, never given an
  invented rank).
- **Ranking method:** competition ranking — `[100, 100, 99]` → ranks
  `1, 1, 3`; the rank value comes from score alone.
- **Tie-breaker (ordering):** `(-score, -study points, -consistency points,
  user_id asc)` — ties share a rank but podium/pagination order is fully
  deterministic.
- **Winner:** rank #1 of the period (null when no eligible users); **Top
  three:** the first three rows of the SAME ranked pass — never a separate
  algorithm.
- **Rank change:** `previous_period_rank − current_period_rank` (positive =
  improved); `null` when the previous equivalent period has no board data or
  the user was not eligible then — never an invented value.
- **Current user:** `your_rank` / `your_score` are present even when the
  user is far outside the visible page (rank is computed across ALL eligible
  users); when not eligible they are `null` and `your_score_status` explains
  why (`not_opted_in` / `insufficient_data`).
- **Privacy:** entries expose only `rank`, `display_name` (fallback
  `"User {id}"` when empty), `score` and an opaque `user_id` — never email,
  phone or private profile fields. The current user's detailed component
  breakdown stays in the Score API.

### Dynamic board (no storage, no caching)

The board is computed on demand: batch scores for every eligible user of the
period, then ranked. **`leaderboard_scores` is NOT written** and no caching
is added — snapshotting is deferred until a measured performance requirement
justifies it (the existing table remains available for that future layer and
has deliberately no `rank` column; rank is always derived from ordering).

### Score Engine remains the source of truth

`batch_scores()` runs grouped SQL once per period (study / shorts / app /
web / active-day aggregations per user in a handful of queries — no N+1)
and feeds each user's data through the same module-level `assemble_score`
logic behind `GET /score/*`. A leaderboard score is byte-identical to the
single-user score API for the same user and period; nothing is duplicated.

### MySQL persistence & database impact

No schema change, no new tables, no Alembic migration (still
`657ba9f4d4f8 (head)`). `leaderboard_settings` was already sufficient
(opt-in / enable / display_name); `leaderboard_scores` is untouched. The
script verifies direct MySQL state (rows seeded, cleaned up afterwards).

### Verification

- `scripts/verify_rank.py` — end-to-end (47 checks): cases A (unique
  scores), B (ties → 1,1,3 + deterministic order), C (top three), D
  (current user outside page 1), E (opted-out excluded), F (disabled
  excluded), G (insufficient-data excluded), H (rank increase), I (rank
  decrease), J (no previous-period data → `null`), K (weekly), L (monthly),
  M (pagination with global ranks), N (deterministic repeat), O (winner),
  plus privacy/public-field checks, display-name fallback, not-opted-in and
  insufficient-data current users, and full regression of Settings / Study /
  Monitoring / Shorts / Web / Reports / Score. Every rank is compared
  against an INDEPENDENT implementation of the Phase 15A logic written in
  the script (RankService is not imported). Start the server first:

  ```powershell
  cd backend
  .venv\Scripts\python -m uvicorn app.main:app --reload
  .venv\Scripts\python -m scripts.verify_rank
  ```

- Manual testing is also available through Swagger at
  <http://127.0.0.1:8000/docs> (send the `X-Dev-User-Id` header, e.g. `1`).

### Not part of this phase

Score snapshots / `leaderboard_scores` writes, caching, weekly/monthly
winner announcement logic, Android Rank-screen integration (a later sync
phase), AWS, Cognito, schema changes.

## Phase 16 — Android ↔ Backend Synchronization

Phase 16 adds the **Android ↔ backend synchronization layer**: the Android
app now pushes its local data to the already-built FastAPI APIs and reads
server-authoritative Reports / Your Score / Rank. Android remains the
**real-time authority** for the study timer, monitoring, Shorts detection
and web blocking; the backend remains authoritative for persisted
historical data, Reports, Your Score and Rank. No existing Android engine
was replaced and no backend endpoint was changed.

### Android network layer

| Layer | Files | What it does |
| --- | --- | --- |
| Config | `app/src/main/java/com/shortscap/app/network/BackendConfig.kt` | Base URL config: emulator host `http://10.0.2.2:8000/` (NOT `127.0.0.1`), overridable via a build config / env-style constant for local device vs future staging/production; centralized timeouts and the temporary dev identity header (`X-Dev-User-Id`) |
| API | `app/src/main/java/com/shortscap/app/network/BackendApi.kt` + `HttpBackendApi.kt` | Single HTTP client (project convention — `HttpURLConnection`, no Retrofit/OkHttp was present, nothing new added): JSON in/out, 2xx/4xx/5xx handling, every backend endpoint the app uses |
| DTOs | `app/src/main/java/com/shortscap/app/network/Dtos.kt` | Request/response models matching the backend schemas 1:1 (settings, study, monitoring, shorts, web, reports, score, rank) |
| Results | `app/src/main/java/com/shortscap/app/network/ApiResult.kt` | `Success` / `HttpError` / `NetworkError` sealed result — loading/error/empty states map cleanly onto it |

### Sync core (offline-first, reusable)

- `app/src/main/java/com/shortscap/app/sync/SyncModels.kt` — `SyncRecord` with
  states `PENDING → SYNCING → SYNCED / FAILED` and an opaque dedupe `key`.
- `app/src/main/java/com/shortscap/app/sync/SyncQueue.kt` — minimal in-memory
  FIFO queue + index by dedupe key (a record already queued/synced for the
  same key is not enqueued twice).
- `app/src/main/java/com/shortscap/app/sync/SyncManager.kt` — orchestrates
  draining via an injectable `SyncDispatcher`: bounded retry with backoff for
  transient failures (timeout / network / 5xx), **no** retry for 400/401/403/
  404/422, marks `SYNCED` only on success, never drops records on failure.
- `app/src/main/java/com/shortscap/app/sync/SyncJson.kt` — tiny pure-Kotlin
  JSON builder (keeps the syncers JVM-testable without Android runtime deps).
- `app/src/main/java/com/shortscap/app/sync/Syncers.kt` — one small syncer per
  domain (settings, study schedule/session/break/event, monitoring
  usage/events, shorts usage/events incl. platform + surface, web events) and
  a `RoutingDispatcher` mapping each record kind to its backend call.
- `app/src/main/java/com/shortscap/app/sync/ReadClients.kt` — read-only
  clients for Reports, Score and Rank with a small in-memory cache
  (clearly separated: cached local copy vs server-authoritative data).
- `app/src/main/java/com/shortscap/app/sync/SyncCoordinator.kt` — ties the
  queue, dispatcher and read clients together (enqueue, drain, fetch/refresh).

### Repository seams (graceful fallback)

- `SettingsRepository` — after a local save, enqueues the corresponding
  `PUT /settings/*` record; on refresh, pulls `GET /settings` (and
  `/monitoring` `/shorts` `/notifications` `/leaderboard` `/permissions`)
  and applies server values to local state. Conflict policy: **local user
  change is authoritative immediately; a successful backend response
  confirms persistence; server values are used on initial/refresh sync and
  never silently overwrite a fresh local change.**
- `StudyRepository` — schedule create/update/delete, session start/end,
  break start/end and study events are enqueued as sync records. The study
  TIMER stays fully local (Phase 8 behavior unchanged).
- `ShortsMonitoringPipeline` — the Phase 11B local Shorts records (platform
  + surface + duration + count retained) drain through the sync layer on
  `POST /shorts/usage/sync` / `POST /shorts/events`.
- `WebRepository` — web events (BLOCK_ATTEMPT / BLOCKED / UNBLOCKED) enqueue
  on `POST /web/events`; blocked-website configuration can be pulled via
  `GET /websites/blocked`. Blocking itself stays fully local.
- Every seam degrades gracefully: if the backend is unreachable the app
  continues to work exactly as before (records stay queued, nothing is
  discarded).

### API mapping (what syncs where)

| Android data | Backend API | Direction |
| --- | --- | --- |
| Settings | `GET/PUT /settings`, `GET/PUT /settings/{monitoring,shorts,notifications,leaderboard,permissions}` | both |
| Study schedules / sessions / breaks / events | `/study/schedules`, `/study/sessions/...`, `/study/breaks/...`, `/study/events` | push + pull |
| App usage / monitoring events | `POST /monitoring/app-usage/sync`, `GET /monitoring/app-usage`, `POST/GET /monitoring/events` | push + pull |
| Shorts usage / events (platform + surface) | `POST /shorts/usage/sync`, `GET /shorts/usage`, `POST/GET /shorts/events` | push + pull |
| Web events / blocked sites | `POST /web/events`, `GET /websites/blocked` | push + pull |
| Reports | `GET /reports/daily|weekly|monthly` | pull (read-only) |
| Your Score | `GET /score/daily|weekly|monthly` | pull (read-only) |
| Rank / Leaderboard | `GET /rank/weekly|monthly` | pull (read-only) |

### Offline-first + retry + dedupe

Local capture → local queue → when the network is available → upload →
success → mark `SYNCED`. A temporarily unavailable backend never causes
local data loss. Retries are bounded with backoff and only for transient
errors; client errors are surfaced immediately. Duplicate uploads are
prevented two ways: the Android dedupe key per logical record AND the
backend's existing idempotent sync endpoints (same usage summary synced
twice → one row).

### Development identity (temporary)

For local development only, the API client sends the backend's temporary
`X-Dev-User-Id` header (centralized in `BackendConfig` — no fake login, no
JWT, no OTP). Cognito will later replace this identity boundary; the header
lives in one place so the swap is a single-file change.

### Verification

- `backend/scripts/verify_sync_contracts.py` — end-to-end (86 checks) over
  EVERY contract the Android client uses: settings fetch/update/refresh,
  study schedule/session/break/event sync, monitoring usage + event sync +
  duplicate handling, shorts usage + event sync with platform/surface
  retained, web events, reports/score/rank retrieval for daily/weekly/
  monthly, user isolation, and full regression of all previous layers.
- Android unit tests: `app/src/test/java/com/shortscap/app/sync/SyncManagerTest.kt`
  — 10 tests (queue states, dedupe, bounded retry with backoff, no-retry on
  4xx, offline capture → drain on restore, success-only `SYNCED` marking,
  dispatcher routing); run with `./gradlew :app:testDebugUnitTest`.
- Build: `./gradlew :app:compileDebugKotlin` — compiles cleanly.

### Not part of this phase

Cognito / real authentication, AWS deployment, the security-hardening phase,
Room/DataStore-based durable sync queue (the queue is in-memory for this
phase; the sync layer is designed so a durable queue can replace it without
changing the syncers), Android UI redesigns, backend schema changes (none —
Alembic still `657ba9f4d4f8 (head)`).

## Cross-Platform Short-Form Content Architecture

**Status: architecture locked and documented. A full universal Shorts
detector is NOT implemented** — this section describes the abstraction that
future platform-specific detection plugs into, and the honest detection
level each platform currently supports.

Short-form content must NOT be treated as a YouTube-only / single-app
feature. The architecture distinguishes **platform** from **content surface**
and keeps detection separate from counting, so ShortsCap is
platform-independent and future platforms can be added safely.

### Platform vs surface

- **Platform** — the app hosting the content (`ShortPlatform`: YOUTUBE,
  INSTAGRAM, TIKTOK, SNAPCHAT, FACEBOOK, MOJ, X, LINKEDIN, UNKNOWN).
  Identified by package name (centralized — never scattered through
  monitoring code).
- **Surface** — the specific short-form place inside a platform
  (`ShortSurface`: YOUTUBE_SHORTS, INSTAGRAM_REELS, FACEBOOK_REELS,
  TIKTOK_SHORT_FEED, SNAPCHAT_SPOTLIGHT, X_SHORT_VIDEO,
  LINKEDIN_SHORT_VIDEO, MOJ_SHORT_VIDEO, UNKNOWN).

A platform may contain short-form, long-form, live, chat, stories and other
screens — so **"app is running" never means "a Short is being watched"**.
Correct classification needs platform detection + surface detection +
interaction/time signals.

### Android-side abstraction (`app/src/main/java/com/shortscap/app/shorts/`)

| File | Responsibility |
| --- | --- |
| `ShortPlatform.kt` | Typed platform enum + centralized package→platform mapping |
| `ShortSurface.kt` | Typed content-surface enum (per-platform short-form places) |
| `ShortDetectionResult.kt` | Detection outcome: platform, surface, `isShortForm`, confidence, `DetectionMethod` (PLATFORM_ADAPTER / GENERIC_UI_SIGNAL / INTERACTION_SIGNAL / UNKNOWN), timestamp, metadata |
| `ShortDetectionSignals.kt` | What the existing architecture can actually provide (package name today; optional activity class, foreground duration, interaction count, visible descriptors for future detectors) |
| `ShortPlatformAdapter.kt` | Interface: `supports(package)` + `detect(signals) → ShortDetectionResult` — answers “is this my platform? / which surface? / how confident?” |
| `YouTubeShortsAdapter.kt` | YouTube — surface detected via the Shorts player activity class (high confidence); otherwise UNKNOWN |
| `InstagramReelsAdapter.kt` / `FacebookReelsAdapter.kt` / `SnapchatSpotlightAdapter.kt` / `XVideoAdapter.kt` / `LinkedInVideoAdapter.kt` | Platforms where Reels/Spotlight/video is one surface among many — platform identified, surface UNKNOWN (conservative, nothing counted yet) |
| `TikTokAdapter.kt` / `MojAdapter.kt` | Predominantly short-form apps — platform identified, surface UNKNOWN / medium confidence (LIVE & other surfaces exist) |
| `GenericShortVideoAdapter.kt` | Conservative fallback for unknown/future apps — returns UNKNOWN, never fabricates a detection |
| `ShortPlatformRegistry.kt` | Central registry: package → adapter map, generic fallback, `detect(signals)` entry point |
| `ShortUsageAggregator.kt` | Separates DETECTION from COUNTING; preserves the 3–5 second rule; only counts explicitly short-form, high-confidence results |
| `ShortsBudgetTracker.kt` | ONE global Shorts budget across ALL platforms + per-platform breakdown for future reports |

### Detection vs counting

`ShortContentDetector` (adapters/registry) determines WHAT is being viewed;
`ShortUsageAggregator` determines WHAT counts. Flow:

```
content appears -> detector -> ShortDetectionResult -> aggregator ->
count / duration -> warning / limit state -> event -> backend sync
```

Detection rules can change without rewriting usage accounting, and the
detector never owns the global counter.

### Existing 3–5 second rule (preserved)

- Swipe/change within ~2 seconds (`SHORT_SWIPE_RULE_MILLIS`) → NOT counted.
- Meaningful engagement reaching the 3–5 second threshold
  (`SHORT_MIN_ENGAGEMENT_MILLIS` … `SHORT_MAX_ENGAGEMENT_WINDOW_MILLIS`) →
  eligible to count as one Short; longer engagement is still one Short with
  its full duration retained.
- Thresholds are documented constants (configurable later through settings
  without touching call sites). No duplicate timing system was created.

### Global Shorts budget

Shorts from every platform contribute to ONE combined budget (e.g. YouTube
Shorts 10 min + Instagram Reels 8 min + TikTok 5 min + Spotlight 4 min =
27 min global Shorts time). No per-platform independent limits unless a
future product requirement explicitly adds them. Platform/surface data is
retained after counting so future reports can break usage down per platform.

### Current detection capability (honest)

- **Fully reliable today:** nothing is claimed as 100% accurate.
- **Surface-positive only for YouTube Shorts** (activity-class signal).
- **All other platforms:** platform identity is known (package-based), the
  surface is UNKNOWN and nothing is counted — until future interaction/UI
  signal sources raise confidence.
- The accessibility service remains privacy-minimal: it observes only
  `TYPE_WINDOW_STATE_CHANGED` (foreground package), never window content,
  and performs no synthetic interaction.

### Backend as persistence / synchronization layer

Android remains the real-time authority (detection, counting, enforcement,
notifications, local buffering). The backend (Phase 10 Shorts Data Layer)
receives synchronized summaries and events, validates, persists and serves
history/summaries. **No real-time server detection** — no Python screen
monitoring, no server-side accessibility, no WebSockets, no timers, no
polling.

### Database review — platform/surface support

Inspected `shorts_usage` / `shorts_events` during the architecture lock:

| Item | State at lock time | State after Phase 11A |
| --- | --- | --- |
| `shorts_events.metadata_json` | JSON column — carries `platform` / `surface` / `detectionMethod` / `confidence` today (no schema change needed) | unchanged |
| `shorts_usage` columns | no `platform` / `surface` columns | **`platform` + `surface` added (VARCHAR(50) NOT NULL)** via migration `657ba9f4d4f8` |
| Idempotency key | user + device + usage_date | **user + device + platform + surface + usage_date** (unique constraint) |

See [Phase 11A — Shorts Usage Database Schema Update](#phase-11a--shorts-usage-database-schema-update).

### Future platform extensibility

Adding a future platform (e.g. `ExampleFutureVideoApp`) requires only:
1) a new `ShortPlatform` value, 2) a new `ShortSurface` value if needed,
3) a new adapter, 4) one registry entry. The aggregator, backend sync,
reporting and ranking keep working unchanged.

### Existing implementation mapping (STEP 1 review)

| Current file | Current responsibility | New cross-platform responsibility |
| --- | --- | --- |
| `monitoring/MonitoringService.kt` | Foreground service keeping monitoring alive | Unchanged — no detection logic added |
| `monitoring/MonitoringEventHub.kt` | Funnel for foreground-app events (doc note added) | Future Shorts detector subscribes here; hub stays a dumb funnel |
| `accessibility/ShortsCapAccessibilityService.kt` | Package-only window-state observation + Brain overlay | Unchanged — future detector consumes the same events via the hub |
| `monitoring/BrainOverlayManager.kt` (`SupportedShortVideoPackages`) | Overlay show/hide package set | Unchanged; registry centralizes recognition going forward |
| `activity/ActivityRepository.kt` / `ActivityModels.kt` | Seeded usage reports | Unchanged; future Shorts reports consume aggregator/budget data |
| `model/Models.kt` (`ShortVideoPlatform`) | Settings UI platform catalog (with `enabled`) | Unchanged; the new typed enums are the detection-side model |

## Database Migration

- **Alembic is configured** (`alembic.ini` + `migrations/`) and connected to
  the app's single SQLAlchemy `Base` metadata (`target_metadata =
  Base.metadata`). The database URL comes from the environment
  (`app.config.settings`) — it is not hardcoded and passwords are never
  written here.
- **Initial migration created:** revision **`70d943e5af25`** —
  *"create approved schema tables"* — generated from the 24 approved models.
- **Migration applied:** `alembic upgrade head` — `alembic current` shows
  `70d943e5af25 (head)`.
- **Phase 11A migration created:** revision **`657ba9f4d4f8`** —
  *"add platform and surface to shorts_usage"* — adds `platform` / `surface`
  (VARCHAR(50) NOT NULL, `UNKNOWN` marker default) and the unique constraint
  `uq_shorts_usage_user_device_platform_surface_date`; scoped to
  `shorts_usage` only. Applied and verified (`alembic current` =
  `657ba9f4d4f8 (head)`).
- **Actual MySQL tables created** in `shortscap_db` (MySQL 8.0.43): the 24
  approved tables (`users`, `user_profiles`, `auth_identities`,
  `otp_verifications`, `devices`, `user_settings`, `permission_states`,
  `study_schedules`, `study_sessions`, `break_sessions`, `study_events`,
  `monitoring_settings`, `app_usage`, `monitoring_events`, `shorts_settings`,
  `shorts_usage`, `shorts_events`, `blocked_websites`, `website_events`,
  `notification_preferences`, `notification_events`, `feedback`,
  `leaderboard_settings`, `leaderboard_scores`), plus Alembic's own
  `alembic_version` tracking table. Verified via `SHOW TABLES`, `DESCRIBE`,
  foreign-key and index checks.
- **Source of truth:** the SQLAlchemy models are the source schema; Alembic
  manages schema changes. Future database changes MUST go through Alembic
  migrations (`alembic revision --autogenerate` + `alembic upgrade head`) —
  avoid manual schema changes.

## Planned / next (NOT implemented yet)

Android → backend sync (settings, study, monitoring, shorts — including
draining the Phase 11B local Shorts store to the shorts APIs), OTP / Google /
JWT / Cognito auth endpoints (replaces the temporary `X-Dev-User-Id`), the
real-time device-monitoring / shorts / web-blocking engines and the study
schedule/reminder engine, reports, leaderboard scoring, analytics, AWS
deployment, and notification backends — each one at a time.

## Notes

- The Android app already contains **local** engines/services (monitoring,
  shorts, study, restriction, website blocking, notifications). The
  `app/engines/` folders here are **reserved for server-side processing and
  synchronization** — they are NOT replacements for the Android local
  engines and no Android logic is duplicated.
- The Android `app/` directory is untouched by the backend work.