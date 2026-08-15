"""shorts_usage.py — ShortsCap backend: SQLAlchemy model for `shorts_usage`.

Aggregated daily Shorts usage per user/device (count + duration) plus the
warning/limit flags for that day. NOTE: the approved schema has `updated_at`
but no `created_at` on this table.
"""

from datetime import date, datetime

from sqlalchemy import BigInteger, Boolean, Date, DateTime, ForeignKey, Integer, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class ShortsUsage(Base):
    """Aggregated daily Shorts usage for a user/device."""

    __tablename__ = "shorts_usage"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    device_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("devices.id", ondelete="SET NULL"), nullable=True
    )
    usage_date: Mapped[date | None] = mapped_column(Date, index=True, nullable=True)
    shorts_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    duration_seconds: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    warning_triggered: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    limit_reached: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="shorts_usage")
    device: Mapped["Device | None"] = relationship()

    def __repr__(self) -> str:
        return f"<ShortsUsage id={self.id} date={self.usage_date} count={self.shorts_count}>"
