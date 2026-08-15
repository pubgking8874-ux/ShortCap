"""reports.py — ShortsCap backend: Pydantic schemas for the reporting layer.

Response models for the read-only Reports API (`GET /reports/daily`,
`/reports/weekly`, `/reports/monthly`). Responses are structured by domain
(study / monitoring / shorts / web) plus period info, an optional daily
trend and an optional previous-period comparison. Reports only contain
factual metrics computed from existing historical data — no Your Score,
Rank or leaderboard values.
"""

from datetime import date

from pydantic import BaseModel


class PeriodInfo(BaseModel):
    """Which period a report covers (`daily` | `weekly` | `monthly`)."""

    type: str
    start_date: date
    end_date: date
    label: str


class StudyMetrics(BaseModel):
    """Study activity in the period, from server-stored timestamps.

    `total_study_seconds` is the sum of authoritative `actual_duration_seconds`
    (= ended_at - started_at) over sessions that ended in the period with a
    terminal status. Breaks are counted through the owning study session.
    """

    total_study_seconds: int
    completed_sessions: int
    cancelled_sessions: int
    break_seconds: int
    completed_breaks: int


class PlatformBreakdown(BaseModel):
    """Shorts usage for ONE platform (aggregated across its surfaces)."""

    platform: str
    shorts_count: int
    duration_seconds: int


class TopApp(BaseModel):
    """One app's aggregated usage in the period (duration-based ranking)."""

    app_name: str
    duration_seconds: int


class MonitoringMetrics(BaseModel):
    """App-usage + monitoring-event activity in the period."""

    total_app_usage_seconds: int
    monitored_apps_count: int
    monitoring_event_count: int
    top_apps: list[TopApp]


class ShortsMetrics(BaseModel):
    """Shorts usage in the period, from the aggregated daily summaries."""

    total_shorts_count: int
    total_duration_seconds: int
    warning_count: int
    limit_reached_count: int
    platform_breakdown: list[PlatformBreakdown]


class WebMetrics(BaseModel):
    """Website blocking activity in the period."""

    total_block_attempts: int
    total_blocked_events: int
    total_unblock_events: int
    unique_blocked_domains: int


class DailyTrendEntry(BaseModel):
    """One calendar day inside a weekly/monthly report. Days with no data
    are zero values — never fabricated observations."""

    date: date
    study_seconds: int
    shorts_seconds: int
    app_usage_seconds: int
    block_attempts: int


class ComparisonMetric(BaseModel):
    """Current vs previous equivalent period for one metric.

    `change_percent` is None when the previous value is zero (an explicit
    not-applicable state — never a fabricated division).
    """

    current: float
    previous: float
    change_percent: float | None = None


class Comparison(BaseModel):
    """Previous-period comparison for the four headline metrics."""

    study_seconds: ComparisonMetric
    shorts_seconds: ComparisonMetric
    app_usage_seconds: ComparisonMetric
    block_attempts: ComparisonMetric


class ReportResponse(BaseModel):
    """One report. `daily_trend` is present for weekly/monthly reports;
    `comparison` is present when requested (default true)."""

    period: PeriodInfo
    study: StudyMetrics
    monitoring: MonitoringMetrics
    shorts: ShortsMetrics
    web: WebMetrics
    daily_trend: list[DailyTrendEntry] | None = None
    comparison: Comparison | None = None
