"""settings.py — ShortsCap backend: Pydantic schemas for user settings.

Request/response models for the Settings API. Validation mirrors the
ShortsCap app's REAL supported values (see the Android app):
  - theme: "dark" | "light" | "system"  (ThemeMode: DARK / LIGHT / SYSTEM)
  - language: BCP-47 codes "en" | "hi" | "ur" | "zh" | "es" (AppLanguage)
  - timezone: any valid IANA timezone name (e.g. "Asia/Kolkata")
No new app-supported languages or themes are invented here.
"""

import zoneinfo
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

# Literal types keep the validation in the schema layer (FastAPI returns 422
# for anything not in the app's supported set).
SupportedTheme = Literal["dark", "light", "system"]
SupportedLanguage = Literal["en", "hi", "ur", "zh", "es"]


class UserSettingsUpdate(BaseModel):
    """Partial update — every field is optional; unspecified fields are
    preserved by the API (PUT semantics)."""

    theme: SupportedTheme | None = None
    language: SupportedLanguage | None = None
    notifications_enabled: bool | None = None
    sound_enabled: bool | None = None
    timezone: str | None = None

    @field_validator("timezone")
    @classmethod
    def timezone_must_be_valid(cls, value: str | None) -> str | None:
        if value is None:
            return value
        try:
            zoneinfo.ZoneInfo(value)
        except (ValueError, zoneinfo.ZoneInfoNotFoundError):
            raise ValueError(f"Invalid timezone: {value!r}. Use a valid IANA timezone name (e.g. 'Asia/Kolkata').")
        return value


class UserSettingsResponse(BaseModel):
    """Full settings payload returned by GET /settings and PUT /settings."""

    model_config = ConfigDict(from_attributes=True)

    user_id: int
    theme: str
    language: str
    notifications_enabled: bool
    sound_enabled: bool
    timezone: str | None = None
    created_at: datetime
    updated_at: datetime


# ---------------------------------------------------------------------------
# Monitoring settings
# ---------------------------------------------------------------------------


class MonitoringSettingsUpdate(BaseModel):
    """Partial update for monitoring settings (all optional)."""

    device_monitoring_enabled: bool | None = None
    monitoring_enabled: bool | None = None
    strict_mode_enabled: bool | None = None


class MonitoringSettingsResponse(BaseModel):
    """Full monitoring settings payload."""

    model_config = ConfigDict(from_attributes=True)

    user_id: int
    device_monitoring_enabled: bool
    monitoring_enabled: bool
    strict_mode_enabled: bool
    created_at: datetime
    updated_at: datetime


# ---------------------------------------------------------------------------
# Shorts settings
# ---------------------------------------------------------------------------


class ShortsSettingsUpdate(BaseModel):
    """Partial update for Shorts settings. Numeric limits must be non-negative."""

    daily_limit_minutes: int | None = Field(default=None, ge=0)
    daily_limit_count: int | None = Field(default=None, ge=0)
    warning_minutes: int | None = Field(default=None, ge=0)
    warning_count: int | None = Field(default=None, ge=0)
    strict_mode_enabled: bool | None = None
    is_enabled: bool | None = None


class ShortsSettingsResponse(BaseModel):
    """Full Shorts settings payload."""

    model_config = ConfigDict(from_attributes=True)

    user_id: int
    daily_limit_minutes: int | None = None
    daily_limit_count: int | None = None
    warning_minutes: int | None = None
    warning_count: int | None = None
    strict_mode_enabled: bool
    is_enabled: bool
    created_at: datetime
    updated_at: datetime


# ---------------------------------------------------------------------------
# Notification preferences
# ---------------------------------------------------------------------------


class NotificationPreferenceUpdate(BaseModel):
    """Partial update for notification preferences (all optional booleans)."""

    study_notifications: bool | None = None
    monitoring_notifications: bool | None = None
    system_notifications: bool | None = None


class NotificationPreferenceResponse(BaseModel):
    """Full notification preferences payload."""

    model_config = ConfigDict(from_attributes=True)

    user_id: int
    study_notifications: bool
    monitoring_notifications: bool
    system_notifications: bool
    created_at: datetime
    updated_at: datetime


# ---------------------------------------------------------------------------
# Leaderboard settings
# ---------------------------------------------------------------------------


class LeaderboardSettingUpdate(BaseModel):
    """Partial update for leaderboard participation settings."""

    is_enabled: bool | None = None
    display_name: str | None = Field(default=None, max_length=100)
    is_opted_in: bool | None = None


class LeaderboardSettingResponse(BaseModel):
    """Full leaderboard participation settings payload."""

    model_config = ConfigDict(from_attributes=True)

    user_id: int
    is_enabled: bool
    display_name: str | None = None
    is_opted_in: bool
    created_at: datetime
    updated_at: datetime


# ---------------------------------------------------------------------------
# Permission states (last-known sync mirror — Android is the real authority)
# ---------------------------------------------------------------------------

# The app's real permission identifiers (Android `PermissionId` enum names).
SupportedPermissionKey = Literal[
    "USAGE_ACCESS",
    "ACCESSIBILITY",
    "OVERLAY",
    "NOTIFICATIONS",
    "BATTERY_OPTIMIZATION",
    "STORAGE_MEDIA",
    "SYSTEM_AUDIO_ACCESS",
]


class PermissionStateUpdate(BaseModel):
    """One last-known permission state to sync (upsert by user + key)."""

    permission_key: SupportedPermissionKey
    is_enabled: bool | None = None
    device_id: int | None = None
    last_checked_at: datetime | None = None


class PermissionStateResponse(BaseModel):
    """One stored permission state."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    device_id: int | None = None
    permission_key: str
    is_enabled: bool
    last_checked_at: datetime | None = None
    updated_at: datetime
