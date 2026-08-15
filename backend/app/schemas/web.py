"""web.py — ShortsCap backend: Pydantic schemas for the web data layer.

Request/response models for the Web API (blocked websites + website events).
Field names and semantics mirror the Android app's Web concepts:

  - a blocked website = a user's domain with a canonical `normalized_domain`
    (unique per user) and an `is_blocked` state (Android `WebRuleStatus`:
    BLOCKED / ALLOWED); `verification_status` is informational
    (`pending` | `verified` | `failed`).
  - website events are restricted to BLOCK_ATTEMPT / BLOCKED / UNBLOCKED —
    no invented taxonomy.

Domain normalization/validation happens in the SERVICE via the shared
`app/utils/domain.py` utility (single source of truth); the schema only
rejects obviously empty input so normalization stays centralized.
"""

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

WebEventType = Literal["BLOCK_ATTEMPT", "BLOCKED", "UNBLOCKED"]
VerificationStatus = Literal["pending", "verified", "failed"]


# ---------------------------------------------------------------------------
# Blocked websites
# ---------------------------------------------------------------------------


class BlockedWebsiteCreate(BaseModel):
    """Create a blocked website. The domain is normalized by the service;
    `is_blocked` defaults to true."""

    domain: str = Field(min_length=1, max_length=2048)
    verification_status: VerificationStatus = "pending"
    is_blocked: bool = True


class BlockedWebsiteUpdate(BaseModel):
    """Partial update — every field is optional; unspecified fields are
    preserved. The domain is re-normalized when supplied."""

    domain: str | None = Field(default=None, min_length=1, max_length=2048)
    verification_status: VerificationStatus | None = None
    is_blocked: bool | None = None


class BlockedWebsiteResponse(BaseModel):
    """One persisted blocked-website row."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    domain: str
    normalized_domain: str
    verification_status: str
    is_blocked: bool
    created_at: datetime
    updated_at: datetime


class BlockedCheckResponse(BaseModel):
    """Answer to "is this domain blocked for the current user?"."""

    domain: str
    normalized_domain: str
    is_present: bool
    is_blocked: bool


# ---------------------------------------------------------------------------
# Website events
# ---------------------------------------------------------------------------


class WebsiteEventCreate(BaseModel):
    """One website event submitted by Android. `domain` is normalized by the
    service; `occurred_at` defaults to the server's current UTC time."""

    device_id: int | None = None
    blocked_website_id: int | None = None
    domain: str | None = Field(default=None, max_length=2048)
    event_type: WebEventType
    occurred_at: datetime | None = None


class WebsiteEventResponse(BaseModel):
    """One persisted website event."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    device_id: int | None = None
    blocked_website_id: int | None = None
    domain: str | None = None
    event_type: str
    occurred_at: datetime


# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------


class WebSummary(BaseModel):
    """Basic read-only web summary (event counts + distinct blocked domains).

    Deliberately minimal — weekly/monthly reports, Your Score, Rank and
    leaderboard are later phases.
    """

    total_block_attempts: int
    total_blocked_events: int
    total_unblock_events: int
    unique_blocked_domains: int
