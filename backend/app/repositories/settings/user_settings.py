"""user_settings.py — ShortsCap backend: settings repository.

UserSettingsRepository — database operations ONLY (no business rules).

Guarantees one settings row per user (`user_settings.user_id` is unique in
the approved schema): create_default/upsert never insert a second row.
"""

from sqlalchemy.orm import Session

from app.models.user_settings import UserSettings


class UserSettingsRepository:
    """CRUD-style data access for the `user_settings` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_user_id(self, user_id: int) -> UserSettings | None:
        """Return the user's settings row, or None if it does not exist."""
        return (
            self.db.query(UserSettings)
            .filter(UserSettings.user_id == user_id)
            .first()
        )

    def create_default(self, user_id: int) -> UserSettings:
        """Insert a row with the model's defaults (dark / en / on / on).

        The unique `user_id` constraint guarantees one row per user.
        """
        settings = UserSettings(user_id=user_id)
        self.db.add(settings)
        self.db.commit()
        self.db.refresh(settings)
        return settings

    def update(self, user_id: int, data: dict) -> UserSettings | None:
        """Apply only the supplied, non-None values. Returns None when the
        user has no settings row yet (caller decides: 404 or create default)."""
        settings = self.get_by_user_id(user_id)
        if settings is None:
            return None
        for key, value in data.items():
            if value is not None:
                setattr(settings, key, value)
        self.db.commit()
        self.db.refresh(settings)
        return settings

    def upsert(self, user_id: int, data: dict) -> UserSettings:
        """Update the user's row, creating it first (with defaults merged in)
        when it does not exist. Never produces a second row."""
        settings = self.get_by_user_id(user_id)
        if settings is None:
            settings = UserSettings(user_id=user_id)
            self.db.add(settings)
        for key, value in data.items():
            if value is not None:
                setattr(settings, key, value)
        self.db.commit()
        self.db.refresh(settings)
        return settings
