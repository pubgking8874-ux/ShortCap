"""monitoring.py — ShortsCap backend: monitoring settings service.

Business-level operations; delegates persistence to the repository. No
monitoring engine behavior here.
"""

from sqlalchemy.orm import Session

from app.models.monitoring_settings import MonitoringSettings
from app.repositories.settings import MonitoringSettingsRepository


class MonitoringSettingsService:
    """Business operations for the user's monitoring settings."""

    def __init__(self, db: Session) -> None:
        self.repository = MonitoringSettingsRepository(db)

    def get_settings(self, user_id: int) -> MonitoringSettings:
        settings = self.repository.get_by_user_id(user_id)
        if settings is None:
            settings = self.repository.create_default(user_id)
        return settings

    def update_settings(self, user_id: int, data: dict) -> MonitoringSettings:
        update_data = {key: value for key, value in data.items() if value is not None}
        return self.repository.upsert(user_id, update_data)

    def ensure_settings(self, user_id: int) -> MonitoringSettings:
        return self.get_settings(user_id)
