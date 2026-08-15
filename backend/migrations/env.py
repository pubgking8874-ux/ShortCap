"""Alembic migration environment — ShortsCap backend.

Connects Alembic to the SAME declarative Base used by all 24 models
(`app.database.Base`) and resolves the database URL from the existing
env-driven configuration (`app.config.settings`) — nothing is hardcoded here
and no secrets are printed.
"""

from logging.config import fileConfig

from alembic import context
from sqlalchemy import create_engine, pool

from app.config import settings
from app.database import Base

# Import ALL models before Alembic reads the metadata so every approved
# table registers on `Base.metadata` (see app/models/__init__.py).
import app.models  # noqa: F401

config = context.config

# Alembic logging from alembic.ini (optional but standard).
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# The app's single declarative Base — never a second one.
target_metadata = Base.metadata

# Database URL comes from the environment (pydantic-settings/.env) — the
# same configuration the running FastAPI app uses. alembic.ini deliberately
# does not hardcode it. NOTE: the URL is passed through as the SQLAlchemy
# URL OBJECT (not `str()`), because a string round-trip re-quotes the
# password and can corrupt it.


def run_migrations_offline() -> None:
    """Run migrations in 'offline' mode (emit SQL without a DB connection)."""
    context.configure(
        url=settings.database_url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )

    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    """Run migrations in 'online' mode (connect and run against the DB)."""
    connectable = create_engine(settings.database_url, poolclass=pool.NullPool)

    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)

        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
