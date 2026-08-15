"""shorts.py — ShortsCap backend: Pydantic schemas for the shorts data layer.

Request/response models for the Shorts API (usage sync, events, summary).
Field names and semantics mirror the Android app's existing Shorts concepts:

  - Shorts usage is AGGREGATED per (device, day): count + duration plus the
    warning / limit flags for that day. The Android side observes Shorts
    activity locally (start/end detection, the 3–5 second counting logic,
    limit enforcement) and syncs summaries; the backend never detects Shorts.
  - event types map 1:1 to actual Android Shorts behaviors:
      SHORT_STARTED     -> determining when a Short starts
      SHORT_ENDED       -> determining when a Short ends
      SHORT_COUNTED     -> counting Shorts (the 3–5 second counting logic)
      WARNING_TRIGGERED -> the app's SHORTS_LIMIT_WARNING behavior
      LIMIT_REACHED     -> the app's SHORTS_LIMIT_REACHED behavior
    No invented taxonomy.

Input and output schemas are separated so validation (non-negative counts and
durations, valid event types, valid dates) lives in the schema layer —
FastAPI rejects invalid input with 422 before any service runs.
"""

from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

ShortsEventType = Literal[
    "SHORT_STARTED",
    "SHORT_COUNTED",
    "SHORT_ENDED",
    "WARNING_TRIGGERED",
    "LIMIT_REACHED",
]


# ---------------------------------------------------------------------------
# Shorts usage
# ---------------------------------------------------------------------------


class ShortsUsageRecord(BaseModel):
    """One aggregated daily Shorts usage summary submitted by Android.

    `device_id` and `usage_date` identify the summary; `shorts_count` and
    `duration_seconds` must be non-negative; `warning_triggered` /
    `limit_reached` are persisted exactly as supplied (the Android
    enforcement system is authoritative for real-time limit state — this
    layer does not decide limits). The backend attaches the development user
    identity — a client-supplied user_id is never trusted.
    """

    device_id: int
    usage_date: date
    shorts_count: int = Field(default=0, ge=0)
    duration_seconds: int = Field(default=0, ge=0)
    warning_triggered: bool = False
    limit_reached: bool = False


class ShortsUsageResponse(BaseModel):
    """One persisted aggregated Shorts usage row.

    NOTE: the approved `shorts_usage` table has `updated_at` but no
    `created_at`, so this response carries only `updated_at`.
    """

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    device_id: int | None = None
    usage_date: date | None = None
    shorts_count: int
    duration_seconds: int
    warning_triggered: bool
    limit_reached: bool
    updated_at: datetime


# ---------------------------------------------------------------------------
# Shorts events
# ---------------------------------------------------------------------------


class ShortsEventCreate(BaseModel):
    """One Shorts event submitted by Android.

    `occurred_at` is optional — when omitted the server stamps it with the
    current UTC time. Aware datetimes are normalized to naive UTC before
    storage (see the service). `duration_seconds`, when supplied, must be
    non-negative.
    """

    device_id: int
    event_type: ShortsEventType
    occurred_at: datetime | None = None
    duration_seconds: int | None = Field(default=None, ge=0)
    metadata_json: dict | None = None


class ShortsEventResponse(BaseModel):
    """One persisted Shorts event."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    device_id: int | None = None
    event_type: str
    occurred_at: datetime
    duration_seconds: int | None = None
    metadata_json: dict | None = None


# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------


class ShortsSummary(BaseModel):
    """Basic read-only Shorts summary (aggregated from the stored rows).

    Deliberately minimal — weekly/monthly reports, Your Score, Rank and
    leaderboard are later phases.
    """

    total_shorts_count: int
    total_duration_seconds: int
    average_daily_shorts: int
    average_daily_duration: int
    warning_count: int
    limit_reached_count: int
