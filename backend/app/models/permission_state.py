"""permission_state.py — ShortsCap backend: SQLAlchemy model for `permission_states`.

Mirrors the Android permission status of a device. IMPORTANT: this table is
NOT the authoritative Android permission source — the Android system remains
the source of truth. This row is only a sync/UX mirror of the last known
state.
"""

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class PermissionState(Base):
    """Last-known permission status for a (user, device) pair."""

    __tablename__ = "permission_states"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    device_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("devices.id", ondelete="SET NULL"), nullable=True
    )
    permission_key: Mapped[str] = mapped_column(String(50), nullable=False)
    is_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    last_checked_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="permission_states")
    device: Mapped["Device | None"] = relationship()

    def __repr__(self) -> str:
        return f"<PermissionState id={self.id} key={self.permission_key!r} enabled={self.is_enabled}>"
