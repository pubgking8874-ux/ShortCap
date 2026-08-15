"""leaderboard_score.py — ShortsCap backend: SQLAlchemy model for `leaderboard_scores`.

One score per user per period (week/month). IMPORTANT: there is deliberately
NO permanent `rank` column — rank is derived later from score ordering. Score
calculation is not part of this phase.
"""

from datetime import date, datetime

from sqlalchemy import BigInteger, Date, DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class LeaderboardScore(Base):
    """A user's score for one leaderboard period."""

    __tablename__ = "leaderboard_scores"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    period_type: Mapped[str] = mapped_column(String(20), nullable=False)  # e.g. "week" | "month"
    period_start: Mapped[date | None] = mapped_column(Date, nullable=True)
    period_end: Mapped[date | None] = mapped_column(Date, nullable=True)
    score: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="leaderboard_scores")

    def __repr__(self) -> str:
        return f"<LeaderboardScore id={self.id} user_id={self.user_id} period={self.period_type!r} score={self.score}>"
