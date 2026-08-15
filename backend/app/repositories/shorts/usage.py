"""usage.py — ShortsCap backend: shorts usage repository.

ShortsUsageRepository — database operations ONLY (no business rules).
Validation, device ownership and normalization live in the service layer.

Duplicate handling (idempotent sync): the schema has NO unique constraint on
(user, device, usage_date), so instead of a database-level constraint the
repository performs a careful lookup-then-upsert (the same strategy as the
Monitoring layer): syncing the same daily summary twice OVERWRITES its values
instead of inserting duplicate rows.
"""

from datetime import date

from sqlalchemy import case, func
from sqlalchemy.orm import Session

from app.models.shorts_usage import ShortsUsage


class ShortsUsageRepository:
    """Data access for the `shorts_usage` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_id(self, usage_id: int) -> ShortsUsage | None:
        """Return one usage row by id, or None."""
        return self.db.query(ShortsUsage).filter(ShortsUsage.id == usage_id).first()

    def get_by_user_device_date(
        self,
        user_id: int,
        device_id: int | None,
        usage_date: date,
    ) -> ShortsUsage | None:
        """Look up the row that a sync payload would map to
        (user + device + usage_date)."""
        return (
            self.db.query(ShortsUsage)
            .filter(
                ShortsUsage.user_id == user_id,
                ShortsUsage.device_id == device_id,
                ShortsUsage.usage_date == usage_date,
            )
            .first()
        )

    def create(self, user_id: int, data: dict) -> ShortsUsage:
        """Insert one aggregated usage row."""
        usage = ShortsUsage(user_id=user_id, **data)
        self.db.add(usage)
        self.db.commit()
        self.db.refresh(usage)
        return usage

    def update(self, usage: ShortsUsage, data: dict) -> ShortsUsage:
        """Apply only the supplied, non-None values to an existing row."""
        for key, value in data.items():
            if value is not None:
                setattr(usage, key, value)
        self.db.commit()
        self.db.refresh(usage)
        return usage

    def upsert_daily_usage(
        self,
        user_id: int,
        device_id: int | None,
        usage_date: date,
        shorts_count: int,
        duration_seconds: int,
        warning_triggered: bool,
        limit_reached: bool,
    ) -> ShortsUsage:
        """Idempotent per-day upsert.

        If a row for (user, device, usage_date) exists, its values are
        OVERWRITTEN with the submitted values (last sync wins — re-syncing
        the same summary never doubles it); otherwise a new row is inserted.
        """
        usage = self.get_by_user_device_date(user_id, device_id, usage_date)
        if usage is None:
            return self.create(
                user_id,
                {
                    "device_id": device_id,
                    "usage_date": usage_date,
                    "shorts_count": shorts_count,
                    "duration_seconds": duration_seconds,
                    "warning_triggered": warning_triggered,
                    "limit_reached": limit_reached,
                },
            )
        return self.update(
            usage,
            {
                "shorts_count": shorts_count,
                "duration_seconds": duration_seconds,
                "warning_triggered": warning_triggered,
                "limit_reached": limit_reached,
            },
        )

    def list_user_usage(
        self,
        user_id: int,
        device_id: int | None = None,
        date_from: date | None = None,
        date_to: date | None = None,
        offset: int = 0,
        limit: int | None = None,
    ) -> list[ShortsUsage]:
        """Return the user's usage rows (newest date first), optionally
        filtered by device or usage-date range, with pagination."""
        query = self.db.query(ShortsUsage).filter(ShortsUsage.user_id == user_id)
        if device_id is not None:
            query = query.filter(ShortsUsage.device_id == device_id)
        if date_from is not None:
            query = query.filter(ShortsUsage.usage_date >= date_from)
        if date_to is not None:
            query = query.filter(ShortsUsage.usage_date <= date_to)
        query = query.order_by(ShortsUsage.usage_date.desc(), ShortsUsage.id.desc())
        if offset:
            query = query.offset(offset)
        if limit is not None:
            query = query.limit(limit)
        return query.all()

    def aggregate_for_user(self, user_id: int) -> dict:
        """Aggregated totals for one user over all stored daily summaries:
        total count, total duration, number of distinct usage days, number of
        days where a warning was triggered and days where the limit was
        reached."""
        # Booleans are counted with a CASE expression so the aggregation is
        # correct regardless of how the driver represents True/False.
        true_as_one = lambda column: case((column.is_(True), 1), else_=0)  # noqa: E731
        row = (
            self.db.query(
                func.coalesce(func.sum(ShortsUsage.shorts_count), 0),
                func.coalesce(func.sum(ShortsUsage.duration_seconds), 0),
                func.count(func.distinct(ShortsUsage.usage_date)),
                func.coalesce(func.sum(true_as_one(ShortsUsage.warning_triggered)), 0),
                func.coalesce(func.sum(true_as_one(ShortsUsage.limit_reached)), 0),
            )
            .filter(ShortsUsage.user_id == user_id)
            .one()
        )
        return {
            "total_count": int(row[0] or 0),
            "total_duration": int(row[1] or 0),
            "days": int(row[2] or 0),
            "warning_days": int(row[3] or 0),
            "limit_days": int(row[4] or 0),
        }
