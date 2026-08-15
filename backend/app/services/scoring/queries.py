"""queries.py — ShortsCap backend: scoring query layer.

Read-only SQL aggregations that feed the score components. All methods
filter by `user_id` (the score only ever uses the current user's data) and
run as grouped SQL — never whole-table loads, never N+1 loops.

Time conventions match the rest of the backend: `study_sessions.ended_at`,
`website_events.occurred_at`, `monitoring_events.occurred_at` are naive-UTC
datetimes; `app_usage.usage_date` and `shorts_usage.usage_date` are DATE
columns. Period boundaries are expanded to
[start 00:00:00, end 23:59:59] for the datetime columns.
"""

from datetime import date, datetime, time

from sqlalchemy import case, func
from sqlalchemy.orm import Session

from app.models.app_usage import AppUsage
from app.models.blocked_website import BlockedWebsite
from app.models.monitoring_event import MonitoringEvent
from app.models.shorts_settings import ShortsSettings
from app.models.shorts_usage import ShortsUsage
from app.models.study_session import StudySession
from app.models.website_event import WebsiteEvent
from app.services.scoring.constants import MIN_MEANINGFUL_SESSION_SEC

_TERMINAL = ("completed", "cancelled")


def _dt_range(start: date, end: date) -> tuple[datetime, datetime]:
    return datetime.combine(start, time.min), datetime.combine(end, time.max)


