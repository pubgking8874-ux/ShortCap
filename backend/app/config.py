"""config.py — ShortsCap backend: application configuration (pydantic-settings, env-driven).

Loads values from environment variables (or the local `.env` file when present,
see `.env.example` for the template). No credentials are hardcoded here; every
secret lives in the environment / `.env` only.
"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Typed settings. Unknown extra env vars are ignored safely."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # --- Application ---
    APP_NAME: str = "ShortsCap Backend"
    APP_ENV: str = "development"
    DEBUG: bool = True

    # --- Database (MySQL) ---
    DB_HOST: str = "localhost"
    DB_PORT: int = 3306
    DB_USER: str = "root"
    DB_PASSWORD: str = ""
    DB_NAME: str = "shortscap"

    @property
    def database_url(self) -> str:
        """SQLAlchemy URL for MySQL. Same shape works for AWS RDS (host/creds via env)."""
        return (
            f"mysql+pymysql://{self.DB_USER}:{self.DB_PASSWORD}"
            f"@{self.DB_HOST}:{self.DB_PORT}/{self.DB_NAME}"
        )


@lru_cache
def get_settings() -> Settings:
    """Return the process-wide settings singleton."""
    return Settings()


settings = get_settings()