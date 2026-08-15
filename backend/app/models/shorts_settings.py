"""shorts_settings.py — ShortsCap backend: SQLAlchemy model for `shorts_settings`.

One main shorts-control settings row per user (`user_id` is unique): daily
limits, warning thresholds and strict mode for short-video platforms.
"""

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, Integer, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class ShortsSettings(Base):
    """Per-user Shorts monitoring/limits configuration."""

    __tablename__ = "shorts_settings"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("users.id", ondelete="CASCADE"),
        unique=True,
        index=True,
        nullable=False,
    )
    daily_limit_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    daily_limit_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    warning_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    warning_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    strict_mode_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    is_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="shorts_settings")

    def __repr__(self) -> str:
        return f"<ShortsSettings id={self.id} user_id={self.user_id}>"
