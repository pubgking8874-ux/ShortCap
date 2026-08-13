# ShortsCap Backend

Server backend for the ShortsCap app (Python + FastAPI + SQLAlchemy + MySQL).

> **Status:** Phase 2 (running FastAPI server) + Phase 3 (database foundation +
> environment configuration): `.env` in place, SQLAlchemy connection layer,
> database health endpoint, and the first `User` model are implemented. Auth,
> OAuth, engines, routers, and migrations are implemented in later phases, one
> at a time.

## Reserved technology stack

- **Python** — language (3.14)
- **FastAPI** — API framework
- **SQLAlchemy 2.x** — ORM
- **pydantic-settings / python-dotenv** — env-driven configuration
- **Alembic** — database migrations (configured later)
- **MySQL** — database (local dev MySQL 8.0.43; AWS RDS for production)
- **PyMySQL** — MySQL driver

## Quick start

```powershell
cd backend
.venv\Scripts\python -m pip install -r requirements.txt
Copy-Item .env.example .env   # or edit the existing .env
# Set DB_PASSWORD in .env to the local MySQL root password (never committed)
.venv\Scripts\python -m uvicorn app.main:app --reload
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
  and `DB_PASSWORD` (currently blank — fill it manually).
- `backend/.env.example` — committed template; never contains real secrets.
- Root and backend `.gitignore` both ignore `.env` / `.env.*`.

## Structure

```
backend/
├── app/                     # FastAPI application
│   ├── main.py              # entry point + /health/db endpoint (running FastAPI app)
│   ├── config.py            # pydantic-settings, env-driven (Phase 3)
│   ├── database.py          # SQLAlchemy engine/session/Base/get_db (Phase 3)
│   ├── models/              # SQLAlchemy models (user.py implemented; rest placeholders)
│   ├── schemas/             # Pydantic schemas (placeholders)
│   ├── routers/             # API routes (placeholders)
│   ├── services/            # business services (placeholders)
│   ├── engines/             # server-side processing engines (placeholders)
│   ├── repositories/        # database access layer (placeholders)
│   ├── auth/                # OTP / Google / JWT / passwords (placeholders)
│   ├── middleware/          # security + logging middleware (placeholders)
│   └── utils/               # datetime / validation / response helpers
├── scripts/
│   └── check_db.py          # real MySQL connectivity check (Phase 3)
├── migrations/              # Alembic (configured later)
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
  - `app/models/user.py` — first model `User` (table `users`): `id`, `name`,
    `email` (unique), `phone` (unique/optional, for mobile OTP login), `gender`,
    `date_of_birth`, `created_at`, `updated_at`. *(No tables are created.)*
  - `scripts/check_db.py` — connectivity check (honest `success` /
    `not_configured` result).
  - `.env` — created with local DB config (password blank, for manual entry).
  - `requirements.txt` — `fastapi`, `uvicorn`, `pydantic-settings`,
    `SQLAlchemy`, `PyMySQL` (python-dotenv ships with pydantic-settings).

## Connection status

- Local MySQL 8.0.43 installed, `MySQL80` service running, database
  `shortscap_db` created. The backend `.env` targets it, but `DB_PASSWORD` is
  **blank pending manual entry**, so the current real status is
  `not_configured`.
- **AWS RDS production: NOT CONFIGURED** (no RDS instance provisioned).

## Planned / next (NOT implemented yet)

Data models/tables beyond `users`, migrations, OTP / Google / JWT auth
endpoints, monitoring / study / shorts / web-blocking engines, sync endpoints,
analytics, and notification backends — each one at a time.

## Notes

- The Android app already contains **local** engines/services (monitoring,
  shorts, study, restriction, website blocking, notifications). The
  `app/engines/` folders here are **reserved for server-side processing and
  synchronization** — they are NOT replacements for the Android local
  engines and no Android logic is duplicated.
- The Android `app/` directory is untouched by the backend work.