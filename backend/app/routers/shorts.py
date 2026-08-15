"""shorts.py — ShortsCap backend: FastAPI routes for the shorts data layer.

Architecture: Router -> Pydantic Schema -> Service -> Repository ->
SQLAlchemy -> MySQL. Routers contain NO database queries and services
contain NO HTTP logic.

Endpoints:
  Usage sync   POST /shorts/usage/sync     (one or a batch of records)
  Usage history GET /shorts/usage
  Events       POST /shorts/events, GET /shorts/events
  Summary      GET /shorts/summary

The backend is a DATA / SYNCHRONIZATION API only — no real-time Shorts
detection, no server-side counting loop, no device control. Android remains
responsible for real-time Shorts detection, counting, enforcement,
notifications and local buffering; the backend validates, stores and serves
the synchronized history for later Reports and Your Score/Rank.

Short settings (GET/PUT /settings/shorts) already exist in the Settings layer
(Phase 7) and are reused — this phase is Shorts DATA, not Shorts SETTINGS.

TEMPORARY DEVELOPMENT IDENTITY (NOT PRODUCTION AUTH):
AWS Cognito is implemented in a later phase. Until then the API reads the
development user ID from the `X-Dev-User-Id` header (see
`app/routers/deps.py` — the single Cognito replacement point). This is
DEVELOPMENT ONLY — it is not a security mechanism, grants no privileges,
and must be removed when real authentication lands.
"""

from datetime import date, datetime, time

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.routers.deps import ensure_dev_user, get_dev_user_id
from app.schemas.shorts import (
    ShortsEventCreate,
    ShortsEventResponse,
    ShortsEventType,
    ShortsSummary,
    ShortsUsageRecord,
    ShortsUsageResponse,
)
from app.services.shorts import (
    ShortsError,
    ShortsEventService,
    ShortsNotFoundError,
    ShortsUsageService,
    ShortsValidationError,
)

router = APIRouter(prefix="/shorts", tags=["shorts"])


def _raise_http(exc: ShortsError) -> None:
    """Map shorts domain errors to HTTP status codes: 404 for missing /
    cross-user records, 422 for domain-level validation problems, 400 for
    anything else. Internal details are never exposed."""
    if isinstance(exc, ShortsNotFoundError):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)
        )
    if isinstance(exc, ShortsValidationError):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)
        )
    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)
    )


# ---------------------------------------------------------------------------
# Shorts usage sync + history
# ---------------------------------------------------------------------------


@router.post(
    "/usage/sync",
    response_model=list[ShortsUsageResponse],
    summary="Sync Shorts usage summaries",
)
def sync_shorts_usage(
    payload: list[ShortsUsageRecord] | ShortsUsageRecord,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[ShortsUsageResponse]:
    """Persist one or more aggregated daily Shorts usage summaries for the
    current user. Each record must reference a device owned by the user.
    Syncing the same day's summary again OVERWRITES its values (idempotent —
    no duplicate rows). The user identity always comes from the development
    header, never from the payload."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    records = payload if isinstance(payload, list) else [payload]
    try:
        synced = ShortsUsageService(db).sync_usage(
            user_id, [r.model_dump() for r in records]
        )
    except ShortsError as exc:
        _raise_http(exc)
    return [ShortsUsageResponse.model_validate(u) for u in synced]


@router.get(
    "/usage",
    response_model=list[ShortsUsageResponse],
    summary="List Shorts usage history",
)
def list_shorts_usage(
    device_id: int | None = Query(default=None),
    date_from: date | None = Query(default=None),
    date_to: date | None = Query(default=None),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=50, ge=1, le=100),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[ShortsUsageResponse]:
    """Return the current user's Shorts usage history (newest date first),
    filtered by device or usage-date range, with page/page_size pagination.
    Only the caller's own rows are returned."""
    try:
        usage = ShortsUsageService(db).list_usage(
            user_id,
            device_id=device_id,
            date_from=date_from,
            date_to=date_to,
            page=page,
            page_size=page_size,
        )
    except ShortsError as exc:
        _raise_http(exc)
    return [ShortsUsageResponse.model_validate(u) for u in usage]


# ---------------------------------------------------------------------------
# Shorts events
# ---------------------------------------------------------------------------


@router.post(
    "/events",
    response_model=ShortsEventResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Submit a Shorts event",
)
def create_event(
    payload: ShortsEventCreate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ShortsEventResponse:
    """Persist one Shorts event for the current user (event_type must be one
    of the supported types; device must belong to the user). `occurred_at`
    defaults to the server's current UTC time when omitted."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    try:
        event = ShortsEventService(db).create_event(user_id, payload.model_dump())
    except ShortsError as exc:
        _raise_http(exc)
    return ShortsEventResponse.model_validate(event)


@router.get(
    "/events",
    response_model=list[ShortsEventResponse],
    summary="List Shorts events",
)
def list_events(
    event_type: ShortsEventType | None = Query(default=None),
    device_id: int | None = Query(default=None),
    start_date: date | None = Query(default=None),
    end_date: date | None = Query(default=None),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=50, ge=1, le=100),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[ShortsEventResponse]:
    """Return the current user's Shorts events (newest first), filtered by
    event type, device or an occurred-at date range, with page/page_size
    pagination. Only the caller's own events are returned."""
    start_dt = datetime.combine(start_date, time.min) if start_date else None
    end_dt = datetime.combine(end_date, time.max) if end_date else None
    try:
        events = ShortsEventService(db).list_events(
            user_id,
            event_type=event_type,
            device_id=device_id,
            start_date=start_dt,
            end_date=end_dt,
            page=page,
            page_size=page_size,
        )
    except ShortsError as exc:
        _raise_http(exc)
    return [ShortsEventResponse.model_validate(e) for e in events]


# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------


@router.get(
    "/summary",
    response_model=ShortsSummary,
    summary="Get a basic Shorts summary",
)
def get_summary(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ShortsSummary:
    """Read-only summary of the current user's stored Shorts data: total
    count / duration, per-day averages and warning / limit counts. This is
    intentionally basic — weekly/monthly reports, Your Score, Rank and
    leaderboard are later phases."""
    summary = ShortsUsageService(db).summary(user_id)
    return ShortsSummary.model_validate(summary)
