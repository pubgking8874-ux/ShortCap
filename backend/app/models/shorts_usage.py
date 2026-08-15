"""shorts_usage.py — ShortsCap backend: SQLAlchemy model for `shorts_usage`.

Aggregated daily Shorts usage per user/device/platform/surface (count +
duration) plus the warning/limit flags for that day. NOTE: the approved
schema has `updated_at` but no `created_at` on this table.

Phase 11A added `platform` and `surface` (VARCHAR(50), NOT NULL) so daily
aggregation can be stored separately per cross-platform short-form
platform/surface (see the Cross-Platform Short-Form Content Architecture).
Existing/pre-architecture rows use the explicit marker value `UNKNOWN` —
historical platform/surface values are never invented.

The logical daily identity is (user_id, device_id, platform, surface,
usage_date) — enforced by a unique constraint so re-syncing the same daily
summary can never create uncontrolled duplicates.
"""

from datetime import date, datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    Date,
    DateTime,
    ForeignKey,
    Integer,
    String,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class ShortsUsage(Base):
    """Aggregated daily Shorts usage for a user/device/platform/surface."""

    __tablename__ = "shorts_usage"
    __table_args__ = (
        UniqueConstraint(
            "user_id",
            "device_id",
            "platform",
            "surface",
            "usage_date",
            name="uq_shorts_usage_user_device_platform_surface_date",
        ),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    device_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("devices.id", ondelete="SET NULL"), nullable=True
    )
    usage_date: Mapped[date | None] = mapped_column(Date, index=True, nullable=True)
    # Cross-platform short-form identity (Phase 11A). `UNKNOWN` is the explicit
    # marker for pre-architecture rows / clients that do not send a value — it
    # is never a fabricated real platform.
    platform: Mapped[str] = mapped_column(
        String(50), nullable=False, default="UNKNOWN", server_default="UNKNOWN"
    )
    surface: Mapped[str] = mapped_column(
        String(50), nullable=False, default="UNKNOWN", server_default="UNKNOWN"
    )
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
