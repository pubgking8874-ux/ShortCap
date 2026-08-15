"""event.py — ShortsCap backend: website event repository.

WebsiteEventRepository — database operations ONLY (no business rules).
Validation, normalization and ownership live in the service layer.
"""

from datetime import datetime

from sqlalchemy import func
from sqlalchemy.orm import Session

from app.models.blocked_website import BlockedWebsite
from app.models.website_event import WebsiteEvent


class WebsiteEventRepository:
    """Data access for the `website_events` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def create(
        self,
        user_id: int,
        event_type: str,
        occurred_at: datetime,
        device_id: int | None = None,
        blocked_website_id: int | None = None,
        domain: str | None = None,
    ) -> WebsiteEvent:
        """Insert one website event row."""
        event = WebsiteEvent(
            user_id=user_id,
            device_id=device_id,
            blocked_website_id=blocked_website_id,
            domain=domain,
            event_type=event_type,
            occurred_at=occurred_at,
        )
        self.db.add(event)
        self.db.commit()
        self.db.refresh(event)
        return event

    def list_user_events(
        self,
        user_id: int,
        event_type: str | None = None,
        device_id: int | None = None,
        domain: str | None = None,
        start_date: datetime | None = None,
        end_date: datetime | None = None,
        offset: int = 0,
        limit: int | None = None,
    ) -> list[WebsiteEvent]:
        """Return the user's events (newest first), optionally filtered by
        event type, device, domain or occurred-at range, paginated."""
        query = self.db.query(WebsiteEvent).filter(WebsiteEvent.user_id == user_id)
        if event_type is not None:
            query = query.filter(WebsiteEvent.event_type == event_type)
        if device_id is not None:
            query = query.filter(WebsiteEvent.device_id == device_id)
        if domain is not None:
            query = query.filter(WebsiteEvent.domain == domain)
        if start_date is not None:
            query = query.filter(WebsiteEvent.occurred_at >= start_date)
        if end_date is not None:
            query = query.filter(WebsiteEvent.occurred_at <= end_date)
        query = query.order_by(WebsiteEvent.occurred_at.desc(), WebsiteEvent.id.desc())
        if offset:
            query = query.offset(offset)
        if limit is not None:
            query = query.limit(limit)
        return query.all()

    def count_events_by_type(self, user_id: int) -> dict[str, int]:
        """Counts of the user's events grouped by event type."""
        rows = (
            self.db.query(WebsiteEvent.event_type, func.count(WebsiteEvent.id))
            .filter(WebsiteEvent.user_id == user_id)
            .group_by(WebsiteEvent.event_type)
            .all()
        )
        return {event_type: count for event_type, count in rows}

    def count_blocked_domains(self, user_id: int) -> int:
        """Number of distinct domains the user currently has configured as
        blocked (`blocked_websites` rows with `is_blocked = true`)."""
        return (
            self.db.query(func.count(func.distinct(BlockedWebsite.normalized_domain)))
            .filter(
                BlockedWebsite.user_id == user_id,
                BlockedWebsite.is_blocked.is_(True),
            )
            .scalar()
            or 0
        )
