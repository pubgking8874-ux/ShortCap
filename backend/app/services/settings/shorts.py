"""shorts.py — ShortsCap backend: Shorts settings service.

Business-level operations; delegates persistence to the repository. No
Shorts detection or enforcement engine here.
"""

from sqlalchemy.orm import Session

from app.models.shorts_settings import ShortsSettings
from app.repositories.settings import ShortsSettingsRepository


class ShortsSettingsService:
    """Business operations for the user's Shorts settings."""

    def __init__(self, db: Session) -> None:
        self.repository = ShortsSettingsRepository(db)

    def get_settings(self, user_id: int) -> ShortsSettings:
        settings = self.repository.get_by_user_id(user_id)
        if settings is None:
            settings = self.repository.create_default(user_id)
        return settings

    def update_settings(self, user_id: int, data: dict) -> ShortsSettings:
        update_data = {key: value for key, value in data.items() if value is not None}
        return self.repository.upsert(user_id, update_data)

    def ensure_settings(self, user_id: int) -> ShortsSettings:
        return self.get_settings(user_id)
