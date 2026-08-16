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
    # "development" | "staging" | "production". Drives the security defaults
    # below (dev identity, CORS wildcard) — never leave it unset in a real
    # deployment.
    APP_ENV: str = "development"
    DEBUG: bool = True

    # --- Security ---
    # TEMPORARY DEVELOPMENT IDENTITY (X-Dev-User-Id) switch. None (default)
    # means "derive from APP_ENV": enabled in development/staging, DISABLED
    # in production (fail closed). Set True/False explicitly to override.
    DEV_IDENTITY_ENABLED: bool | None = None
    # Comma-separated list of allowed CORS origins (e.g.
    # "http://localhost:3000,https://app.example.com"). Empty = no
    # cross-origin browser requests are allowed (native Android clients are
    # unaffected). "*" is rejected outside the development environment.
    CORS_ALLOW_ORIGINS: str = ""
    # Comma-separated list of allowed HTTP Host header values (e.g.
    # "api.example.com,localhost:8000"). Empty = not enforced yet (reserved
    # for deployments where a Host header must be pinned).
    ALLOWED_HOSTS: str = ""

    # --- Database (MySQL) ---
    DB_HOST: str = "127.0.0.1"
    DB_PORT: int = 3306
    DB_USER: str = "root"
    DB_PASSWORD: str = ""
    DB_NAME: str = "shortscap_db"

    @property
    def dev_identity_enabled(self) -> bool:
        """Whether the temporary X-Dev-User-Id identity is accepted.

        Explicitly configured value wins; otherwise it follows the
        environment and FAILS CLOSED in production. This is the single
        switch that keeps the development identity from becoming an
        authentication bypass in a deployed environment.
        """
        if self.DEV_IDENTITY_ENABLED is not None:
            return self.DEV_IDENTITY_ENABLED
        return self.APP_ENV != "production"

    @property
    def cors_allow_origins(self) -> list[str]:
        """Parsed CORS origins. A wildcard is only ever allowed in the
        development environment — fail fast at startup rather than silently
        opening the API to every origin in a deployed environment."""
        origins = [o.strip() for o in self.CORS_ALLOW_ORIGINS.split(",") if o.strip()]
        if "*" in origins and self.APP_ENV != "development":
            raise ValueError(
                "CORS_ALLOW_ORIGINS must not contain '*' outside the "
                "development environment."
            )
        return origins

    @property
    def allowed_hosts(self) -> list[str]:
        """Parsed allowed Host header values (empty = not enforced)."""
        return [h.strip() for h in self.ALLOWED_HOSTS.split(",") if h.strip()]

    @property
    def database_url(self) -> str:
        """SQLAlchemy URL for MySQL. Same shape works for AWS RDS (host/creds via env)."""
        from sqlalchemy.engine import URL

        return URL.create(
            drivername="mysql+pymysql",
            username=self.DB_USER,
            password=self.DB_PASSWORD,
            host=self.DB_HOST,
            port=self.DB_PORT,
            database=self.DB_NAME,
        )


@lru_cache
def get_settings() -> Settings:
    """Return the process-wide settings singleton."""
    return Settings()


settings = get_settings()