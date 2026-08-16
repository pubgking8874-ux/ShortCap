from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy.exc import SQLAlchemyError

from app.config import settings
from app.database import check_database_connection
from app.middleware.logging import RequestLoggingMiddleware
from app.middleware.security import SecurityHeadersMiddleware
from app.routers.monitoring import router as monitoring_router
from app.routers.rank import router as rank_router
from app.routers.reports import router as reports_router
from app.routers.score import router as score_router
from app.routers.settings import router as settings_router
from app.routers.shorts import router as shorts_router
from app.routers.study import router as study_router
from app.routers.web import blocked_websites_router, web_events_router

app = FastAPI(title="ShortsCap Backend")

# ---- Security hardening (Phase 19) ----
# Order: later add_middleware calls wrap earlier ones, so the security
# headers + sanitized logging sit on the outside of CORS/host checks.
app.add_middleware(RequestLoggingMiddleware)  # sanitized access log (DEBUG only)
app.add_middleware(SecurityHeadersMiddleware)  # nosniff / frame / referrer

# Environment-aware CORS: configured origins only; a wildcard is rejected
# outside development (see Settings.cors_allow_origins). Empty list = no
# cross-origin browser access (native Android clients are unaffected).
cors_origins = settings.cors_allow_origins
if cors_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=cors_origins,
        allow_credentials=False,  # no cookies/authorization headers used
        allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
        allow_headers=["Content-Type", "X-Dev-User-Id"],
    )

# Configurable trusted-host validation for deployments (empty = not
# enforced yet, ready to be set via ALLOWED_HOSTS without code changes).
allowed_hosts = settings.allowed_hosts
if allowed_hosts:
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=allowed_hosts)

# Phase 6 — settings data layer (GET /settings, PUT /settings).
app.include_router(settings_router)

# Phase 8 — study data layer (schedules / sessions / breaks / events).
app.include_router(study_router)

# Phase 9 — monitoring data layer (app usage sync / events / summary).
app.include_router(monitoring_router)

# Phase 10 — shorts data layer (usage sync / events / summary).
app.include_router(shorts_router)

# Phase 12 — web data layer (blocked websites + website events / summary).
app.include_router(blocked_websites_router)
app.include_router(web_events_router)

# Phase 13 — reporting / insights layer (read-only daily/weekly/monthly
# aggregations over existing historical data).
app.include_router(reports_router)

# Phase 14B — Your Score engine (read-only daily/weekly/monthly score
# calculation per the approved Phase 14A specification).
app.include_router(score_router)

# Phase 15B — Rank / Leaderboard engine (weekly/monthly dynamic board per
# the approved Phase 15A specification; the Score Engine is the only source
# of score values; `leaderboard_scores` is not written).
app.include_router(rank_router)


@app.exception_handler(SQLAlchemyError)
async def database_error_handler(request: Request, exc: SQLAlchemyError) -> JSONResponse:
    """Never leak database internals (passwords, URLs, stack traces)."""
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": "Database error. Please try again later."},
    )


@app.get("/")
def root():
    return {
        "status": "success",
        "message": "ShortsCap Backend is running",
    }


@app.get("/health/db")
def health_db():
    """Database health check.

    Response never exposes the password, connection string, credentials,
    environment variables, or internal error details.
    """
    result = check_database_connection()
    if result["status"] == "success":
        return {"status": "connected", "database": settings.DB_NAME}
    return JSONResponse(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        content={"status": "not_connected", "database": settings.DB_NAME},
    )