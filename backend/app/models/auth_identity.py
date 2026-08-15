"""auth_identity.py — ShortsCap backend: SQLAlchemy model for `auth_identities`.

A user can authenticate through multiple providers. `provider` supports
"google" and "phone". No authentication logic lives here — this is the data
model only.
"""

from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class AuthIdentity(Base):
    """One authentication identity (e.g. Google account or phone number) for a user."""

    __tablename__ = "auth_identities"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    provider: Mapped[str] = mapped_column(String(30), nullable=False)  # "google" | "phone"
    provider_user_id: Mapped[str | None] = mapped_column(String(255), nullable=True)
    provider_email: Mapped[str | None] = mapped_column(String(255), nullable=True)
    is_verified: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    user: Mapped["User"] = relationship(back_populates="auth_identities")

    def __repr__(self) -> str:
        return f"<AuthIdentity id={self.id} user_id={self.user_id} provider={self.provider!r}>"
