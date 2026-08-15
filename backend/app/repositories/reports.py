"""reports.py — ShortsCap backend: reporting query layer.

Read-only aggregations over the EXISTING domain tables for the Reports API.
No writes, no schema changes, no new tables — the raw data remains the
source of truth and every method filters by `user_id` (cross-user data is
never included). Aggregations run in SQL (SUM / COUNT / GROUP BY) so reports
never load whole tables into Python.

Time conventions match the rest of the backend: `study_sessions.ended_at`,
`monitoring_events.occurred_at`, `website_events.occurred_at` are naive-UTC
datetimes; `app_usage.usage_date` and `shorts_usage.usage_date` are DATE
columns. Period boundaries are passed as (start_date, end_date) and expanded
to [start 00:00:00, end 23:59:59] for the datetime columns.
"""

from datetime import date, datetime, time

from sqlalchemy import case, func
from sqlalchemy.orm import Session

from app.models.app_usage import AppUsage
from app.models.break_session import BreakSession
from app.models.monitoring_event import MonitoringEvent
from app.models.shorts_usage import ShortsUsage
from app.models.study_session import StudySession
from app.models.website_event import WebsiteEvent

# Terminal study statuses whose actual_duration_seconds is authoritative.
_TERMINAL = ("completed", "cancelled")


def _dt_range(start: date, end: date) -> tuple[datetime, datetime]:
    """Expand a date range to inclusive naive-UTC datetime boundaries."""
    return datetime.combine(start, time.min), datetime.combine(end, time.max)


