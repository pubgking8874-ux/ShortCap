"""event.py — ShortsCap backend: website event service.

WebsiteEventService — business-level operations for website events:

  * events are attached to the CURRENT user (never a client-supplied id)
  * device ownership is validated before storing (unknown / another user's
    device -> 404)
  * `blocked_website_id` ownership is validated when supplied
  * `domain` is normalized through the shared domain utility before storage
  * timestamps are normalized to the backend's naive-UTC convention (aware
    datetimes are converted to UTC and stripped of tzinfo; naive datetimes are
    treated as already-UTC)
  * event_type is restricted to BLOCK_ATTEMPT / BLOCKED / UNBLOCKED (schema)

No real-time website blocking happens here — Android observes web activity,
enforces blocking and syncs the events to this layer.
"""

from datetime import datetime, timezone

from sqlalchemy.orm import Session

from app.models.blocked_website import BlockedWebsite
from app.models.device import Device
from app.models.website_event import WebsiteEvent
from app.repositories.web.event import WebsiteEventRepository
from app.services.web.errors import (
    WebNotFoundError,
    WebValidationError,
)
from app.utils.datetime import utcnow
from app.utils.domain import normalize_domain


def _to_naive_utc(value: datetime) -> datetime:
    """Normalize any datetime to the backend's naive-UTC convention."""
    if value.tzinfo is not None:
        return value.astimezone(timezone.utc).replace(tzinfo=None)
    return value  # already naive — treated as UTC (documented convention)


class WebsiteEventService:
    """Business operations for website events."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.repository = WebsiteEventRepository(db)

    def _validate_device_owner(self, user_id: int, device_id: int | None) -> None:
        """Reject device references that don't exist or aren't the user's."""
        if device_id is None:
            return
        device = (
            self.db.query(Device)
            .filter(Device.id == device_id, Device.user_id == user_id)
            .first()
        )
        if device is None:
            raise WebNotFoundError("Device not found.")

    def _validate_website_owner(
        self, user_id: int, blocked_website_id: int | None
    ) -> None:
        """Reject blocked-website references that don't exist or aren't the
        user's (404 — never reveals another user's record)."""
        if blocked_website_id is None:
            return
        website = (
            self.db.query(BlockedWebsite)
            .filter(
                BlockedWebsite.id == blocked_website_id,
                BlockedWebsite.user_id == user_id,
            )
            .first()
        )
        if website is None:
            raise WebNotFoundError("Blocked website not found.")

    def create_event(self, user_id: int, data: dict) -> WebsiteEvent:
        """Validate and persist one website event for the user."""
        device_id = data.get("device_id")
        blocked_website_id = data.get("blocked_website_id")
        self._validate_device_owner(user_id, device_id)
        self._validate_website_owner(user_id, blocked_website_id)

        domain: str | None = None
        if data.get("domain") is not None:
            domain = normalize_domain(data["domain"])
            if domain is None:
                raise WebValidationError("Invalid domain.")

        occurred_at = data.get("occurred_at")
        if occurred_at is None:
            occurred_at = utcnow()
        else:
            occurred_at = _to_naive_utc(occurred_at)

        return self.repository.create(
            user_id=user_id,
            event_type=data["event_type"],
            occurred_at=occurred_at,
            device_id=device_id,
            blocked_website_id=blocked_website_id,
            domain=domain,
        )

    def list_events(
        self,
        user_id: int,
        event_type: str | None = None,
        device_id: int | None = None,
        domain: str | None = None,
        start_date: datetime | None = None,
        end_date: datetime | None = None,
        page: int = 1,
        page_size: int = 50,
    ) -> list[WebsiteEvent]:
        """Return the user's events (paginated, newest first), filtered by
        event type, device, normalized domain or occurred-at range. Only the
        caller's own events are ever returned."""
        if start_date is not None and end_date is not None and start_date > end_date:
            raise WebValidationError("start_date must not be after end_date.")
        normalized = normalize_domain(domain) if domain is not None else None
        return self.repository.list_user_events(
            user_id,
            event_type=event_type,
            device_id=device_id,
            domain=normalized,
            start_date=start_date,
            end_date=end_date,
            offset=(page - 1) * page_size,
            limit=page_size,
        )

    def summary(self, user_id: int) -> dict:
        """Basic read-only web summary: per-type event counts + the number of
        distinct domains the user currently has blocked. Deliberately minimal
        — weekly/monthly reports, Your Score and Rank are later phases."""
        counts = self.repository.count_events_by_type(user_id)
        return {
            "total_block_attempts": counts.get("BLOCK_ATTEMPT", 0),
            "total_blocked_events": counts.get("BLOCKED", 0),
            "total_unblock_events": counts.get("UNBLOCKED", 0),
            "unique_blocked_domains": self.repository.count_blocked_domains(user_id),
        }
