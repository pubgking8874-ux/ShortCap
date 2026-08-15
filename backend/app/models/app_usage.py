"""app_usage.py — ShortsCap backend: SQLAlchemy model for the `app_usage` table.

Aggregated per-day, per-app usage for a user/device — NOT raw per-second
activity. `usage_date` is the calendar day the aggregate covers.
"""

from datetime import date, datetime

from sqlalchemy import BigInteger, Date, DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class AppUsage(Base):
    """Aggregated daily usage for one app on one device."""

    __tablename__ = "app_usage"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    device_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("devices.id", ondelete="SET NULL"), nullable=True
    )
    package_name: Mapped[str] = mapped_column(String(255), nullable=False)
    app_name: Mapped[str | None] = mapped_column(String(255), nullable=True)
    usage_date: Mapped[date | None] = mapped_column(Date, index=True, nullable=True)
    duration_seconds: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    launch_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="app_usage")
    device: Mapped["Device | None"] = relationship()

    def __repr__(self) -> str:
        return f"<AppUsage id={self.id} package={self.package_name!r} date={self.usage_date}>"
