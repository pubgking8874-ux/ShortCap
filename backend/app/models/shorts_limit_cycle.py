"""shorts_limit_cycle.py — ShortsCap backend: SQLAlchemy model for
`shorts_limit_cycles`.

The durable 24-hour Shorts enforcement cycle (Shorts Control domain).

Responsibility split (documented in the Shorts Control architecture):
  - `shorts_settings`          -> configuration (limits, warning, HUD look)
  - `shorts_usage`             -> synchronized daily usage summaries
  - `shorts_events`            -> event history
  - `shorts_limit_cycles`      -> the CURRENT 24-hour runtime window state

One user has AT MOST ONE active cycle at a time. The approved single-device
development reality means the active cycle is scoped per USER; `device_id` is
recorded on creation for future multi-device support but is NOT part of the
uniqueness (a broad global unique constraint would wrongly block future
multi-device work).

Single-active-cycle guard: MySQL has no partial unique indexes, so the model
uses the standard NULL-distinct trick — `is_active` is `True` for the one
current window and NULL for every finished/disabled window, with a unique
constraint on (user_id, is_active). MySQL treats NULLs as distinct, so any
number of historical cycles can coexist while at most one row can carry
`is_active = True` per user. The service layer enforces the same rule.

States (existing enum-style string values): ACTIVE, LIMIT_REACHED, EXPIRED,
DISABLED. A LIMIT_REACHED cycle is still the current window until it expires;
EXPIRED / DISABLED cycles are historical.
"""

from datetime import datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    ForeignKey,
    Integer,
    String,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class ShortsLimitCycle(Base):
    """One 24-hour Shorts limit cycle (the active window or a historical one)."""

    __tablename__ = "shorts_limit_cycles"
    __table_args__ = (
        UniqueConstraint(
            "user_id",
            "is_active",
            name="uq_shorts_limit_cycles_user_active",
        ),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    device_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("devices.id", ondelete="SET NULL"), nullable=True
    )
    limit_count: Mapped[int] = mapped_column(Integer, nullable=False)
    current_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    cycle_started_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    cycle_expires_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    status: Mapped[str] = mapped_column(
        String(20), nullable=False, default="ACTIVE", server_default="ACTIVE"
    )
    warning_triggered: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False
    )
    limit_reached: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    # Single-active-cycle guard — see module docstring.
    is_active: Mapped[bool | None] = mapped_column(Boolean, nullable=True, default=None)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="shorts_limit_cycles")
    device: Mapped["Device | None"] = relationship()

    def __repr__(self) -> str:
        return f"<ShortsLimitCycle id={self.id} status={self.status!r} count={self.current_count}/{self.limit_count}>"
