"""monitoring.py — ShortsCap backend: Pydantic schemas for monitoring data.

Request/response models for the Monitoring data layer (app-usage sync,
monitoring events, summary). Field names and semantics mirror the Android
app's existing monitoring concepts:

  - app usage is AGGREGATED per (device, package, day) — the Android side
    observes usage locally and syncs summaries; the backend never computes
    real-time usage.
  - event types map to existing Android concepts (MONITORING_STOPPED is an
    existing notification category; SHORTS_LIMIT_WARNING / SHORTS_LIMIT_REACHED
    are the app's limit sound categories; APP_RESTRICTED is the app-blocking /
    restricted-mode concept).

Input and output schemas are separated so validation (non-negative durations,
non-empty package names, valid event types, valid dates) lives in the schema
layer — FastAPI rejects invalid input with 422 before any service runs.
"""

import re
from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

# Event types actually supported by the Android monitoring architecture
# (see module docstring). No invented taxonomy.
MonitoringEventType = Literal[
    "MONITORING_STARTED",
    "MONITORING_STOPPED",
    "LIMIT_WARNING",
    "LIMIT_REACHED",
    "APP_RESTRICTED",
]

# Android package names: dot-separated segments of letters/digits/underscore,
# each starting with a letter (e.g. com.google.android.youtube).
_PACKAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)*$")


def _validate_package_name(value: str | None, field: str) -> str | None:
    """Non-empty, no spaces, valid Android package shape."""
    if value is None:
        return None
    stripped = value.strip()
    if not stripped:
        raise ValueError(f"{field} must not be empty.")
    if len(stripped) > 255:
        raise ValueError(f"{field} must be at most 255 characters.")
    if not _PACKAGE_RE.match(stripped):
        raise ValueError(
            f"Invalid {field}: {value!r}. Expected an Android package name "
            "like 'com.example.app'."
        )
    return stripped


# ---------------------------------------------------------------------------
# App usage
# ---------------------------------------------------------------------------


class AppUsageRecord(BaseModel):
    """One aggregated daily usage summary submitted by Android.

    `device_id`, `package_name` and `usage_date` identify the summary;
    `duration_seconds` / `launch_count` must be non-negative. The backend
    attaches the development user identity — a client-supplied user_id is
    never trusted.
    """

    device_id: int
    package_name: str
    app_name: str | None = Field(default=None, max_length=255)
    usage_date: date
    duration_seconds: int = Field(default=0, ge=0)
    launch_count: int = Field(default=0, ge=0)

    @field_validator("package_name")
    @classmethod
    def package_must_be_valid(cls, value: str) -> str:
        return _validate_package_name(value, "package_name")

    @field_validator("app_name")
    @classmethod
    def app_name_must_not_be_blank(cls, value: str | None) -> str | None:
        if value is None:
            return None
        stripped = value.strip()
        if not stripped:
            raise ValueError("app_name must not be empty.")
        return stripped


class AppUsageResponse(BaseModel):
    """One persisted aggregated usage row."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    device_id: int | None = None
    package_name: str
    app_name: str | None = None
    usage_date: date | None = None
    duration_seconds: int
    launch_count: int
    created_at: datetime
    updated_at: datetime


# ---------------------------------------------------------------------------
# Monitoring events
# ---------------------------------------------------------------------------


class MonitoringEventCreate(BaseModel):
    """One monitoring event submitted by Android.

    `occurred_at` is optional — when omitted the server stamps it with the
    current UTC time. Aware datetimes are normalized to naive UTC before
    storage (see the service).
    """

    device_id: int
    event_type: MonitoringEventType
    app_package: str | None = None
    occurred_at: datetime | None = None
    metadata_json: dict | None = None

    @field_validator("app_package")
    @classmethod
    def app_package_must_be_valid(cls, value: str | None) -> str | None:
        return _validate_package_name(value, "app_package")


class MonitoringEventResponse(BaseModel):
    """One persisted monitoring event."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    device_id: int | None = None
    event_type: str
    app_package: str | None = None
    occurred_at: datetime
    metadata_json: dict | None = None


# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------


class MonitoringSummary(BaseModel):
    """Basic read-only monitoring summary (aggregated from the stored rows).

    Deliberately minimal — weekly/monthly reports, Your Score, leaderboard and
    ranking are later phases.
    """

    total_app_usage_seconds: int
    total_launches: int
    monitored_apps_count: int
    event_count: int
