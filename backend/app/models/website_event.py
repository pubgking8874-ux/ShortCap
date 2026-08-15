"""website_event.py — ShortsCap backend: SQLAlchemy model for `website_events`.

A timestamped website event (blocked / unblocked / access attempted …).
`blocked_website_id` is nullable because an event can refer to a website that
was later removed.
"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class WebsiteEvent(Base):
    """One website-related event."""

    __tablename__ = "website_events"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    device_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("devices.id", ondelete="SET NULL"), nullable=True
    )
    blocked_website_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("blocked_websites.id", ondelete="SET NULL"), nullable=True
    )
    domain: Mapped[str | None] = mapped_column(String(255), nullable=True)
    event_type: Mapped[str] = mapped_column(String(50), nullable=False)
    occurred_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)

    user: Mapped["User"] = relationship(back_populates="website_events")
    device: Mapped["Device | None"] = relationship()
    blocked_website: Mapped["BlockedWebsite | None"] = relationship(back_populates="events")

    def __repr__(self) -> str:
        return f"<WebsiteEvent id={self.id} type={self.event_type!r} domain={self.domain!r}>"
