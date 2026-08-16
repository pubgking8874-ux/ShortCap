"""shorts.py — ShortsCap backend: FastAPI routes for the shorts data layer.

Architecture: Router -> Pydantic Schema -> Service -> Repository ->
SQLAlchemy -> MySQL. Routers contain NO database queries and services
contain NO HTTP logic.

Endpoints:
  Usage sync    POST /shorts/usage/sync     (one or a batch of records)
  Usage history GET /shorts/usage
  Events        POST /shorts/events, GET /shorts/events
  Summary       GET /shorts/summary
  Control       GET /shorts/control, PUT /shorts/control (combined state)
  Limit cycle   GET /shorts/limit-cycle
                POST /shorts/limit-cycle/activate, POST /shorts/limit-cycle/disable

The backend is a DATA / SYNCHRONIZATION API only — no real-time Shorts
detection, no server-side counting loop, no device control. Android remains
responsible for real-time Shorts detection, counting, enforcement,
notifications and local buffering; the backend validates, stores and serves
the synchronized history for later Reports and Your Score/Rank.

Short settings (GET/PUT /settings/shorts) already exist in the Settings layer
(Phase 7) and are reused — this phase adds the Shorts Control domain: the
durable 24-hour limit cycle (`shorts_limit_cycles`), the persisted HUD
appearance preference (on `shorts_settings`) and read-only insights. The
cycle count is RECONCILED from synchronized usage after every usage sync —
the backend never trusts a client-supplied cycle count.

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
from app.models.shorts_limit_cycle import ShortsLimitCycle
from app.schemas.shorts import (
    ShortControlResponse,
    ShortsControlUpdate,
    ShortsEventCreate,
    ShortsEventResponse,
    ShortsEventType,
    ShortsLimitCycleActivate,
    ShortsLimitCycleResponse,
    ShortsSummary,
    ShortsUsageRecord,
    ShortsUsageResponse,
)
from app.services.settings import ShortsSettingsService
from app.services.shorts import (
    ShortsControlService,
    ShortsError,
    ShortsEventService,
    ShortsLimitCycleService,
    ShortsNotFoundError,
    ShortsUsageService,
    ShortsValidationError,
    reconcile_cycle_after_usage_sync,
)
from app.utils.datetime import utcnow

router = APIRouter(prefix="/shorts", tags=["shorts"])


def _cycle_response(cycle: ShortsLimitCycle) -> ShortsLimitCycleResponse:
    """Build the limit-cycle response with the spec-required derived fields
    (`remaining_seconds` from timestamps, `usage_ratio` from count/limit).
    Both are computed at response time — never persisted as decreasing
    values."""
    now = utcnow()
    data = ShortsLimitCycleResponse.model_validate(cycle).model_dump()
    data["remaining_seconds"] = max(0, int((cycle.cycle_expires_at - now).total_seconds()))
    data["usage_ratio"] = (
        round(cycle.current_count / cycle.limit_count, 3)
        if cycle.limit_count > 0
        else 0.0
    )
    return ShortsLimitCycleResponse(**data)


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
        # Shorts Control: reconcile the active 24-hour cycle's count from the
        # synchronized usage (idempotent — re-syncs can never double-count).
        reconcile_cycle_after_usage_sync(db, user_id)
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


# ---------------------------------------------------------------------------
# Shorts Control (combined state) + 24-hour limit cycle
# ---------------------------------------------------------------------------


@router.get(
    "/control",
    response_model=ShortControlResponse,
    summary="Get combined Shorts Control state",
)
def get_shorts_control(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ShortControlResponse:
    """The combined Shorts Control state for the current user: the canonical
    Short Applications catalog, the current 24-hour limit cycle (or null),
    the HUD appearance preference and read-only Yesterday / Today / This
    Week / This Month insights. Only the caller's own data is returned."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    return ShortControlResponse.model_validate(
        ShortsControlService(db).control(user_id)
    )


@router.put(
    "/control",
    response_model=ShortControlResponse,
    summary="Update Shorts Control settings",
)
def update_shorts_control(
    payload: ShortsControlUpdate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ShortControlResponse:
    """Partial update of the persisted Shorts Control settings (limit,
    warning thresholds, enable/disable, strict mode, HUD appearance).
    Changing the limit updates ONLY the active cycle's threshold — the
    current count and the 24-hour timer are preserved. Returns the refreshed
    control state."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    return ShortControlResponse.model_validate(
        ShortsControlService(db).update_control(
            user_id, payload.model_dump(exclude_unset=True)
        )
    )


@router.get(
    "/limit-cycle",
    response_model=ShortsLimitCycleResponse,
    summary="Get the current 24-hour Shorts limit cycle",
)
def get_limit_cycle(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ShortsLimitCycleResponse:
    """Return the user's current 24-hour limit cycle (ACTIVE or
    LIMIT_REACHED). Expired cycles are marked EXPIRED inline and then treated
    as absent. 404 when no cycle is active yet — the control endpoint's
    `limit_cycle` block is null in that case."""
    cycle = ShortsLimitCycleService(db).get_active(user_id)
    if cycle is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No active Shorts limit cycle.",
        )
    return _cycle_response(cycle)


@router.post(
    "/limit-cycle/activate",
    response_model=ShortsLimitCycleResponse,
    summary="Activate a 24-hour Shorts limit cycle",
)
def activate_limit_cycle(
    payload: ShortsLimitCycleActivate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ShortsLimitCycleResponse:
    """Activate a 24-hour cycle with the given limit count. If an active
    cycle already exists it is returned UNCHANGED (never a second cycle).
    The cycle persists immediately; the count starts at zero and the window
    is [now, now + 24h]. The limit is also persisted to the user's Shorts
    settings (daily_limit_count)."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    try:
        # Persist the configured limit to the user's Shorts settings (the
        # threshold a future cycle starts from); does not touch any cycle.
        ShortsSettingsService(db).update_settings(
            user_id, {"daily_limit_count": payload.limit_count}
        )
        cycle = ShortsLimitCycleService(db).activate(
            user_id, payload.limit_count, device_id=payload.device_id
        )
    except ShortsError as exc:
        _raise_http(exc)
    return _cycle_response(cycle)


@router.post(
    "/limit-cycle/disable",
    response_model=ShortsLimitCycleResponse,
    summary="Disable the current Shorts limit cycle",
)
def disable_limit_cycle(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ShortsLimitCycleResponse:
    """Disable Shorts control: the active cycle becomes DISABLED (historical;
    usage/events are never deleted). 404 when no active cycle exists."""
    cycle = ShortsLimitCycleService(db).disable(user_id)
    if cycle is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No active Shorts limit cycle.",
        )
    return _cycle_response(cycle)


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