class ReportingRepository:
    """SQL aggregation methods for the reporting layer (no business logic)."""

    def __init__(self, db: Session) -> None:
        self.db = db

    # ------------------------------------------------------------------
    # Study
    # ------------------------------------------------------------------

    def study_aggregates(self, user_id: int, start: date, end: date) -> dict:
        """Study totals for sessions that ENDED in the range, plus a per-day
        study-seconds map for the trend."""
        start_dt, end_dt = _dt_range(start, end)
        filters = (
            StudySession.user_id == user_id,
            StudySession.status.in_(_TERMINAL),
            StudySession.ended_at >= start_dt,
            StudySession.ended_at <= end_dt,
        )
        total, completed, cancelled = (
            self.db.query(
                func.coalesce(func.sum(StudySession.actual_duration_seconds), 0),
                func.coalesce(
                    func.sum(case((StudySession.status == "completed", 1), else_=0)), 0
                ),
                func.coalesce(
                    func.sum(case((StudySession.status == "cancelled", 1), else_=0)), 0
                ),
            )
            .filter(*filters)
            .one()
        )

        # Breaks are owned by study sessions (break_sessions has no user_id).
        break_filters = (
            StudySession.user_id == user_id,
            BreakSession.status == "completed",
            BreakSession.ended_at >= start_dt,
            BreakSession.ended_at <= end_dt,
        )
        break_seconds, completed_breaks = (
            self.db.query(
                func.coalesce(func.sum(BreakSession.duration_seconds), 0),
                func.coalesce(
                    func.sum(case((BreakSession.status == "completed", 1), else_=0)), 0
                ),
            )
            .join(StudySession, BreakSession.study_session_id == StudySession.id)
            .filter(*break_filters)
            .one()
        )

        trend_rows = (
            self.db.query(
                func.date(StudySession.ended_at),
                func.coalesce(func.sum(StudySession.actual_duration_seconds), 0),
            )
            .filter(*filters)
            .group_by(func.date(StudySession.ended_at))
            .all()
        )
        return {
            "total_study_seconds": int(total or 0),
            "completed_sessions": int(completed or 0),
            "cancelled_sessions": int(cancelled or 0),
            "break_seconds": int(break_seconds or 0),
            "completed_breaks": int(completed_breaks or 0),
            "trend": {row[0]: int(row[1]) for row in trend_rows if row[0] is not None},
        }

    # ------------------------------------------------------------------
    # Monitoring
    # ------------------------------------------------------------------

    def monitoring_aggregates(self, user_id: int, start: date, end: date) -> dict:
        """App-usage totals, distinct monitored apps, top apps, monitoring
        event count and a per-day app-usage-seconds map."""
        start_dt, end_dt = _dt_range(start, end)
        usage_filters = (
            AppUsage.user_id == user_id,
            AppUsage.usage_date >= start,
            AppUsage.usage_date <= end,
        )
        total_seconds, monitored_apps = (
            self.db.query(
                func.coalesce(func.sum(AppUsage.duration_seconds), 0),
                func.count(func.distinct(AppUsage.package_name)),
            )
            .filter(*usage_filters)
            .one()
        )

        event_count = (
            self.db.query(func.count(MonitoringEvent.id))
            .filter(
                MonitoringEvent.user_id == user_id,
                MonitoringEvent.occurred_at >= start_dt,
                MonitoringEvent.occurred_at <= end_dt,
            )
            .scalar()
            or 0
        )

        app_key = func.coalesce(AppUsage.app_name, AppUsage.package_name)
        top_rows = (
            self.db.query(
                app_key,
                func.coalesce(func.sum(AppUsage.duration_seconds), 0),
            )
            .filter(*usage_filters)
            .group_by(app_key)
            .order_by(func.sum(AppUsage.duration_seconds).desc())
            .limit(5)
            .all()
        )

        trend_rows = (
            self.db.query(
                AppUsage.usage_date,
                func.coalesce(func.sum(AppUsage.duration_seconds), 0),
            )
            .filter(*usage_filters)
            .group_by(AppUsage.usage_date)
            .all()
        )
        return {
            "total_app_usage_seconds": int(total_seconds or 0),
            "monitored_apps_count": int(monitored_apps or 0),
            "monitoring_event_count": int(event_count or 0),
            "top_apps": [
                {"app_name": name, "duration_seconds": int(seconds)}
                for name, seconds in top_rows
            ],
            "trend": {row[0]: int(row[1]) for row in trend_rows if row[0] is not None},
        }

    # ------------------------------------------------------------------
    # Shorts
    # ------------------------------------------------------------------

    def shorts_aggregates(self, user_id: int, start: date, end: date) -> dict:
        """Shorts totals, warning/limit day counts, per-platform breakdown
        and a per-day duration map."""
        filters = (
            ShortsUsage.user_id == user_id,
            ShortsUsage.usage_date >= start,
            ShortsUsage.usage_date <= end,
        )
        total_count, total_duration, warning_days, limit_days = (
            self.db.query(
                func.coalesce(func.sum(ShortsUsage.shorts_count), 0),
                func.coalesce(func.sum(ShortsUsage.duration_seconds), 0),
                func.coalesce(
                    func.sum(
                        case((ShortsUsage.warning_triggered.is_(True), 1), else_=0)
                    ),
                    0,
                ),
                func.coalesce(
                    func.sum(
                        case((ShortsUsage.limit_reached.is_(True), 1), else_=0)
                    ),
                    0,
                ),
            )
            .filter(*filters)
            .one()
        )

        platform_rows = (
            self.db.query(
                ShortsUsage.platform,
                func.coalesce(func.sum(ShortsUsage.shorts_count), 0),
                func.coalesce(func.sum(ShortsUsage.duration_seconds), 0),
            )
            .filter(*filters)
            .group_by(ShortsUsage.platform)
            .order_by(func.sum(ShortsUsage.duration_seconds).desc())
            .all()
        )

        trend_rows = (
            self.db.query(
                ShortsUsage.usage_date,
                func.coalesce(func.sum(ShortsUsage.duration_seconds), 0),
            )
            .filter(*filters)
            .group_by(ShortsUsage.usage_date)
            .all()
        )
        return {
            "total_shorts_count": int(total_count or 0),
            "total_duration_seconds": int(total_duration or 0),
            "warning_count": int(warning_days or 0),
            "limit_reached_count": int(limit_days or 0),
            "platform_breakdown": [
                {"platform": platform, "shorts_count": int(count), "duration_seconds": int(duration)}
                for platform, count, duration in platform_rows
            ],
            "trend": {row[0]: int(row[1]) for row in trend_rows if row[0] is not None},
        }

    # ------------------------------------------------------------------
    # Web
    # ------------------------------------------------------------------

    def web_aggregates(self, user_id: int, start: date, end: date) -> dict:
        """Website event counts by type, distinct blocked domains and a
        per-day block-attempt map."""
        start_dt, end_dt = _dt_range(start, end)
        count_rows = (
            self.db.query(WebsiteEvent.event_type, func.count(WebsiteEvent.id))
            .filter(
                WebsiteEvent.user_id == user_id,
                WebsiteEvent.occurred_at >= start_dt,
                WebsiteEvent.occurred_at <= end_dt,
            )
            .group_by(WebsiteEvent.event_type)
            .all()
        )
        counts = {event_type: count for event_type, count in count_rows}

        unique_domains = (
            self.db.query(func.count(func.distinct(WebsiteEvent.domain)))
            .filter(
                WebsiteEvent.user_id == user_id,
                WebsiteEvent.event_type == "BLOCKED",
                WebsiteEvent.occurred_at >= start_dt,
                WebsiteEvent.occurred_at <= end_dt,
            )
            .scalar()
            or 0
        )

        trend_rows = (
            self.db.query(
                func.date(WebsiteEvent.occurred_at),
                func.count(WebsiteEvent.id),
            )
            .filter(
                WebsiteEvent.user_id == user_id,
                WebsiteEvent.event_type == "BLOCK_ATTEMPT",
                WebsiteEvent.occurred_at >= start_dt,
                WebsiteEvent.occurred_at <= end_dt,
            )
            .group_by(func.date(WebsiteEvent.occurred_at))
            .all()
        )
        return {
            "total_block_attempts": int(counts.get("BLOCK_ATTEMPT", 0)),
            "total_blocked_events": int(counts.get("BLOCKED", 0)),
            "total_unblock_events": int(counts.get("UNBLOCKED", 0)),
            "unique_blocked_domains": int(unique_domains or 0),
            "trend": {row[0]: int(row[1]) for row in trend_rows if row[0] is not None},
        }