class ScoringQueries:
    """SQL aggregation methods for the score engine (no business logic)."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def shorts_limit_minutes(self, user_id: int) -> int | None:
        """The user's configured daily Shorts limit (minutes), or None when
        unset (the component falls back to the approved default)."""
        return (
            self.db.query(ShortsSettings.daily_limit_minutes)
            .filter(ShortsSettings.user_id == user_id)
            .scalar()
        )

    def study_aggregates(self, user_id: int, start: date, end: date) -> dict:
        """Meaningful study aggregates (terminal status + >= 300 s) for
        sessions ENDED in the range."""
        start_dt, end_dt = _dt_range(start, end)
        completed, total, total_seconds = (
            self.db.query(
                func.coalesce(
                    func.sum(case((StudySession.status == "completed", 1), else_=0)), 0
                ),
                func.count(StudySession.id),
                func.coalesce(func.sum(StudySession.actual_duration_seconds), 0),
            )
            .filter(
                StudySession.user_id == user_id,
                StudySession.status.in_(_TERMINAL),
                StudySession.actual_duration_seconds >= MIN_MEANINGFUL_SESSION_SEC,
                StudySession.ended_at >= start_dt,
                StudySession.ended_at <= end_dt,
            )
            .one()
        )
        return {
            "completed": int(completed or 0),
            "total": int(total or 0),
            "total_seconds": int(total_seconds or 0),
        }

    def shorts_days(self, user_id: int, start: date, end: date) -> list[dict]:
        """Per-day Shorts usage in the range (aggregated by usage_date)."""
        rows = (
            self.db.query(
                ShortsUsage.usage_date,
                func.coalesce(func.sum(ShortsUsage.duration_seconds), 0),
                func.coalesce(
                    func.max(case((ShortsUsage.warning_triggered.is_(True), 1), else_=0)), 0
                ),
                func.coalesce(
                    func.max(case((ShortsUsage.limit_reached.is_(True), 1), else_=0)), 0
                ),
            )
            .filter(
                ShortsUsage.user_id == user_id,
                ShortsUsage.usage_date >= start,
                ShortsUsage.usage_date <= end,
            )
            .group_by(ShortsUsage.usage_date)
            .all()
        )
        return [
            {
                "minutes": round((seconds or 0) / 60, 4),
                "warning_triggered": bool(warning),
                "limit_reached": bool(limit),
            }
            for _, seconds, warning, limit in rows
        ]

    def app_days(self, user_id: int, start: date, end: date) -> list[dict]:
        """Per-day total phone usage in minutes (aggregated by usage_date)."""
        rows = (
            self.db.query(
                AppUsage.usage_date,
                func.coalesce(func.sum(AppUsage.duration_seconds), 0),
            )
            .filter(
                AppUsage.user_id == user_id,
                AppUsage.usage_date >= start,
                AppUsage.usage_date <= end,
            )
            .group_by(AppUsage.usage_date)
            .all()
        )
        return [{"minutes": round((seconds or 0) / 60, 4)} for _, seconds in rows]

    def enforcement_events(self, user_id: int, start: date, end: date) -> int:
        """Count of LIMIT_REACHED / APP_RESTRICTED monitoring events in range."""
        start_dt, end_dt = _dt_range(start, end)
        return (
            self.db.query(func.count(MonitoringEvent.id))
            .filter(
                MonitoringEvent.user_id == user_id,
                MonitoringEvent.event_type.in_(["LIMIT_REACHED", "APP_RESTRICTED"]),
                MonitoringEvent.occurred_at >= start_dt,
                MonitoringEvent.occurred_at <= end_dt,
            )
            .scalar()
            or 0
        )

    def blocked_active_count(self, user_id: int) -> int:
        """Number of the user's currently-active blocked websites."""
        return (
            self.db.query(func.count(BlockedWebsite.id))
            .filter(
                BlockedWebsite.user_id == user_id,
                BlockedWebsite.is_blocked.is_(True),
            )
            .scalar()
            or 0
        )

    def web_events(self, user_id: int, start: date, end: date) -> list[dict]:
        """Website events in the range (type + domain for repeat detection)."""
        start_dt, end_dt = _dt_range(start, end)
        rows = (
            self.db.query(WebsiteEvent.event_type, WebsiteEvent.domain)
            .filter(
                WebsiteEvent.user_id == user_id,
                WebsiteEvent.occurred_at >= start_dt,
                WebsiteEvent.occurred_at <= end_dt,
            )
            .all()
        )
        return [{"type": event_type, "domain": domain} for event_type, domain in rows]

    def active_days(self, user_id: int, start: date, end: date) -> int:
        """Distinct calendar days in the range with ANY recorded activity
        (study session ended, app usage, shorts usage, website event or
        monitoring event). Days count once regardless of row count."""
        start_dt, end_dt = _dt_range(start, end)
        dates: set[date] = set()

        for row, in (
            self.db.query(func.date(StudySession.ended_at))
            .filter(
                StudySession.user_id == user_id,
                StudySession.ended_at >= start_dt,
                StudySession.ended_at <= end_dt,
            )
            .distinct()
            .all()
        ):
            if row is not None:
                dates.add(row)

        for row, in (
            self.db.query(AppUsage.usage_date)
            .filter(
                AppUsage.user_id == user_id,
                AppUsage.usage_date >= start,
                AppUsage.usage_date <= end,
            )
            .distinct()
            .all()
        ):
            if row is not None:
                dates.add(row)

        for row, in (
            self.db.query(ShortsUsage.usage_date)
            .filter(
                ShortsUsage.user_id == user_id,
                ShortsUsage.usage_date >= start,
                ShortsUsage.usage_date <= end,
            )
            .distinct()
            .all()
        ):
            if row is not None:
                dates.add(row)

        for row, in (
            self.db.query(func.date(WebsiteEvent.occurred_at))
            .filter(
                WebsiteEvent.user_id == user_id,
                WebsiteEvent.occurred_at >= start_dt,
                WebsiteEvent.occurred_at <= end_dt,
            )
            .distinct()
            .all()
        ):
            if row is not None:
                dates.add(row)

        for row, in (
            self.db.query(func.date(MonitoringEvent.occurred_at))
            .filter(
                MonitoringEvent.user_id == user_id,
                MonitoringEvent.occurred_at >= start_dt,
                MonitoringEvent.occurred_at <= end_dt,
            )
            .distinct()
            .all()
        ):
            if row is not None:
                dates.add(row)

        return len(dates)
