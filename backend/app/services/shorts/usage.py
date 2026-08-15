"""usage.py — ShortsCap backend: shorts usage service.

ShortsUsageService — business-level operations for synchronized Shorts usage:

  * every record is attached to the CURRENT user (never a client-supplied id)
  * device ownership is validated before anything is stored
  * idempotent per-day upsert (see ShortsUsageRepository) prevents duplicates
  * warning_triggered / limit_reached are persisted EXACTLY as supplied — the
    Android enforcement system remains authoritative for real-time limit
    state; this layer does not decide limits
  * aggregate summary values

The backend does NOT perform real-time Shorts detection — Android observes
Shorts activity (start/end detection, the 3–5 second counting logic, limit
enforcement) and syncs the summaries.
"""

from datetime import date

from sqlalchemy.orm import Session

from app.models.device import Device
from app.models.shorts_usage import ShortsUsage
from app.repositories.shorts import ShortsUsageRepository
from app.services.shorts.errors import (
    ShortsNotFoundError,
    ShortsValidationError,
)


class ShortsUsageService:
    """Business operations for Shorts usage summaries."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.repository = ShortsUsageRepository(db)

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
            raise ShortsNotFoundError("Device not found.")

    def sync_usage(self, user_id: int, records: list[dict]) -> list[ShortsUsage]:
        """Idempotently persist a batch of daily Shorts summaries for the user.

        Each record is validated (device ownership) and upserted per
        (user, device, usage_date). Returns the resulting rows in submission
        order.
        """
        synced: list[ShortsUsage] = []
        for record in records:
            self._validate_device_owner(user_id, record.get("device_id"))
            usage = self.repository.upsert_daily_usage(
                user_id=user_id,
                device_id=record.get("device_id"),
                usage_date=record["usage_date"],
                shorts_count=record.get("shorts_count", 0),
                duration_seconds=record.get("duration_seconds", 0),
                warning_triggered=record.get("warning_triggered", False),
                limit_reached=record.get("limit_reached", False),
            )
            synced.append(usage)
        return synced

    def list_usage(
        self,
        user_id: int,
        device_id: int | None = None,
        date_from: date | None = None,
        date_to: date | None = None,
        page: int = 1,
        page_size: int = 50,
    ) -> list[ShortsUsage]:
        """Return the user's Shorts usage history (paginated), filtered by
        device or date range. Only the caller's own rows are ever returned."""
        if date_from is not None and date_to is not None and date_from > date_to:
            raise ShortsValidationError("date_from must not be after date_to.")
        return self.repository.list_user_usage(
            user_id,
            device_id=device_id,
            date_from=date_from,
            date_to=date_to,
            offset=(page - 1) * page_size,
            limit=page_size,
        )

    def summary(self, user_id: int) -> dict:
        """Basic Shorts summary for the user over the stored daily rows:
        totals, per-day averages and warning / limit counts."""
        aggregates = self.repository.aggregate_for_user(user_id)
        days = aggregates["days"]
        return {
            "total_shorts_count": aggregates["total_count"],
            "total_duration_seconds": aggregates["total_duration"],
            "average_daily_shorts": aggregates["total_count"] // days if days else 0,
            "average_daily_duration": aggregates["total_duration"] // days if days else 0,
            "warning_count": aggregates["warning_days"],
            "limit_reached_count": aggregates["limit_days"],
        }
