"""usage.py — ShortsCap backend: app usage repository.

AppUsageRepository — database operations ONLY (no business rules).
Validation, device ownership and normalization live in the service layer.

Duplicate handling (idempotent sync): the schema has NO unique constraint on
(user, device, package, date), so instead of a database-level constraint the
repository performs a careful lookup-then-upsert: syncing the same summary
twice OVERWRITES the aggregate values instead of inserting duplicate rows.
"""

from datetime import date

from sqlalchemy import func
from sqlalchemy.orm import Session

from app.models.app_usage import AppUsage


class AppUsageRepository:
    """Data access for the `app_usage` table."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_id(self, usage_id: int) -> AppUsage | None:
        """Return one usage row by id, or None."""
        return self.db.query(AppUsage).filter(AppUsage.id == usage_id).first()

    def get_by_user_device_package_date(
        self,
        user_id: int,
        device_id: int | None,
        package_name: str,
        usage_date: date,
    ) -> AppUsage | None:
        """Look up the row that a sync payload would map to
        (user + device + package + usage_date)."""
        return (
            self.db.query(AppUsage)
            .filter(
                AppUsage.user_id == user_id,
                AppUsage.device_id == device_id,
                AppUsage.package_name == package_name,
                AppUsage.usage_date == usage_date,
            )
            .first()
        )

    def create(self, user_id: int, data: dict) -> AppUsage:
        """Insert one aggregated usage row."""
        usage = AppUsage(user_id=user_id, **data)
        self.db.add(usage)
        self.db.commit()
        self.db.refresh(usage)
        return usage

    def update(self, usage: AppUsage, data: dict) -> AppUsage:
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
        package_name: str,
        usage_date: date,
        duration_seconds: int,
        launch_count: int,
        app_name: str | None = None,
    ) -> AppUsage:
        """Idempotent per-day upsert.

        If a row for (user, device, package, date) exists, its aggregate
        values are OVERWRITTEN with the submitted values (last sync wins —
        re-syncing the same summary never doubles it); otherwise a new row is
        inserted.
        """
        usage = self.get_by_user_device_package_date(
            user_id, device_id, package_name, usage_date
        )
        if usage is None:
            return self.create(
                user_id,
                {
                    "device_id": device_id,
                    "package_name": package_name,
                    "app_name": app_name,
                    "usage_date": usage_date,
                    "duration_seconds": duration_seconds,
                    "launch_count": launch_count,
                },
            )
        return self.update(
            usage,
            {
                "duration_seconds": duration_seconds,
                "launch_count": launch_count,
                "app_name": app_name,
            },
        )

    def list_user_usage(
        self,
        user_id: int,
        device_id: int | None = None,
        package_name: str | None = None,
        date_from: date | None = None,
        date_to: date | None = None,
        offset: int = 0,
        limit: int | None = None,
    ) -> list[AppUsage]:
        """Return the user's usage rows (newest date first), optionally
        filtered by device, package or usage-date range, with pagination."""
        query = self.db.query(AppUsage).filter(AppUsage.user_id == user_id)
        if device_id is not None:
            query = query.filter(AppUsage.device_id == device_id)
        if package_name is not None:
            query = query.filter(AppUsage.package_name == package_name)
        if date_from is not None:
            query = query.filter(AppUsage.usage_date >= date_from)
        if date_to is not None:
            query = query.filter(AppUsage.usage_date <= date_to)
        query = query.order_by(AppUsage.usage_date.desc(), AppUsage.id.desc())
        if offset:
            query = query.offset(offset)
        if limit is not None:
            query = query.limit(limit)
        return query.all()

    def aggregate_for_user(self, user_id: int) -> dict:
        """Aggregated totals for one user: total duration, total launches,
        distinct monitored packages. Returns {'total_seconds': int,
        'total_launches': int, 'packages': int}."""
        row = (
            self.db.query(
                func.coalesce(func.sum(AppUsage.duration_seconds), 0),
                func.coalesce(func.sum(AppUsage.launch_count), 0),
                func.count(func.distinct(AppUsage.package_name)),
            )
            .filter(AppUsage.user_id == user_id)
            .one()
        )
        return {
            "total_seconds": int(row[0] or 0),
            "total_launches": int(row[1] or 0),
            "packages": int(row[2] or 0),
        }
