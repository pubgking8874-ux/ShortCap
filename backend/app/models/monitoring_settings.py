"""monitoring_settings.py — ShortsCap backend: SQLAlchemy model for
`monitoring_settings`.

One main monitoring-settings row per user (`user_id` is unique). Mirrors the
Android Monitoring screen (device monitoring master switch, strict mode).
"""

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class MonitoringSettings(Base):
    """Per-user device monitoring configuration."""

    __tablename__ = "monitoring_settings"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("users.id", ondelete="CASCADE"),
        unique=True,
        index=True,
        nullable=False,
    )
    device_monitoring_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    monitoring_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    strict_mode_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="monitoring_settings")

    def __repr__(self) -> str:
        return f"<MonitoringSettings id={self.id} user_id={self.user_id}>"
