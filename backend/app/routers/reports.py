"""reports.py — ShortsCap backend: FastAPI routes for the reporting layer.

Architecture: Router -> Reporting Schema -> Reporting Service -> Reporting
Repository -> SQLAlchemy -> MySQL. Routers contain NO database queries and
no aggregation SQL; the read-only ReportingRepository runs the SQL
aggregations.

Endpoints:
  GET /reports/daily?date=&include_comparison=
  GET /reports/weekly?date=&include_comparison=
  GET /reports/monthly?date=&include_comparison=

Reports are read-only aggregations over the EXISTING historical tables
(study_sessions / break_sessions, app_usage / monitoring_events,
shorts_usage, website_events). No report tables are created, no schema is
changed, and the raw data remains the source of truth. `date` is interpreted
as a UTC calendar date (the backend's documented naive-UTC convention);
when omitted it defaults to the server's current UTC date. `include_comparison`
(default true) adds the previous equivalent period with percentage changes
(None when the previous value is zero).

Deliberately NOT implemented here: Your Score, Rank, leaderboard, scoring
formulas — a later Score Engine consumes this data or the raw rows.

TEMPORARY DEVELOPMENT IDENTITY (NOT PRODUCTION AUTH):
AWS Cognito is implemented in a later phase. Until then the API reads the
development user ID from the `X-Dev-User-Id` header (see
`app/routers/deps.py` — the single Cognito replacement point). Reports are
computed ONLY for the current user — a client-supplied user_id is never
accepted.
"""

from datetime import date

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.database import get_db
from app.routers.deps import ensure_dev_user, get_dev_user_id
from app.schemas.reports import ReportResponse
from app.services.reporting import ReportingService
from app.utils.datetime import utcnow

router = APIRouter(prefix="/reports", tags=["reports"])


@router.get(
    "/daily",
    response_model=ReportResponse,
    summary="Daily report",
)
def daily_report(
    date: date = Query(default=None, description="UTC calendar date (default: today)"),
    include_comparison: bool = Query(default=True),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ReportResponse:
    """Factual daily summary of the current user's study / monitoring /
    shorts / web activity for the given UTC date, with optional previous-day
    comparison."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    report_date = date if date is not None else utcnow().date()
    return ReportResponse.model_validate(
        ReportingService(db).daily(user_id, report_date, include_comparison)
    )


@router.get(
    "/weekly",
    response_model=ReportResponse,
    summary="Weekly report",
)
def weekly_report(
    date: date = Query(default=None, description="Any date in the UTC week (default: today)"),
    include_comparison: bool = Query(default=True),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ReportResponse:
    """Factual weekly summary (ISO week, Monday–Sunday) of the current
    user's study / monitoring / shorts / web activity, including a 7-day
    daily trend and optional previous-week comparison."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    report_date = date if date is not None else utcnow().date()
    return ReportResponse.model_validate(
        ReportingService(db).weekly(user_id, report_date, include_comparison)
    )


@router.get(
    "/monthly",
    response_model=ReportResponse,
    summary="Monthly report",
)
def monthly_report(
    date: date = Query(default=None, description="Any date in the UTC month (default: today)"),
    include_comparison: bool = Query(default=True),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ReportResponse:
    """Factual monthly summary (calendar month) of the current user's study
    / monitoring / shorts / web activity, including a daily trend and
    optional previous-month comparison."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    report_date = date if date is not None else utcnow().date()
    return ReportResponse.model_validate(
        ReportingService(db).monthly(user_id, report_date, include_comparison)
    )
