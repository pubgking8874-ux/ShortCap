"""study_schedule.py — ShortsCap backend: SQLAlchemy model for `study_schedules`.

A user-defined recurring study plan (subject, start time, duration, days).
`days_of_week` stores the enabled weekday codes as a simple comma-separated
string (e.g. "Mon,Tue,Wed") — the schedule engine is implemented later.
"""

from datetime import datetime, time

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, Integer, String, Time, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class StudySchedule(Base):
    """A recurring study schedule."""

    __tablename__ = "study_schedules"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    title: Mapped[str | None] = mapped_column(String(100), nullable=True)
    subject: Mapped[str | None] = mapped_column(String(100), nullable=True)
    start_time: Mapped[time | None] = mapped_column(Time, nullable=True)
    duration_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    days_of_week: Mapped[str | None] = mapped_column(String(100), nullable=True)
    reminder_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    is_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="study_schedules")
    sessions: Mapped[list["StudySession"]] = relationship(back_populates="schedule")

    def __repr__(self) -> str:
        return f"<StudySchedule id={self.id} user_id={self.user_id} subject={self.subject!r}>"
