from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse
from sqlalchemy.exc import SQLAlchemyError

from app.config import settings
from app.database import check_database_connection
from app.routers.settings import router as settings_router
from app.routers.study import router as study_router

app = FastAPI(title="ShortsCap Backend")

# Phase 6 — settings data layer (GET /settings, PUT /settings).
app.include_router(settings_router)

# Phase 8 — study data layer (schedules / sessions / breaks / events).
app.include_router(study_router)


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