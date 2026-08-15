"""event.py — ShortsCap backend: monitoring event service.

MonitoringEventService — business-level operations for monitoring events:

  * events are attached to the CURRENT user (never a client-supplied id)
  * device ownership is validated before storing
  * timestamps are normalized to the backend's naive-UTC convention:
    aware datetimes are converted to UTC and stripped of tzinfo (MySQL
    DATETIME has no timezone); naive datetimes are treated as already-UTC
  * event_type / app_package / metadata are validated (schema layer)

No real-time event detection happens here — Android submits the events.
"""

from datetime import datetime, timezone

from sqlalchemy.orm import Session

from app.models.device import Device
from app.models.monitoring_event import MonitoringEvent
from app.repositories.monitoring import MonitoringEventRepository
from app.services.monitoring.errors import (
    MonitoringNotFoundError,
    MonitoringValidationError,
)
from app.utils.datetime import utcnow


def _to_naive_utc(value: datetime) -> datetime:
    """Normalize any datetime to the backend's naive-UTC convention."""
    if value.tzinfo is not None:
        return value.astimezone(timezone.utc).replace(tzinfo=None)
    return value  # already naive — treated as UTC (documented convention)


class MonitoringEventService:
    """Business operations for monitoring events."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.repository = MonitoringEventRepository(db)

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
            raise MonitoringNotFoundError("Device not found.")

    def create_event(self, user_id: int, data: dict) -> MonitoringEvent:
        """Validate and persist one monitoring event for the user."""
        device_id = data.get("device_id")
        self._validate_device_owner(user_id, device_id)

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
            app_package=data.get("app_package"),
            metadata_json=data.get("metadata_json"),
        )

    def list_events(
        self,
        user_id: int,
        event_type: str | None = None,
        device_id: int | None = None,
        app_package: str | None = None,
        start_date: datetime | None = None,
        end_date: datetime | None = None,
        page: int = 1,
        page_size: int = 50,
    ) -> list[MonitoringEvent]:
        """Return the user's events (paginated), filtered by event type,
        device, app package or occurred-at range. Only the caller's own
        events are ever returned."""
        if start_date is not None and end_date is not None and start_date > end_date:
            raise MonitoringValidationError("start_date must not be after end_date.")
        return self.repository.list_user_events(
            user_id,
            event_type=event_type,
            device_id=device_id,
            app_package=app_package,
            start_date=start_date,
            end_date=end_date,
            offset=(page - 1) * page_size,
            limit=page_size,
        )

    def count_events(self, user_id: int) -> int:
        """Total monitoring events for one user (used by the summary)."""
        return self.repository.count_for_user(user_id)
