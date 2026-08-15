"""shorts.py — ShortsCap backend: Shorts settings repository.

Database operations only (no business rules). One row per user
(`shorts_settings.user_id` is unique in the approved schema).
"""

from sqlalchemy.orm import Session

from app.models.shorts_settings import ShortsSettings


class ShortsSettingsRepository:
    """CRUD-style data access for the `shorts_settings` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_user_id(self, user_id: int) -> ShortsSettings | None:
        return (
            self.db.query(ShortsSettings)
            .filter(ShortsSettings.user_id == user_id)
            .first()
        )

    def create_default(self, user_id: int) -> ShortsSettings:
        settings = ShortsSettings(user_id=user_id)
        self.db.add(settings)
        self.db.commit()
        self.db.refresh(settings)
        return settings

    def update(self, user_id: int, data: dict) -> ShortsSettings | None:
        settings = self.get_by_user_id(user_id)
        if settings is None:
            return None
        for key, value in data.items():
            if value is not None:
                setattr(settings, key, value)
        self.db.commit()
        self.db.refresh(settings)
        return settings

    def upsert(self, user_id: int, data: dict) -> ShortsSettings:
        settings = self.get_by_user_id(user_id)
        if settings is None:
            settings = ShortsSettings(user_id=user_id)
            self.db.add(settings)
        for key, value in data.items():
            if value is not None:
                setattr(settings, key, value)
        self.db.commit()
        self.db.refresh(settings)
        return settings
