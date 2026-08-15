"""notification_preference.py — ShortsCap backend: SQLAlchemy model for
`notification_preferences`.

One main notification-preferences row per user (`user_id` is unique) —
per-category on/off switches mirroring the app's Notifications settings.
"""

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class NotificationPreference(Base):
    """Per-user notification category preferences."""

    __tablename__ = "notification_preferences"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("users.id", ondelete="CASCADE"),
        unique=True,
        index=True,
        nullable=False,
    )
    study_notifications: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    monitoring_notifications: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    system_notifications: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="notification_preferences")

    def __repr__(self) -> str:
        return f"<NotificationPreference id={self.id} user_id={self.user_id}>"
