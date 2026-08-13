from fastapi import FastAPI, status
from fastapi.responses import JSONResponse

from app.config import settings
from app.database import check_database_connection

app = FastAPI(title="ShortsCap Backend")


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