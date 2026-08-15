"""notification.py — ShortsCap backend: notification preferences service.

Business-level operations; delegates persistence to the repository. No
notification delivery logic here.
"""

from sqlalchemy.orm import Session

from app.models.notification_preference import NotificationPreference
from app.repositories.settings import NotificationPreferenceRepository


class NotificationPreferenceService:
    """Business operations for the user's notification preferences."""

    def __init__(self, db: Session) -> None:
        self.repository = NotificationPreferenceRepository(db)

    def get_preferences(self, user_id: int) -> NotificationPreference:
        prefs = self.repository.get_by_user_id(user_id)
        if prefs is None:
            prefs = self.repository.create_default(user_id)
        return prefs

    def update_preferences(self, user_id: int, data: dict) -> NotificationPreference:
        update_data = {key: value for key, value in data.items() if value is not None}
        return self.repository.upsert(user_id, update_data)

    def ensure_preferences(self, user_id: int) -> NotificationPreference:
        return self.get_preferences(user_id)
