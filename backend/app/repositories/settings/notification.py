"""notification.py — ShortsCap backend: notification preferences repository.

Database operations only (no business rules). One row per user
(`notification_preferences.user_id` is unique in the approved schema).
"""

from sqlalchemy.orm import Session

from app.models.notification_preference import NotificationPreference


class NotificationPreferenceRepository:
    """CRUD-style data access for the `notification_preferences` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_user_id(self, user_id: int) -> NotificationPreference | None:
        return (
            self.db.query(NotificationPreference)
            .filter(NotificationPreference.user_id == user_id)
            .first()
        )

    def create_default(self, user_id: int) -> NotificationPreference:
        prefs = NotificationPreference(user_id=user_id)
        self.db.add(prefs)
        self.db.commit()
        self.db.refresh(prefs)
        return prefs

    def update(self, user_id: int, data: dict) -> NotificationPreference | None:
        prefs = self.get_by_user_id(user_id)
        if prefs is None:
            return None
        for key, value in data.items():
            if value is not None:
                setattr(prefs, key, value)
        self.db.commit()
        self.db.refresh(prefs)
        return prefs

    def upsert(self, user_id: int, data: dict) -> NotificationPreference:
        prefs = self.get_by_user_id(user_id)
        if prefs is None:
            prefs = NotificationPreference(user_id=user_id)
            self.db.add(prefs)
        for key, value in data.items():
            if value is not None:
                setattr(prefs, key, value)
        self.db.commit()
        self.db.refresh(prefs)
        return prefs
