"""study.py — ShortsCap backend: FastAPI routes for the study data layer.

Architecture: Router -> Pydantic Schema -> Service -> Repository ->
SQLAlchemy -> MySQL. Routers contain NO database queries and services
contain NO HTTP logic.

Endpoints:
  Schedules  POST/GET /study/schedules, GET/PUT/DELETE /study/schedules/{id}
  Sessions   POST /study/sessions/start, POST /study/sessions/{id}/end,
             POST /study/sessions/{id}/cancel, GET /study/sessions,
             GET /study/sessions/{id}
  Breaks     POST /study/sessions/{id}/breaks/start,
             POST /study/breaks/{break_id}/end
  Events     GET /study/events

The backend is NOT a real-time timer: it only persists study state and
history. Android remains responsible for real-time timing and the UI.

TEMPORARY DEVELOPMENT IDENTITY (NOT PRODUCTION AUTH):
AWS Cognito is implemented in a later phase. Until then the API reads the
development user ID from the `X-Dev-User-Id` header (see
`app/routers/deps.py` — the single Cognito replacement point). This is
DEVELOPMENT ONLY — it is not a security mechanism, grants no privileges,
and must be removed when real authentication lands.
"""

from datetime import datetime
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.routers.deps import ensure_dev_user, get_dev_user_id
from app.schemas.study import (
    BreakSessionResponse,
    SessionStatus,
    StudyEventResponse,
    StudyEventType,
    StudyScheduleCreate,
    StudyScheduleResponse,
    StudyScheduleUpdate,
    StudySessionEnd,
    StudySessionResponse,
    StudySessionStart,
)
from app.services.study import (
    BreakSessionService,
    StudyError,
    StudyEventService,
    StudyNotFoundError,
    StudyScheduleService,
    StudySessionService,
    StudyStateError,
)

router = APIRouter(prefix="/study", tags=["study"])


def _raise_http(exc: StudyError) -> None:
    """Map study domain errors to HTTP status codes:
      404 for missing / cross-user records, 400 for invalid state
      transitions. Internal details are never exposed."""
    if isinstance(exc, StudyNotFoundError):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)
        )
    if isinstance(exc, StudyStateError):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)
        )
    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)
    )


# ---------------------------------------------------------------------------
# Study schedules
# ---------------------------------------------------------------------------


