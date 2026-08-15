"""rank.py — ShortsCap backend: read-only leaderboard repository.

Database access only (no business rules). Supports the Phase 15B Rank engine:

  * eligible_user_ids() — users opted in AND enabled (Phase 15A §3)
  * display_names() — approved public display identity per user

Writes are deliberately absent: the leaderboard is DYNAMIC (Phase 15A §9),
`leaderboard_scores` is not written, and no private profile fields (email,
phone, name, …) are ever read here.
"""

from sqlalchemy.orm import Session

from app.models.leaderboard_setting import LeaderboardSetting


class RankRepository:
    """Read-only queries over `leaderboard_settings` for the leaderboard."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def eligible_user_ids(self) -> list[int]:
        """User ids who appear on the leaderboard: opted in AND enabled.

        `is_opted_in` defaults to False, so users with no settings row are
        never included (Phase 15A eligibility rule 1).
        """
        rows = (
            self.db.query(LeaderboardSetting.user_id)
            .filter(
                LeaderboardSetting.is_opted_in.is_(True),
                LeaderboardSetting.is_enabled.is_(True),
            )
            .all()
        )
        return [uid for (uid,) in rows]

    def display_names(self, user_ids: list[int]) -> dict[int, str | None]:
        """Approved public display name per user (None when unset — the
        service applies the deterministic `User {id}` fallback)."""
        if not user_ids:
            return {}
        rows = (
            self.db.query(LeaderboardSetting.user_id, LeaderboardSetting.display_name)
            .filter(LeaderboardSetting.user_id.in_(user_ids))
            .all()
        )
        return {uid: name for uid, name in rows}
