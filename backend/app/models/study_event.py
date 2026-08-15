"""study_event.py — ShortsCap backend: SQLAlchemy model for `study_events`.

A timestamped event inside a study session or break (start/stop/pause/…).
Extra structured payload lives in the MySQL-compatible JSON column.
"""

from datetime import datetime

from sqlalchemy import JSON, BigInteger, DateTime, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class StudyEvent(Base):
    """One event within a study session / break."""

    __tablename__ = "study_events"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    study_session_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("study_sessions.id", ondelete="CASCADE"), nullable=True
    )
    break_session_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("break_sessions.id", ondelete="CASCADE"), nullable=True
    )
    event_type: Mapped[str] = mapped_column(String(50), nullable=False)
    event_time: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    metadata_json: Mapped[dict | None] = mapped_column(JSON, nullable=True)

    user: Mapped["User"] = relationship(back_populates="study_events")
    study_session: Mapped["StudySession | None"] = relationship(back_populates="events")
    break_session: Mapped["BreakSession | None"] = relationship(back_populates="events")

    def __repr__(self) -> str:
        return f"<StudyEvent id={self.id} type={self.event_type!r}>"
