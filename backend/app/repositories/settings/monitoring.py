"""monitoring.py — ShortsCap backend: monitoring settings repository.

Database operations only (no business rules). One row per user
(`monitoring_settings.user_id` is unique in the approved schema).
"""

from sqlalchemy.orm import Session

from app.models.monitoring_settings import MonitoringSettings


class MonitoringSettingsRepository:
    """CRUD-style data access for the `monitoring_settings` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_user_id(self, user_id: int) -> MonitoringSettings | None:
        return (
            self.db.query(MonitoringSettings)
            .filter(MonitoringSettings.user_id == user_id)
            .first()
        )

    def create_default(self, user_id: int) -> MonitoringSettings:
        settings = MonitoringSettings(user_id=user_id)
        self.db.add(settings)
        self.db.commit()
        self.db.refresh(settings)
        return settings

    def update(self, user_id: int, data: dict) -> MonitoringSettings | None:
        settings = self.get_by_user_id(user_id)
        if settings is None:
            return None
        for key, value in data.items():
            if value is not None:
                setattr(settings, key, value)
        self.db.commit()
        self.db.refresh(settings)
        return settings

    def upsert(self, user_id: int, data: dict) -> MonitoringSettings:
        settings = self.get_by_user_id(user_id)
        if settings is None:
            settings = MonitoringSettings(user_id=user_id)
            self.db.add(settings)
        for key, value in data.items():
            if value is not None:
                setattr(settings, key, value)
        self.db.commit()
        self.db.refresh(settings)
        return settings
