"""user_settings.py — ShortsCap backend: settings service.

UserSettingsService — business-level settings operations. It coordinates with
the repository (never touches SQL directly) and is independent of HTTP /
FastAPI request objects (it receives plain dicts of validated values).
"""

from sqlalchemy.orm import Session

from app.models.user_settings import UserSettings
from app.repositories.settings import UserSettingsRepository


class UserSettingsService:
    """Business operations for the user's application settings."""

    def __init__(self, db: Session) -> None:
        self.repository = UserSettingsRepository(db)

    def get_settings(self, user_id: int) -> UserSettings:
        """Return the user's settings, creating the app's safe defaults the
        first time (no row exists yet)."""
        settings = self.repository.get_by_user_id(user_id)
        if settings is None:
            settings = self.repository.create_default(user_id)
        return settings

    def update_settings(self, user_id: int, data: dict) -> UserSettings:
        """Apply a partial settings update.

        Only the supplied, non-None values are persisted — unspecified fields
        are preserved. A missing row is created first with defaults, so PUT
        always returns a complete settings payload.
        """
        # Business rule: never overwrite a field the client did not supply.
        update_data = {key: value for key, value in data.items() if value is not None}
        return self.repository.upsert(user_id, update_data)

    def ensure_settings(self, user_id: int) -> UserSettings:
        """Idempotent: make sure the user has a settings row, then return it."""
        return self.get_settings(user_id)
