"""monitoring.py — ShortsCap backend: FastAPI routes for the monitoring data layer.

Architecture: Router -> Pydantic Schema -> Service -> Repository ->
SQLAlchemy -> MySQL. Routers contain NO database queries and services
contain NO HTTP logic.

Endpoints:
  Usage sync   POST /monitoring/app-usage/sync   (one or a batch of records)
  Usage history GET /monitoring/app-usage
  Events       POST /monitoring/events, GET /monitoring/events
  Summary      GET /monitoring/summary

The backend is a DATA / SYNCHRONIZATION API only — no real-time monitoring
loop, no server-side app detection, no timers. Android remains the real-time
monitoring authority and syncs observed data here.

TEMPORARY DEVELOPMENT IDENTITY (NOT PRODUCTION AUTH):
AWS Cognito is implemented in a later phase. Until then the API reads the
development user ID from the `X-Dev-User-Id` header (see
`app/routers/deps.py` — the single Cognito replacement point). This is
DEVELOPMENT ONLY — it is not a security mechanism, grants no privileges,
and must be removed when real authentication lands.
"""

from datetime import date, datetime, time
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.routers.deps import ensure_dev_user, get_dev_user_id
from app.schemas.monitoring import (
    AppUsageRecord,
    AppUsageResponse,
    MonitoringEventCreate,
    MonitoringEventResponse,
    MonitoringEventType,
    MonitoringSummary,
)
from app.services.monitoring import (
    AppUsageService,
    MonitoringError,
    MonitoringEventService,
    MonitoringNotFoundError,
    MonitoringValidationError,
)

router = APIRouter(prefix="/monitoring", tags=["monitoring"])


def _raise_http(exc: MonitoringError) -> None:
    """Map monitoring domain errors to HTTP status codes: 404 for missing /
    cross-user records, 422 for domain-level validation problems, 400 for
    anything else. Internal details are never exposed."""
    if isinstance(exc, MonitoringNotFoundError):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)
        )
    if isinstance(exc, MonitoringValidationError):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)
        )
    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)
    )


# ---------------------------------------------------------------------------
# App usage sync + history
# ---------------------------------------------------------------------------


@router.post(
    "/app-usage/sync",
    response_model=list[AppUsageResponse],
    summary="Sync app usage summaries",
)
def sync_app_usage(
    payload: list[AppUsageRecord] | AppUsageRecord,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[AppUsageResponse]:
    """Persist one or more aggregated daily usage summaries for the current
    user. Each record must reference a device owned by the user. Syncing the
    same summary again OVERWRITES its values (idempotent — no duplicates).
    The user identity always comes from the development header, never from
    the payload."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    records = payload if isinstance(payload, list) else [payload]
    try:
        synced = AppUsageService(db).sync_usage(
            user_id, [r.model_dump() for r in records]
        )
    except MonitoringError as exc:
        _raise_http(exc)
    return [AppUsageResponse.model_validate(u) for u in synced]


@router.get(
    "/app-usage",
    response_model=list[AppUsageResponse],
    summary="List app usage history",
)
def list_app_usage(
    device_id: int | None = Query(default=None),
    package_name: str | None = Query(default=None),
    date_from: date | None = Query(default=None),
    date_to: date | None = Query(default=None),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=50, ge=1, le=100),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[AppUsageResponse]:
    """Return the current user's usage history (newest date first), filtered
    by device, package or usage-date range, with simple page/page_size
    pagination. Only the caller's own rows are returned."""
    try:
        usage = AppUsageService(db).list_usage(
            user_id,
            device_id=device_id,
            package_name=package_name,
            date_from=date_from,
            date_to=date_to,
            page=page,
            page_size=page_size,
        )
    except MonitoringError as exc:
        _raise_http(exc)
    return [AppUsageResponse.model_validate(u) for u in usage]


# ---------------------------------------------------------------------------
# Monitoring events
# ---------------------------------------------------------------------------


@router.post(
    "/events",
    response_model=MonitoringEventResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Submit a monitoring event",
)
def create_event(
    payload: MonitoringEventCreate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> MonitoringEventResponse:
    """Persist one monitoring event for the current user (event_type must be
    one of the supported types; device must belong to the user). `occurred_at`
    defaults to the server's current UTC time when omitted."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    try:
        event = MonitoringEventService(db).create_event(
            user_id, payload.model_dump()
        )
    except MonitoringError as exc:
        _raise_http(exc)
    return MonitoringEventResponse.model_validate(event)


@router.get(
    "/events",
    response_model=list[MonitoringEventResponse],
    summary="List monitoring events",
)
def list_events(
    event_type: MonitoringEventType | None = Query(default=None),
    device_id: int | None = Query(default=None),
    app_package: str | None = Query(default=None),
    start_date: date | None = Query(default=None),
    end_date: date | None = Query(default=None),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=50, ge=1, le=100),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[MonitoringEventResponse]:
    """Return the current user's monitoring events (newest first), filtered
    by event type, device, app package or an occurred-at date range, with
    page/page_size pagination. Only the caller's own events are returned."""
    start_dt = datetime.combine(start_date, time.min) if start_date else None
    end_dt = datetime.combine(end_date, time.max) if end_date else None
    try:
        events = MonitoringEventService(db).list_events(
            user_id,
            event_type=event_type,
            device_id=device_id,
            app_package=app_package,
            start_date=start_dt,
            end_date=end_dt,
            page=page,
            page_size=page_size,
        )
    except MonitoringError as exc:
        _raise_http(exc)
    return [MonitoringEventResponse.model_validate(e) for e in events]


# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------


@router.get(
    "/summary",
    response_model=MonitoringSummary,
    summary="Get a basic monitoring summary",
)
def get_summary(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> MonitoringSummary:
    """Read-only summary of the current user's stored monitoring data:
    total usage seconds, total launches, distinct monitored apps and event
    count. This is intentionally basic — weekly/monthly reports, Your Score,
    leaderboard and ranking are later phases."""
    usage_service = AppUsageService(db)
    event_service = MonitoringEventService(db)
    summary = usage_service.summary(user_id)
    summary["event_count"] = event_service.count_events(user_id)
    return MonitoringSummary.model_validate(summary)
