"""user.py — ShortsCap backend: SQLAlchemy model for the `users` table.

Phase 4 — approved schema. The user is the central account entity that
nearly every other table references. Email and phone are BOTH optional
(a user may sign up with either identifier), but each is unique when present.
"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class User(Base):
    """A ShortsCap account."""

    __tablename__ = "users"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    email: Mapped[str | None] = mapped_column(String(255), unique=True, index=True, nullable=True)
    phone: Mapped[str | None] = mapped_column(String(20), unique=True, index=True, nullable=True)
    name: Mapped[str | None] = mapped_column(String(100), nullable=True)
    gender: Mapped[str | None] = mapped_column(String(30), nullable=True)
    profile_image_url: Mapped[str | None] = mapped_column(String(500), nullable=True)
    status: Mapped[str] = mapped_column(String(20), nullable=False, server_default="active")
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now(), onupdate=func.now()
    )

    # --- Relationships (approved Phase 4 schema, resolved by class name so
    # there are no circular imports) ---
    profiles: Mapped["UserProfile"] = relationship(
        back_populates="user", uselist=False, cascade="all, delete-orphan"
    )
    auth_identities: Mapped[list["AuthIdentity"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    otp_verifications: Mapped[list["OtpVerification"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    devices: Mapped[list["Device"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    settings: Mapped["UserSettings"] = relationship(
        back_populates="user", uselist=False, cascade="all, delete-orphan"
    )
    permission_states: Mapped[list["PermissionState"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    study_schedules: Mapped[list["StudySchedule"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    study_sessions: Mapped[list["StudySession"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    study_events: Mapped[list["StudyEvent"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    monitoring_settings: Mapped["MonitoringSettings"] = relationship(
        back_populates="user", uselist=False, cascade="all, delete-orphan"
    )
    app_usage: Mapped[list["AppUsage"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    monitoring_events: Mapped[list["MonitoringEvent"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    shorts_settings: Mapped["ShortsSettings"] = relationship(
        back_populates="user", uselist=False, cascade="all, delete-orphan"
    )
    shorts_limit_cycles: Mapped[list["ShortsLimitCycle"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    shorts_usage: Mapped[list["ShortsUsage"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    shorts_events: Mapped[list["ShortsEvent"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    blocked_websites: Mapped[list["BlockedWebsite"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    website_events: Mapped[list["WebsiteEvent"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    notification_preferences: Mapped["NotificationPreference"] = relationship(
        back_populates="user", uselist=False, cascade="all, delete-orphan"
    )
    notification_events: Mapped[list["NotificationEvent"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    feedback: Mapped[list["Feedback"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    leaderboard_settings: Mapped["LeaderboardSetting"] = relationship(
        back_populates="user", uselist=False, cascade="all, delete-orphan"
    )
    leaderboard_scores: Mapped[list["LeaderboardScore"]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<User id={self.id} email={self.email!r}>"
