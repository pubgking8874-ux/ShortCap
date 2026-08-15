from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse
from sqlalchemy.exc import SQLAlchemyError

from app.config import settings
from app.database import check_database_connection
from app.routers.monitoring import router as monitoring_router
from app.routers.reports import router as reports_router
from app.routers.score import router as score_router
from app.routers.settings import router as settings_router
from app.routers.shorts import router as shorts_router
from app.routers.study import router as study_router
from app.routers.web import blocked_websites_router, web_events_router

app = FastAPI(title="ShortsCap Backend")

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