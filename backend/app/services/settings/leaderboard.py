"""leaderboard.py — ShortsCap backend: leaderboard settings service.

Business-level operations; delegates persistence to the repository. Only
participation/display preferences — NO score/rank/winner logic.
"""

from sqlalchemy.orm import Session

from app.models.leaderboard_setting import LeaderboardSetting
from app.repositories.settings import LeaderboardSettingsRepository


class LeaderboardSettingsService:
    """Business operations for the user's leaderboard participation settings."""

    def __init__(self, db: Session) -> None:
        self.repository = LeaderboardSettingsRepository(db)

    def get_settings(self, user_id: int) -> LeaderboardSetting:
        setting = self.repository.get_by_user_id(user_id)
        if setting is None:
            setting = self.repository.create_default(user_id)
        return setting

    def update_settings(self, user_id: int, data: dict) -> LeaderboardSetting:
        update_data = {key: value for key, value in data.items() if value is not None}
        return self.repository.upsert(user_id, update_data)

    def ensure_settings(self, user_id: int) -> LeaderboardSetting:
        return self.get_settings(user_id)
