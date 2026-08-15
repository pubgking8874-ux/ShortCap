"""leaderboard_setting.py — ShortsCap backend: SQLAlchemy model for
`leaderboard_settings`.

One main leaderboard row per user (`user_id` is unique): whether the user is
on the leaderboard and under which display name. The ranking algorithm is NOT
part of this phase — only the data model.
"""

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class LeaderboardSetting(Base):
    """Per-user leaderboard participation/display settings."""

    __tablename__ = "leaderboard_settings"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("users.id", ondelete="CASCADE"),
        unique=True,
        index=True,
        nullable=False,
    )
    is_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    display_name: Mapped[str | None] = mapped_column(String(100), nullable=True)
    is_opted_in: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="leaderboard_settings")

    def __repr__(self) -> str:
        return f"<LeaderboardSetting id={self.id} user_id={self.user_id} opted_in={self.is_opted_in}>"
