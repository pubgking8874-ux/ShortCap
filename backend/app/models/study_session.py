"""study_session.py — ShortsCap backend: SQLAlchemy model for `study_sessions`.

One study session run (optionally tied to a schedule and a device). Holds
the planned vs. actual durations and a status; breaks and study events hang
off the session.
"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class StudySession(Base):
    """A single study session."""

    __tablename__ = "study_sessions"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    schedule_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("study_schedules.id", ondelete="SET NULL"), nullable=True
    )
    device_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("devices.id", ondelete="SET NULL"), nullable=True
    )
    started_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    ended_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    planned_duration_seconds: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    actual_duration_seconds: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="active")
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())

    user: Mapped["User"] = relationship(back_populates="study_sessions")
    schedule: Mapped["StudySchedule | None"] = relationship(back_populates="sessions")
    device: Mapped["Device | None"] = relationship()
    breaks: Mapped[list["BreakSession"]] = relationship(
        back_populates="study_session", cascade="all, delete-orphan"
    )
    events: Mapped[list["StudyEvent"]] = relationship(
        back_populates="study_session", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<StudySession id={self.id} user_id={self.user_id} status={self.status!r}>"
