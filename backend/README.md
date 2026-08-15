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
> shorts events / shorts summary on the existing approved tables). Auth,
> OAuth, engines, and the remaining routers are implemented in later
> phases, one at a time.

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

Android → backend sync (settings, study, monitoring, shorts), OTP / Google /
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