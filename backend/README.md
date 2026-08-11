# ShortsCap Backend

Server backend for the ShortsCap app (Python + FastAPI + SQLAlchemy + MySQL).

> **Status:** Phase 2 (running FastAPI server) + Phase 3 (database foundation):
> configuration, SQLAlchemy connection layer, and the first `User` model are
> implemented. Auth, OAuth, engines, routers, and migrations are implemented in
> later phases, one at a time.

## Reserved technology stack

- **Python** — language (3.14)
- **FastAPI** — API framework
- **SQLAlchemy 2.x** — ORM
- **Alembic** — database migrations (configured later)
- **MySQL** — database (local dev; AWS RDS for production)
- **PyMySQL** — MySQL driver

## Quick start

```powershell
cd backend
.venv\Scripts\python -m pip install -r requirements.txt
Copy-Item .env.example .env   # then fill in real DB credentials
.venv\Scripts\python -m uvicorn app.main:app --reload
```

Open <http://127.0.0.1:8000/> and <http://127.0.0.1:8000/docs>.

## Database connectivity check

```powershell
.venv\Scripts\python -m scripts.check_db
```

Reports the **real** connection state — it never fabricates success. With no
valid `.env` credentials it reports `not_configured` (e.g. access denied),
which is the expected output until local MySQL / AWS RDS credentials are set.

## Structure

```
backend/
├── app/                     # FastAPI application
│   ├── main.py              # entry point (running FastAPI app)
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
├── .env.example
├── alembic.ini
└── README.md
```

## Implemented so far

- **Phase 2 — running server:** minimal FastAPI app at `app/main.py` with a
  `GET /` health response; verified via Uvicorn + Swagger `/docs`.
- **Phase 3 — database foundation:**
  - `app/config.py` — `pydantic-settings` `Settings` (env-driven; `.env`
    supported). No credentials in source.
  - `app/database.py` — SQLAlchemy `create_engine` (MySQL via PyMySQL,
    `pool_pre_ping`, `pool_recycle`), `Base` (declarative), `SessionLocal`,
    FastAPI `get_db()` dependency, and `check_database_connection()` that
    performs a real query.
  - `app/models/user.py` — first model `User` (table `users`): `id`, `name`,
    `email` (unique), `phone` (unique/optional, for mobile OTP login), `gender`,
    `date_of_birth`, `created_at`, `updated_at`.
  - `scripts/check_db.py` — connectivity check (honest `success` /
    `not_configured` result).
  - `requirements.txt` updated with `pydantic-settings`, `SQLAlchemy`, `PyMySQL`.

## Connection status

- Local MySQL server exists (`MySQL80` service) but **no valid `.env`
  credential puts the backend in a connected state**.
- **AWS RDS production: NOT CONFIGURED** (no RDS instance provisioned).

## Notes

- The Android app already contains **local** engines/services (monitoring,
  shorts, study, restriction, website blocking, notifications). The
  `app/engines/` folders here are **reserved for server-side processing and
  synchronization** — they are NOT replacements for the Android local
  engines and no Android logic is duplicated.
- The Android `app/` directory is untouched by the backend work.