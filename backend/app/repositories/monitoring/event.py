"""event.py — ShortsCap backend: monitoring event repository.

MonitoringEventRepository — database operations ONLY (no business rules).
Validation and device ownership live in the service layer.
"""

from datetime import datetime

from sqlalchemy import func
from sqlalchemy.orm import Session

from app.models.monitoring_event import MonitoringEvent


class MonitoringEventRepository:
    """Data access for the `monitoring_events` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def create(
        self,
        user_id: int,
        event_type: str,
        occurred_at: datetime,
        device_id: int | None = None,
        app_package: str | None = None,
        metadata_json: dict | None = None,
    ) -> MonitoringEvent:
        """Insert one monitoring event row."""
        event = MonitoringEvent(
            user_id=user_id,
            device_id=device_id,
            event_type=event_type,
            app_package=app_package,
            occurred_at=occurred_at,
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
        app_package: str | None = None,
        start_date: datetime | None = None,
        end_date: datetime | None = None,
        offset: int = 0,
        limit: int | None = None,
    ) -> list[MonitoringEvent]:
        """Return the user's events (newest first), optionally filtered by
        event type, device, app package or occurred-at range, paginated."""
        query = self.db.query(MonitoringEvent).filter(
            MonitoringEvent.user_id == user_id
        )
        if event_type is not None:
            query = query.filter(MonitoringEvent.event_type == event_type)
        if device_id is not None:
            query = query.filter(MonitoringEvent.device_id == device_id)
        if app_package is not None:
            query = query.filter(MonitoringEvent.app_package == app_package)
        if start_date is not None:
            query = query.filter(MonitoringEvent.occurred_at >= start_date)
        if end_date is not None:
            query = query.filter(MonitoringEvent.occurred_at <= end_date)
        query = query.order_by(
            MonitoringEvent.occurred_at.desc(), MonitoringEvent.id.desc()
        )
        if offset:
            query = query.offset(offset)
        if limit is not None:
            query = query.limit(limit)
        return query.all()

    def count_for_user(self, user_id: int) -> int:
        """Total number of monitoring events for one user."""
        return (
            self.db.query(func.count(MonitoringEvent.id))
            .filter(MonitoringEvent.user_id == user_id)
            .scalar()
            or 0
        )
