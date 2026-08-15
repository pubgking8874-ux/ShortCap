"""break_session.py — ShortsCap backend: SQLAlchemy model for `break_sessions`.

A break inside a study session (BreakSession.study_session_id →
study_sessions.id).
"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class BreakSession(Base):
    """A break taken during a study session."""

    __tablename__ = "break_sessions"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    study_session_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("study_sessions.id", ondelete="CASCADE"), index=True, nullable=False
    )
    started_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    ended_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    duration_seconds: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="active")
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())

    study_session: Mapped["StudySession"] = relationship(back_populates="breaks")
    events: Mapped[list["StudyEvent"]] = relationship(
        back_populates="break_session", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<BreakSession id={self.id} study_session_id={self.study_session_id}>"
