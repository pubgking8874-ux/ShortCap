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
        return self.active_days_by_user([user_id], start, end).get(user_id, 0)

    # ------------------------------------------------------------------
    # Grouped by-user variants (used by the batch scoring layer so the
    # leaderboard never runs one query per user — no N+1, Phase 15B).
    # ------------------------------------------------------------------

    def study_aggregates_by_user(
        self, user_ids: list[int], start: date, end: date
    ) -> dict[int, dict]:
        """Meaningful study aggregates per user (terminal + >= 300 s), for
        sessions ENDED in the range."""
        start_dt, end_dt = _dt_range(start, end)
        rows = (
            self.db.query(
                StudySession.user_id,
                func.coalesce(
                    func.sum(case((StudySession.status == "completed", 1), else_=0)), 0
                ),
                func.count(StudySession.id),
                func.coalesce(func.sum(StudySession.actual_duration_seconds), 0),
            )
            .filter(
                StudySession.user_id.in_(user_ids),
                StudySession.status.in_(_TERMINAL),
                StudySession.actual_duration_seconds >= MIN_MEANINGFUL_SESSION_SEC,
                StudySession.ended_at >= start_dt,
                StudySession.ended_at <= end_dt,
            )
            .group_by(StudySession.user_id)
            .all()
        )
        return {
            uid: {"completed": int(c or 0), "total": int(t or 0), "total_seconds": int(s or 0)}
            for uid, c, t, s in rows
        }

    def shorts_days_by_user(
        self, user_ids: list[int], start: date, end: date
    ) -> dict[int, list[dict]]:
        """Per-day Shorts usage per user, aggregated by usage_date."""
        rows = (
            self.db.query(
                ShortsUsage.user_id,
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
                ShortsUsage.user_id.in_(user_ids),
                ShortsUsage.usage_date >= start,
                ShortsUsage.usage_date <= end,
            )
            .group_by(ShortsUsage.user_id, ShortsUsage.usage_date)
            .all()
        )
        result: dict[int, list[dict]] = {}
        for uid, _, seconds, warning, limit in rows:
            result.setdefault(uid, []).append(
                {
                    "minutes": round((seconds or 0) / 60, 4),
                    "warning_triggered": bool(warning),
                    "limit_reached": bool(limit),
                }
            )
        return result

    def app_days_by_user(
        self, user_ids: list[int], start: date, end: date
    ) -> dict[int, list[dict]]:
        """Per-day total phone usage in minutes per user (by usage_date)."""
        rows = (
            self.db.query(
                AppUsage.user_id,
                AppUsage.usage_date,
                func.coalesce(func.sum(AppUsage.duration_seconds), 0),
            )
            .filter(
                AppUsage.user_id.in_(user_ids),
                AppUsage.usage_date >= start,
                AppUsage.usage_date <= end,
            )
            .group_by(AppUsage.user_id, AppUsage.usage_date)
            .all()
        )
        result: dict[int, list[dict]] = {}
        for uid, _, seconds in rows:
            result.setdefault(uid, []).append(
                {"minutes": round((seconds or 0) / 60, 4)}
            )
        return result

    def enforcement_events_by_user(
        self, user_ids: list[int], start: date, end: date
    ) -> dict[int, int]:
        """Count of LIMIT_REACHED / APP_RESTRICTED monitoring events per user."""
        start_dt, end_dt = _dt_range(start, end)
        rows = (
            self.db.query(
                MonitoringEvent.user_id,
                func.count(MonitoringEvent.id),
            )
            .filter(
                MonitoringEvent.user_id.in_(user_ids),
                MonitoringEvent.event_type.in_(["LIMIT_REACHED", "APP_RESTRICTED"]),
                MonitoringEvent.occurred_at >= start_dt,
                MonitoringEvent.occurred_at <= end_dt,
            )
            .group_by(MonitoringEvent.user_id)
            .all()
        )
        return {uid: int(n or 0) for uid, n in rows}

    def blocked_active_by_user(self, user_ids: list[int]) -> dict[int, int]:
        """Number of currently-active blocked websites per user."""
        rows = (
            self.db.query(
                BlockedWebsite.user_id,
                func.count(BlockedWebsite.id),
            )
            .filter(
                BlockedWebsite.user_id.in_(user_ids),
                BlockedWebsite.is_blocked.is_(True),
            )
            .group_by(BlockedWebsite.user_id)
            .all()
        )
        return {uid: int(n or 0) for uid, n in rows}

    def web_events_by_user(
        self, user_ids: list[int], start: date, end: date
    ) -> dict[int, list[dict]]:
        """Website events per user in the range (type + domain)."""
        start_dt, end_dt = _dt_range(start, end)
        rows = (
            self.db.query(WebsiteEvent.event_type, WebsiteEvent.domain, WebsiteEvent.user_id)
            .filter(
                WebsiteEvent.user_id.in_(user_ids),
                WebsiteEvent.occurred_at >= start_dt,
                WebsiteEvent.occurred_at <= end_dt,
            )
            .all()
        )
        result: dict[int, list[dict]] = {}
        for event_type, domain, uid in rows:
            result.setdefault(uid, []).append({"type": event_type, "domain": domain})
        return result

    def shorts_limits_by_user(
        self, user_ids: list[int]
    ) -> dict[int, int | None]:
        """Configured daily Shorts limit (minutes) per user; None when unset."""
        rows = (
            self.db.query(ShortsSettings.user_id, ShortsSettings.daily_limit_minutes)
            .filter(ShortsSettings.user_id.in_(user_ids))
            .all()
        )
        return {uid: limit for uid, limit in rows}

    def active_days_by_user(
        self, user_ids: list[int], start: date, end: date
    ) -> dict[int, int]:
        """Distinct calendar days with ANY recorded activity per user. Days
        count once per user regardless of row count."""
        start_dt, end_dt = _dt_range(start, end)
        per_user: dict[int, set[date]] = {}

        queries = [
            (
                self.db.query(StudySession.user_id, func.date(StudySession.ended_at))
                .filter(
                    StudySession.user_id.in_(user_ids),
                    StudySession.ended_at >= start_dt,
                    StudySession.ended_at <= end_dt,
                )
                .distinct()
            ),
            (
                self.db.query(AppUsage.user_id, AppUsage.usage_date)
                .filter(
                    AppUsage.user_id.in_(user_ids),
                    AppUsage.usage_date >= start,
                    AppUsage.usage_date <= end,
                )
                .distinct()
            ),
            (
                self.db.query(ShortsUsage.user_id, ShortsUsage.usage_date)
                .filter(
                    ShortsUsage.user_id.in_(user_ids),
                    ShortsUsage.usage_date >= start,
                    ShortsUsage.usage_date <= end,
                )
                .distinct()
            ),
            (
                self.db.query(WebsiteEvent.user_id, func.date(WebsiteEvent.occurred_at))
                .filter(
                    WebsiteEvent.user_id.in_(user_ids),
                    WebsiteEvent.occurred_at >= start_dt,
                    WebsiteEvent.occurred_at <= end_dt,
                )
                .distinct()
            ),
            (
                self.db.query(MonitoringEvent.user_id, func.date(MonitoringEvent.occurred_at))
                .filter(
                    MonitoringEvent.user_id.in_(user_ids),
                    MonitoringEvent.occurred_at >= start_dt,
                    MonitoringEvent.occurred_at <= end_dt,
                )
                .distinct()
            ),
        ]
        for query in queries:
            for uid, day in query.all():
                if uid is not None and day is not None:
                    per_user.setdefault(uid, set()).add(day)
        return {uid: len(days) for uid, days in per_user.items()}