@router.post(
    "/schedules",
    response_model=StudyScheduleResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Create a study schedule",
)
def create_schedule(
    payload: StudyScheduleCreate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> StudyScheduleResponse:
    """Create a schedule for the current user (title required; positive
    duration; non-negative reminder)."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    try:
        schedule = StudyScheduleService(db).create_schedule(
            user_id, payload.model_dump()
        )
    except StudyError as exc:
        _raise_http(exc)
    return StudyScheduleResponse.model_validate(schedule)


@router.get(
    "/schedules",
    response_model=list[StudyScheduleResponse],
    summary="List study schedules",
)
def list_schedules(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[StudyScheduleResponse]:
    """Return all of the current user's schedules."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    schedules = StudyScheduleService(db).list_schedules(user_id)
    return [StudyScheduleResponse.model_validate(s) for s in schedules]


@router.get(
    "/schedules/{schedule_id}",
    response_model=StudyScheduleResponse,
    summary="Get one study schedule",
)
def get_schedule(
    schedule_id: int,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> StudyScheduleResponse:
    """Return one of the current user's schedules (404 for other users')."""
    try:
        schedule = StudyScheduleService(db).get_schedule(user_id, schedule_id)
    except StudyError as exc:
        _raise_http(exc)
    return StudyScheduleResponse.model_validate(schedule)


@router.put(
    "/schedules/{schedule_id}",
    response_model=StudyScheduleResponse,
    summary="Update a study schedule",
)
def update_schedule(
    schedule_id: int,
    payload: StudyScheduleUpdate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> StudyScheduleResponse:
    """Partial update of one of the current user's schedules; only supplied
    fields change. 404 for schedules that don't exist or belong to another
    user."""
    try:
        schedule = StudyScheduleService(db).update_schedule(
            user_id, schedule_id, payload.model_dump(exclude_unset=True)
        )
    except StudyError as exc:
        _raise_http(exc)
    return StudyScheduleResponse.model_validate(schedule)


@router.delete(
    "/schedules/{schedule_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Delete a study schedule",
)
def delete_schedule(
    schedule_id: int,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> None:
    """Delete one of the current user's schedules (404 for other users')."""
    try:
        StudyScheduleService(db).delete_schedule(user_id, schedule_id)
    except StudyError as exc:
        _raise_http(exc)


# ---------------------------------------------------------------------------
# Study sessions
# ---------------------------------------------------------------------------


@router.post(
    "/sessions/start",
    response_model=StudySessionResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Start a study session",
)
def start_session(
    payload: StudySessionStart,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> StudySessionResponse:
    """Start a study session for the current user (status active). Optional
    schedule_id / device_id must belong to the user. The backend only
    persists state — it does not run a real-time timer."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    try:
        session = StudySessionService(db).start_session(
            user_id, payload.model_dump(exclude_unset=True)
        )
    except StudyError as exc:
        _raise_http(exc)
    return StudySessionResponse.model_validate(session)


@router.post(
    "/sessions/{session_id}/end",
    response_model=StudySessionResponse,
    summary="End a study session",
)
def end_session(
    session_id: int,
    payload: StudySessionEnd | None = None,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> StudySessionResponse:
    """End the current user's ACTIVE session (status completed, STUDY_ENDED).
    Pass `{"cancelled": true}` to explicitly represent cancellation
    (status cancelled, STUDY_CANCELLED). Completed/cancelled sessions cannot
    be ended again."""
    if payload is None:
        payload = StudySessionEnd()
    try:
        session = StudySessionService(db).end_session(
            user_id, session_id, cancelled=payload.cancelled
        )
    except StudyError as exc:
        _raise_http(exc)
    return StudySessionResponse.model_validate(session)


@router.post(
    "/sessions/{session_id}/cancel",
    response_model=StudySessionResponse,
    summary="Cancel a study session",
)
def cancel_session(
    session_id: int,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> StudySessionResponse:
    """Cancel the current user's ACTIVE session (status cancelled,
    STUDY_CANCELLED). Completed sessions cannot be cancelled."""
    try:
        session = StudySessionService(db).cancel_session(user_id, session_id)
    except StudyError as exc:
        _raise_http(exc)
    return StudySessionResponse.model_validate(session)


@router.get(
    "/sessions",
    response_model=list[StudySessionResponse],
    summary="List study session history",
)
def list_sessions(
    status: SessionStatus | None = Query(default=None),
    schedule_id: int | None = Query(default=None),
    date_from: datetime | None = Query(default=None),
    date_to: datetime | None = Query(default=None),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[StudySessionResponse]:
    """Return the current user's session history (newest first), optionally
    filtered by status, schedule_id or a started_at date range."""
    sessions = StudySessionService(db).list_sessions(
        user_id,
        status=status,
        schedule_id=schedule_id,
        date_from=date_from,
        date_to=date_to,
    )
    return [StudySessionResponse.model_validate(s) for s in sessions]


@router.get(
    "/sessions/{session_id}",
    response_model=StudySessionResponse,
    summary="Get one study session",
)
def get_session(
    session_id: int,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> StudySessionResponse:
    """Return one of the current user's sessions (404 for other users')."""
    try:
        session = StudySessionService(db).get_session(user_id, session_id)
    except StudyError as exc:
        _raise_http(exc)
    return StudySessionResponse.model_validate(session)


# ---------------------------------------------------------------------------
# Break sessions
# ---------------------------------------------------------------------------


@router.post(
    "/sessions/{session_id}/breaks/start",
    response_model=BreakSessionResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Start a break inside a study session",
)
def start_break(
    session_id: int,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> BreakSessionResponse:
    """Start a break inside the current user's ACTIVE study session. Rejects
    breaks on completed sessions and overlapping active breaks."""
    try:
        break_session = BreakSessionService(db).start_break(user_id, session_id)
    except StudyError as exc:
        _raise_http(exc)
    return BreakSessionResponse.model_validate(break_session)


@router.post(
    "/breaks/{break_id}/end",
    response_model=BreakSessionResponse,
    summary="End a break session",
)
def end_break(
    break_id: int,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> BreakSessionResponse:
    """End the current user's ACTIVE break (status completed, BREAK_ENDED).
    Already-completed breaks cannot be ended twice."""
    try:
        break_session = BreakSessionService(db).end_break(user_id, break_id)
    except StudyError as exc:
        _raise_http(exc)
    return BreakSessionResponse.model_validate(break_session)


# ---------------------------------------------------------------------------
# Study events
# ---------------------------------------------------------------------------


@router.get(
    "/events",
    response_model=list[StudyEventResponse],
    summary="List study event history",
)
def list_events(
    event_type: StudyEventType | None = Query(default=None),
    session_id: int | None = Query(default=None),
    date_from: datetime | None = Query(default=None),
    date_to: datetime | None = Query(default=None),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[StudyEventResponse]:
    """Return the current user's study events (newest first), optionally
    filtered by event_type, session or an event_time date range."""
    events = StudyEventService(db).list_events(
        user_id,
        event_type=event_type,
        study_session_id=session_id,
        date_from=date_from,
        date_to=date_to,
    )
    return [StudyEventResponse.model_validate(e) for e in events]
