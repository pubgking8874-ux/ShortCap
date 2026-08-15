"""leaderboard.py — ShortsCap backend: leaderboard settings repository.

Database operations only (no business rules, no ranking logic). One row per
user (`leaderboard_settings.user_id` is unique in the approved schema).
"""

from sqlalchemy.orm import Session

from app.models.leaderboard_setting import LeaderboardSetting


class LeaderboardSettingsRepository:
    """CRUD-style data access for the `leaderboard_settings` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_user_id(self, user_id: int) -> LeaderboardSetting | None:
        return (
            self.db.query(LeaderboardSetting)
            .filter(LeaderboardSetting.user_id == user_id)
            .first()
        )

    def create_default(self, user_id: int) -> LeaderboardSetting:
        setting = LeaderboardSetting(user_id=user_id)
        self.db.add(setting)
        self.db.commit()
        self.db.refresh(setting)
        return setting

    def update(self, user_id: int, data: dict) -> LeaderboardSetting | None:
        setting = self.get_by_user_id(user_id)
        if setting is None:
            return None
        for key, value in data.items():
            if value is not None:
                setattr(setting, key, value)
        self.db.commit()
        self.db.refresh(setting)
        return setting

    def upsert(self, user_id: int, data: dict) -> LeaderboardSetting:
        setting = self.get_by_user_id(user_id)
        if setting is None:
            setting = LeaderboardSetting(user_id=user_id)
            self.db.add(setting)
        for key, value in data.items():
            if value is not None:
                setattr(setting, key, value)
        self.db.commit()
        self.db.refresh(setting)
        return setting
