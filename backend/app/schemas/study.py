"""study.py — ShortsCap backend: Pydantic schemas for the study data layer.

Request/response models for the Study API (schedules, sessions, breaks,
events). Field names and semantics mirror the Android app's study concepts:

  - StudyScheduleEntry  -> subject, days, start time, duration, reminder,
                           enabled  (days_of_week here is the same weekday
                           concept; stored comma-separated, exposed as a list)
  - StudySession        -> timestamp-based session (start/end/duration)

Input and output schemas are separated so validation (positive durations,
non-negative reminders, valid days, valid status values) lives in the schema
layer — FastAPI rejects invalid input with 422 before any service runs.
"""

from datetime import datetime, time
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

# Event types the backend actually creates (plus STUDY_REMINDER, which is
# reserved for the future reminder engine — the backend does not invent it).
StudyEventType = Literal[
    "STUDY_STARTED",
    "STUDY_ENDED",
    "STUDY_CANCELLED",
    "BREAK_STARTED",
    "BREAK_ENDED",
    "STUDY_REMINDER",
]

# Valid session / break status values stored in the schema's `status` column.
SessionStatus = Literal["active", "completed", "cancelled"]
BreakStatus = Literal["active", "completed"]

# Canonical weekday codes stored in study_schedules.days_of_week
# (comma-separated, e.g. "Mon,Tue,Wed").
StudyDayCode = Literal["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

# Accept full names or abbreviations in input; normalize to 3-letter codes.
_DAY_ALIASES = {
    "mon": "Mon", "tue": "Tue", "wed": "Wed", "thu": "Thu",
    "fri": "Fri", "sat": "Sat", "sun": "Sun",
    "monday": "Mon", "tuesday": "Tue", "wednesday": "Wed",
    "thursday": "Thu", "friday": "Fri", "saturday": "Sat", "sunday": "Sun",
}


def _normalize_days(value: list[str] | None) -> list[str] | None:
    """Validate + normalize a list of weekday names to canonical 3-letter codes."""
    if value is None:
        return None
    normalized: list[str] = []
    for day in value:
        key = day.strip().lower()
        if key not in _DAY_ALIASES:
            raise ValueError(
                f"Invalid day: {day!r}. Use e.g. 'Mon' or 'Monday'."
            )
        normalized.append(_DAY_ALIASES[key])
    return normalized


# ---------------------------------------------------------------------------
# Study schedules
# ---------------------------------------------------------------------------


class StudyScheduleCreate(BaseModel):
    """Create a study schedule. `title` is required; durations must be
    positive and reminders non-negative."""

    title: str = Field(min_length=1, max_length=100)
    subject: str | None = Field(default=None, max_length=100)
    start_time: time | None = None
    duration_minutes: int | None = Field(default=None, gt=0)
    days_of_week: list[str] | None = None
    reminder_minutes: int | None = Field(default=None, ge=0)
    is_enabled: bool = True

    @field_validator("days_of_week")
    @classmethod
    def days_must_be_valid(cls, value: list[str] | None) -> list[str] | None:
        return _normalize_days(value)


class StudyScheduleUpdate(BaseModel):
    """Partial update — every field is optional; unspecified fields are
    preserved by the API (PUT semantics)."""

    title: str | None = Field(default=None, min_length=1, max_length=100)
    subject: str | None = Field(default=None, max_length=100)
    start_time: time | None = None
    duration_minutes: int | None = Field(default=None, gt=0)
    days_of_week: list[str] | None = None
    reminder_minutes: int | None = Field(default=None, ge=0)
    is_enabled: bool | None = None

    @field_validator("days_of_week")
    @classmethod
    def days_must_be_valid(cls, value: list[str] | None) -> list[str] | None:
        return _normalize_days(value)


class StudyScheduleResponse(BaseModel):
    """Full schedule payload. `days_of_week` is exposed as a list of
    canonical 3-letter weekday codes."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    title: str | None = None
    subject: str | None = None
    start_time: time | None = None
    duration_minutes: int | None = None
    days_of_week: list[str] = []
    reminder_minutes: int | None = None
    is_enabled: bool
    created_at: datetime
    updated_at: datetime

    @field_validator("days_of_week", mode="before")
    @classmethod
    def split_stored_days(cls, value: object) -> list[str]:
        """The DB stores a comma-separated string; expose it as a list."""
        if value is None:
            return []
        if isinstance(value, list):
            return [str(day) for day in value]
        return [day for day in str(value).split(",") if day]


# ---------------------------------------------------------------------------
# Study sessions
# ---------------------------------------------------------------------------


class StudySessionStart(BaseModel):
    """Start a study session. `schedule_id` / `device_id` are optional;
    `planned_duration_seconds` must be positive when supplied."""

    schedule_id: int | None = None
    device_id: int | None = None
    planned_duration_seconds: int | None = Field(default=None, gt=0)


class StudySessionEnd(BaseModel):
    """Optional body for ending a session. `cancelled: true` explicitly
    represents cancellation (status `cancelled` + STUDY_CANCELLED event);
    the default completes the session normally."""

    cancelled: bool = False


class StudySessionResponse(BaseModel):
    """Full study session payload."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    schedule_id: int | None = None
    device_id: int | None = None
    started_at: datetime | None = None
    ended_at: datetime | None = None
    planned_duration_seconds: int | None = None
    actual_duration_seconds: int | None = None
    status: str
    created_at: datetime


# ---------------------------------------------------------------------------
# Break sessions
# ---------------------------------------------------------------------------


class BreakSessionResponse(BaseModel):
    """Full break session payload."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    study_session_id: int
    started_at: datetime | None = None
    ended_at: datetime | None = None
    duration_seconds: int | None = None
    status: str
    created_at: datetime


# ---------------------------------------------------------------------------
# Study events
# ---------------------------------------------------------------------------


class StudyEventResponse(BaseModel):
    """One study event (STUDY_STARTED / STUDY_ENDED / STUDY_CANCELLED /
    BREAK_STARTED / BREAK_ENDED)."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    study_session_id: int | None = None
    break_session_id: int | None = None
    event_type: str
    event_time: datetime
    metadata_json: dict | None = None
