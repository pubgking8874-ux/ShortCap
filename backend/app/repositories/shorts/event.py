"""event.py — ShortsCap backend: shorts event repository.

ShortsEventRepository — database operations ONLY (no business rules).
Validation and device ownership live in the service layer.
"""

from datetime import datetime

from sqlalchemy.orm import Session

from app.models.shorts_event import ShortsEvent


class ShortsEventRepository:
    """Data access for the `shorts_events` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_id(self, event_id: int) -> ShortsEvent | None:
        """Return one event by id, or None."""
        return self.db.query(ShortsEvent).filter(ShortsEvent.id == event_id).first()

    def create(
        self,
        user_id: int,
        event_type: str,
        occurred_at: datetime,
        device_id: int | None = None,
        duration_seconds: int | None = None,
        metadata_json: dict | None = None,
    ) -> ShortsEvent:
        """Insert one Shorts event row."""
        event = ShortsEvent(
            user_id=user_id,
            device_id=device_id,
            event_type=event_type,
            occurred_at=occurred_at,
            duration_seconds=duration_seconds,
            metadata_json=metadata_json,
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
        start_date: datetime | None = None,
        end_date: datetime | None = None,
        offset: int = 0,
        limit: int | None = None,
    ) -> list[ShortsEvent]:
        """Return the user's events (newest first), optionally filtered by
        event type, device or occurred-at range, paginated."""
        query = self.db.query(ShortsEvent).filter(ShortsEvent.user_id == user_id)
        if event_type is not None:
            query = query.filter(ShortsEvent.event_type == event_type)
        if device_id is not None:
            query = query.filter(ShortsEvent.device_id == device_id)
        if start_date is not None:
            query = query.filter(ShortsEvent.occurred_at >= start_date)
        if end_date is not None:
            query = query.filter(ShortsEvent.occurred_at <= end_date)
        query = query.order_by(ShortsEvent.occurred_at.desc(), ShortsEvent.id.desc())
        if offset:
            query = query.offset(offset)
        if limit is not None:
            query = query.limit(limit)
        return query.all()
