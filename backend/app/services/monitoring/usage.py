"""usage.py — ShortsCap backend: app usage service.

AppUsageService — business-level operations for synchronized app usage:

  * every record is attached to the CURRENT user (never a client-supplied id)
  * device ownership is validated before anything is stored
  * submitted records are normalized (stripped names)
  * idempotent per-day upsert (see AppUsageRepository) prevents duplicates
  * aggregate summary values

The backend does NOT calculate real-time usage — Android observes usage and
syncs the summaries.
"""

from datetime import date

from sqlalchemy.orm import Session

from app.models.app_usage import AppUsage
from app.models.device import Device
from app.repositories.monitoring import AppUsageRepository
from app.services.monitoring.errors import (
    MonitoringNotFoundError,
    MonitoringValidationError,
)


class AppUsageService:
    """Business operations for app usage summaries."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.repository = AppUsageRepository(db)

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

    def sync_usage(self, user_id: int, records: list[dict]) -> list[AppUsage]:
        """Idempotently persist a batch of usage summaries for the user.

        Each record is validated (device ownership), normalized and upserted
        per (user, device, package, date). Returns the resulting rows in
        submission order.
        """
        synced: list[AppUsage] = []
        for record in records:
            self._validate_device_owner(user_id, record.get("device_id"))
            usage = self.repository.upsert_daily_usage(
                user_id=user_id,
                device_id=record.get("device_id"),
                package_name=record["package_name"],
                usage_date=record["usage_date"],
                duration_seconds=record.get("duration_seconds", 0),
                launch_count=record.get("launch_count", 0),
                app_name=record.get("app_name"),
            )
            synced.append(usage)
        return synced

    def list_usage(
        self,
        user_id: int,
        device_id: int | None = None,
        package_name: str | None = None,
        date_from: date | None = None,
        date_to: date | None = None,
        page: int = 1,
        page_size: int = 50,
    ) -> list[AppUsage]:
        """Return the user's usage history (paginated), filtered by device,
        package or date range. Only the caller's own rows are ever returned."""
        if date_from is not None and date_to is not None and date_from > date_to:
            raise MonitoringValidationError("date_from must not be after date_to.")
        return self.repository.list_user_usage(
            user_id,
            device_id=device_id,
            package_name=package_name,
            date_from=date_from,
            date_to=date_to,
            offset=(page - 1) * page_size,
            limit=page_size,
        )

    def summary(self, user_id: int) -> dict:
        """Basic monitoring summary for the user (totals over stored rows).
        `event_count` is filled in by the router from the event service."""
        aggregates = self.repository.aggregate_for_user(user_id)
        return {
            "total_app_usage_seconds": aggregates["total_seconds"],
            "total_launches": aggregates["total_launches"],
            "monitored_apps_count": aggregates["packages"],
            "event_count": 0,
        }
