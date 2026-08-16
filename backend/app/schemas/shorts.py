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

# Cross-platform short-form identity (Phase 11A) — mirrors the Android
# `ShortPlatform` / `ShortSurface` enums. `UNKNOWN` is the explicit marker for
# clients that do not (yet) send a value; it is never a fabricated platform.
ShortPlatformLiteral = Literal[
    "YOUTUBE", "INSTAGRAM", "TIKTOK", "SNAPCHAT", "FACEBOOK",
    "MOJ", "X", "LINKEDIN", "UNKNOWN",
]
ShortSurfaceLiteral = Literal[
    "YOUTUBE_SHORTS", "INSTAGRAM_REELS", "FACEBOOK_REELS", "TIKTOK_SHORT_FEED",
    "SNAPCHAT_SPOTLIGHT", "X_SHORT_VIDEO", "LINKEDIN_SHORT_VIDEO",
    "MOJ_SHORT_VIDEO", "UNKNOWN",
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

    `platform` / `surface` (Phase 11A) are OPTIONAL for backward
    compatibility: when omitted they are stored as `UNKNOWN`. The logical
    daily identity is (user + device + platform + surface + usage_date), so
    the same platform/surface/day can never create duplicate rows.
    """

    device_id: int
    usage_date: date
    shorts_count: int = Field(default=0, ge=0)
    duration_seconds: int = Field(default=0, ge=0)
    warning_triggered: bool = False
    limit_reached: bool = False
    platform: ShortPlatformLiteral | None = None
    surface: ShortSurfaceLiteral | None = None


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
    platform: str = "UNKNOWN"
    surface: str = "UNKNOWN"
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
# Shorts Control — 24-hour limit cycle + HUD preference + insights
# ---------------------------------------------------------------------------


# Cycle status values mirror the Android ShortsLimitCycleStatus enum exactly.
ShortsLimitCycleStatus = Literal["ACTIVE", "LIMIT_REACHED", "EXPIRED", "DISABLED"]

# HUD appearance values mirror the Android ShortsHudAppearance enum exactly.
ShortsHudAppearance = Literal["BRAIN", "LIVE_COUNTER", "SHORTSCAP"]


class ShortsLimitCycleResponse(BaseModel):
    """One persisted 24-hour Shorts limit cycle.

    `remaining_seconds` and `usage_ratio` are DERIVED at response time (the
    spec requires them on the limit-cycle API); they are never persisted as
    continuously decreasing values. Defaults keep `model_validate` working
    from ORM objects — the router fills them in explicitly."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    device_id: int | None = None
    limit_count: int
    current_count: int
    cycle_started_at: datetime
    cycle_expires_at: datetime
    status: str
    warning_triggered: bool
    limit_reached: bool
    remaining_seconds: int = 0
    usage_ratio: float = 0.0
    created_at: datetime
    updated_at: datetime


class ShortControlLimitCycle(BaseModel):
    """The computed limit-cycle block of the Shorts Control response: the
    authoritative synchronized cycle info Android renders (count / limit,
    status, window times, remaining seconds and usage ratio for circular
    progress). Remaining time is DERIVED from timestamps at request time —
    never a persisted, continuously decreasing value."""

    limit_count: int
    current_count: int
    status: str
    cycle_started_at: datetime
    cycle_expires_at: datetime
    remaining_seconds: int
    usage_ratio: float
    warning_triggered: bool
    limit_reached: bool


class ShortPlatformConfig(BaseModel):
    """One canonical short-form platform configuration option (Short
    Applications). The eight platforms are the product's supported set;
    `enabled` is the default configuration state — runtime per-platform
    toggles remain Android-local until the settings sync phase, and these
    flags never claim real-device verification."""

    id: str
    name: str
    domain: str
    enabled: bool


class ShortControlApplications(BaseModel):
    """The Short Applications block — the canonical platform catalog."""

    platforms: list[ShortPlatformConfig]


class ShortsInsightsPeriod(BaseModel):
    """One period's Shorts usage summary (aggregated from stored
    `shorts_usage` rows — real data only, missing platforms never
    fabricated)."""

    total_shorts_count: int
    total_duration_seconds: int
    warning_count: int
    limit_reached_count: int
    platform_breakdown: list[dict]


class ShortsInsights(BaseModel):
    """The Shorts Insights block — Yesterday / Today / This Week / This Month."""

    yesterday: ShortsInsightsPeriod
    today: ShortsInsightsPeriod
    this_week: ShortsInsightsPeriod
    this_month: ShortsInsightsPeriod


class ShortControlHud(BaseModel):
    """The HUD block — the persisted HUD appearance preference."""

    appearance: str


class ShortControlResponse(BaseModel):
    """The combined Shorts Control state consumed by Android:
    applications (canonical platform catalog), the current limit cycle (or
    None when no cycle is active), the HUD appearance preference and the
    read-only insights summaries. The Android app remains the real-time
    enforcement authority — this is synchronized configuration + state only."""

    applications: ShortControlApplications
    limit_cycle: ShortControlLimitCycle | None = None
    hud: ShortControlHud
    insights: ShortsInsights


class ShortsControlUpdate(BaseModel):
    """Partial update of the persisted Shorts Control settings (PUT
    /shorts/control). Unspecified fields are preserved. Changing the limit
    never resets an active cycle's count or 24-hour timer — only the
    threshold changes for the existing window."""

    limit_count: int | None = Field(default=None, ge=0)
    warning_count: int | None = Field(default=None, ge=0)
    warning_minutes: int | None = Field(default=None, ge=0)
    is_enabled: bool | None = None
    strict_mode_enabled: bool | None = None
    hud_appearance: ShortsHudAppearance | None = None


class ShortsLimitCycleActivate(BaseModel):
    """Activate a 24-hour Shorts limit cycle with the given limit count.
    `device_id` is optional (the approved single-device development reality
    keeps the active cycle per-user); when supplied it must belong to the
    caller. If an active cycle already exists, the API returns it unchanged
    instead of creating a second one."""

    limit_count: int = Field(..., gt=0)
    device_id: int | None = None


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
