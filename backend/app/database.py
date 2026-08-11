"""database.py — ShortsCap backend: SQLAlchemy engine + session management (MySQL).

The engine URL derives from env config (DB_HOST / DB_PORT / DB_USER /
DB_PASSWORD / DB_NAME) via `app.config.settings`. `Base` is the declarative
base for all models, `SessionLocal` is the scoped session factory, and
`get_db()` is the FastAPI request dependency.
"""

from collections.abc import Generator

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import settings


class Base(DeclarativeBase):
    """Declarative base for all SQLAlchemy models."""


engine = create_engine(
    settings.database_url,
    pool_pre_ping=True,
    pool_recycle=1800,
    echo=settings.DEBUG,
)

SessionLocal = sessionmaker(
    bind=engine,
    autoflush=False,
    autocommit=False,
    expire_on_commit=False,
)


def get_db() -> Generator[Session, None, None]:
    """FastAPI dependency yielding a database session tied to a request."""
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()


def check_database_connection() -> dict:
    """Attempt a REAL connection — never fabricates success.

    Returns a status dict used by the Phase 3 verification script and report.
    """
    from sqlalchemy import text

    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        return {
            "status": "success",
            "message": "Connected",
            "database": settings.database_url,
        }
    except Exception as exc:  # noqa: BLE001 - surface any connect error
        return {
            "status": "not_configured",
            "message": f"Not configured: {exc}",
            "database": settings.database_url,
        }