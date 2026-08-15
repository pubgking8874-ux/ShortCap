"""blocked_website.py — ShortsCap backend: SQLAlchemy model for `blocked_websites`.

A domain a user blocks. `normalized_domain` (lowercased, stripped) is unique
per user and indexed; `verification_status` is informational only — the
domain-verification logic itself is implemented in a later phase.
"""

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, String, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class BlockedWebsite(Base):
    """A blocked website domain for a user."""

    __tablename__ = "blocked_websites"
    __table_args__ = (
        UniqueConstraint("user_id", "normalized_domain", name="uq_blocked_websites_user_domain"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    domain: Mapped[str] = mapped_column(String(255), nullable=False)
    normalized_domain: Mapped[str] = mapped_column(String(255), index=True, nullable=False)
    verification_status: Mapped[str] = mapped_column(String(20), nullable=False, default="pending")
    is_blocked: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="blocked_websites")
    events: Mapped[list["WebsiteEvent"]] = relationship(
        back_populates="blocked_website", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<BlockedWebsite id={self.id} domain={self.domain!r}>"
