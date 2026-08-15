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
> tables). Auth, OAuth, engines, and the remaining routers are implemented in
> later phases, one at a time.

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
weekly/monthly reports, Your Score / Rank / leaderboard, scoring formulas,
and any new Web features.

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