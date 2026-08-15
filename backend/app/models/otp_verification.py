"""otp_verification.py — ShortsCap backend: SQLAlchemy model for `otp_verifications`.

Stores OTP attempts for phone/email verification. SECURITY: only the OTP
*hash* is ever persisted (`otp_hash`) — never the plain OTP value. The
verification logic itself is implemented in a later phase.
"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class OtpVerification(Base):
    """A single OTP send/attempt. Only the OTP hash is stored."""

    __tablename__ = "otp_verifications"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=True
    )
    phone: Mapped[str | None] = mapped_column(String(20), nullable=True)
    otp_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    purpose: Mapped[str] = mapped_column(String(30), nullable=False)  # e.g. "login" | "signup" | "reset"
    expires_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    verified_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    attempt_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())

    user: Mapped["User | None"] = relationship(back_populates="otp_verifications")

    def __repr__(self) -> str:
        return f"<OtpVerification id={self.id} purpose={self.purpose!r} verified={self.verified_at is not None}>"
