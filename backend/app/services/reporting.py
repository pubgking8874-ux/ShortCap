"""reporting.py — ShortsCap backend: reporting service.

ReportingService — read-only orchestration for the Reports API:

  * period math (daily / ISO-week / calendar-month, plus the previous
    equivalent period)
  * aggregation coordination through the ReportingRepository (SQL)
  * previous-period comparison with an explicit zero-guard — when the
    previous value is 0 the change is None ("not applicable"), never a
    fabricated percentage
  * daily-trend assembly for weekly / monthly reports (missing days are
    zero, never invented observations)
  * response assembly structured by domain (study / monitoring / shorts /
    web)

All values come from existing historical data; no Score, Rank or leaderboard
is computed here (a later Score Engine consumes this data or the raw rows).
"""

from datetime import date, datetime, timedelta

from sqlalchemy.orm import Session

from app.repositories.reports import ReportingRepository
from app.utils.datetime import utcnow


def _pct(current: float, previous: float) -> float | None:
    """Percentage change, or None when the previous value is zero."""
    if previous == 0:
        return None
    return round((current - previous) / previous * 100, 1)


class ReportingService:
    """Builds daily / weekly / monthly reports for the current user."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.repository = ReportingRepository(db)

    # ------------------------------------------------------------------
    # Period helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _daily_period(report_date: date) -> dict:
        return {
            "type": "daily",
            "start_date": report_date,
            "end_date": report_date,
            "label": f"{report_date:%Y-%m-%d}",
        }

    @staticmethod
    def _weekly_period(report_date: date) -> dict:
        start = report_date - timedelta(days=report_date.isoweekday() - 1)  # Monday
        end = start + timedelta(days=6)  # Sunday
        return {
            "type": "weekly",
            "start_date": start,
            "end_date": end,
            "label": f"{start:%Y-%m-%d} – {end:%Y-%m-%d}",
        }

    @staticmethod
    def _monthly_period(report_date: date) -> dict:
        start = report_date.replace(day=1)
        next_month = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
        end = next_month - timedelta(days=1)
        return {
            "type": "monthly",
            "start_date": start,
            "end_date": end,
            "label": f"{report_date:%Y-%m}",
        }

    def _shift_previous(self, period: dict) -> dict:
        """Shift a period back by one equivalent period."""
        start = period["start_date"]
        end = period["end_date"]
        span = (end - start).days + 1
        return {
            "start_date": start - timedelta(days=span),
            "end_date": end - timedelta(days=span),
        }

    # ------------------------------------------------------------------
    # Aggregation + assembly
    # ------------------------------------------------------------------

    def _collect(self, user_id: int, start: date, end: date) -> dict:
        """Run all four domain aggregations for one date range."""
        return {
            "study": self.repository.study_aggregates(user_id, start, end),
            "monitoring": self.repository.monitoring_aggregates(user_id, start, end),
            "shorts": self.repository.shorts_aggregates(user_id, start, end),
            "web": self.repository.web_aggregates(user_id, start, end),
        }

    @staticmethod
    def _comparison(cur: dict, prev: dict) -> dict:
        """Previous-period comparison for the four headline metrics."""
        pairs = [
            ("study_seconds", "study", "total_study_seconds"),
            ("shorts_seconds", "shorts", "total_duration_seconds"),
            ("app_usage_seconds", "monitoring", "total_app_usage_seconds"),
            ("block_attempts", "web", "total_block_attempts"),
        ]
        result: dict = {}
        for key, domain, field in pairs:
            current = float(cur[domain][field])
            previous = float(prev[domain][field])
            result[key] = {
                "current": current,
                "previous": previous,
                "change_percent": _pct(current, previous),
            }
        return result

    @staticmethod
    def _trend(period: dict, collected: dict) -> list[dict]:
        """Per-day trend rows for every day in the period. Days without data
        are zero values — missing observations are never invented."""
        study_trend = collected["study"]["trend"]
        shorts_trend = collected["shorts"]["trend"]
        app_trend = collected["monitoring"]["trend"]
        web_trend = collected["web"]["trend"]
        days = []
        cursor = period["start_date"]
        while cursor <= period["end_date"]:
            days.append(
                {
                    "date": cursor,
                    "study_seconds": study_trend.get(cursor, 0),
                    "shorts_seconds": shorts_trend.get(cursor, 0),
                    "app_usage_seconds": app_trend.get(cursor, 0),
                    "block_attempts": web_trend.get(cursor, 0),
                }
            )
            cursor += timedelta(days=1)
        return days

    def _report(
        self,
        user_id: int,
        report_date: date,
        period: dict,
        include_comparison: bool,
        include_trend: bool,
    ) -> dict:
        collected = self._collect(user_id, period["start_date"], period["end_date"])
        response: dict = {
            "period": period,
            "study": collected["study"],
            "monitoring": collected["monitoring"],
            "shorts": collected["shorts"],
            "web": collected["web"],
        }
        if include_trend:
            response["daily_trend"] = self._trend(period, collected)
        if include_comparison:
            prev = self._shift_previous(period)
            previous = self._collect(user_id, prev["start_date"], prev["end_date"])
            response["comparison"] = self._comparison(collected, previous)
        return response

    # ------------------------------------------------------------------
    # Public entry points
    # ------------------------------------------------------------------

    def daily(self, user_id: int, report_date: date | None = None, include_comparison: bool = True) -> dict:
        if report_date is None:
            report_date = utcnow().date()
        return self._report(
            user_id, report_date, self._daily_period(report_date), include_comparison, include_trend=False
        )

    def weekly(self, user_id: int, report_date: date | None = None, include_comparison: bool = True) -> dict:
        if report_date is None:
            report_date = utcnow().date()
        return self._report(
            user_id, report_date, self._weekly_period(report_date), include_comparison, include_trend=True
        )

    def monthly(self, user_id: int, report_date: date | None = None, include_comparison: bool = True) -> dict:
        if report_date is None:
            report_date = utcnow().date()
        return self._report(
            user_id, report_date, self._monthly_period(report_date), include_comparison, include_trend=True
        )
