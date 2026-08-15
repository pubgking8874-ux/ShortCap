"""shorts_event.py — ShortsCap backend: SQLAlchemy model for `shorts_events`.

A timestamped Shorts event (watch started / warning / limit reached …).
Structured payload lives in the MySQL-compatible JSON column.
"""

from datetime import datetime

from sqlalchemy import JSON, BigInteger, DateTime, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class ShortsEvent(Base):
    """One Shorts-related event."""

    __tablename__ = "shorts_events"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    device_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("devices.id", ondelete="SET NULL"), nullable=True
    )
    event_type: Mapped[str] = mapped_column(String(50), nullable=False)
    occurred_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    duration_seconds: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    metadata_json: Mapped[dict | None] = mapped_column(JSON, nullable=True)

    user: Mapped["User"] = relationship(back_populates="shorts_events")
    device: Mapped["Device | None"] = relationship()

    def __repr__(self) -> str:
        return f"<ShortsEvent id={self.id} type={self.event_type!r}>"
