"""web.py — ShortsCap backend: FastAPI routes for the web data layer.

Architecture: Router -> Pydantic Schema -> Service -> Repository ->
SQLAlchemy -> MySQL. Routers contain NO database queries and services
contain NO HTTP logic.

Endpoints:
  Blocked websites  POST /websites/blocked            (create)
                    GET  /websites/blocked             (list)
                    GET  /websites/blocked/check       (is-domain-blocked)
                    GET  /websites/blocked/{id}        (get one)
                    PUT  /websites/blocked/{id}        (partial update)
                    DELETE /websites/blocked/{id}      (delete)
  Website events    POST /web/events                   (submit)
                    GET  /web/events                   (list + filters)
  Web summary       GET  /web/summary

The backend is a CONFIGURATION / HISTORY API only — no server-side browser
monitoring, no accessibility service, no blocking loop, no WebSocket
real-time blocking. Android remains responsible for detecting web/domain
activity, enforcing blocks in real time, showing the blocked-page UI and
handling local restrictions; it syncs events to this layer.

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
from app.schemas.web import (
    BlockedCheckResponse,
    BlockedWebsiteCreate,
    BlockedWebsiteResponse,
    BlockedWebsiteUpdate,
    WebEventType,
    WebSummary,
    WebsiteEventCreate,
    WebsiteEventResponse,
)
from app.services.web import (
    BlockedWebsiteService,
    WebConflictError,
    WebError,
    WebNotFoundError,
    WebsiteEventService,
    WebValidationError,
)

blocked_websites_router = APIRouter(prefix="/websites/blocked", tags=["websites"])
web_events_router = APIRouter(prefix="/web", tags=["web"])


def _raise_http(exc: WebError) -> None:
    """Map web domain errors to HTTP status codes: 404 for missing /
    cross-user records, 409 for duplicate domains, 422 for domain-level
    validation problems, 400 for anything else. Internal details are never
    exposed."""
    if isinstance(exc, WebNotFoundError):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)
        )
    if isinstance(exc, WebConflictError):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail=str(exc)
        )
    if isinstance(exc, WebValidationError):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)
        )
    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)
    )


# ---------------------------------------------------------------------------
# Blocked websites — CRUD
# ---------------------------------------------------------------------------


@blocked_websites_router.post(
    "",
    response_model=BlockedWebsiteResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Create a blocked website",
)
def create_blocked_website(
    payload: BlockedWebsiteCreate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> BlockedWebsiteResponse:
    """Block a domain for the current user. The domain is normalized by the
    service (https://youtube.com/, www.YouTube.com and youtube.com all become
    youtube.com); malformed domains are rejected (422) and a domain the user
    already has blocked is rejected (409)."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    try:
        website = BlockedWebsiteService(db).create(user_id, payload.model_dump())
    except WebError as exc:
        _raise_http(exc)
    return BlockedWebsiteResponse.model_validate(website)


@blocked_websites_router.get(
    "",
    response_model=list[BlockedWebsiteResponse],
    summary="List blocked websites",
)
def list_blocked_websites(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[BlockedWebsiteResponse]:
    """Return all of the current user's blocked websites, oldest first."""
    websites = BlockedWebsiteService(db).list(user_id)
    return [BlockedWebsiteResponse.model_validate(w) for w in websites]


@blocked_websites_router.get(
    "/check",
    response_model=BlockedCheckResponse,
    summary="Check whether a domain is blocked",
)
def check_blocked_domain(
    domain: str = Query(..., min_length=1, max_length=2048),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> BlockedCheckResponse:
    """Answer whether the current user has the given domain blocked. The
    domain is normalized before matching, so youtube.com and
    https://www.youtube.com/ give the same answer."""
    return BlockedCheckResponse.model_validate(
        BlockedWebsiteService(db).check(user_id, domain)
    )


@blocked_websites_router.get(
    "/{website_id}",
    response_model=BlockedWebsiteResponse,
    summary="Get one blocked website",
)
def get_blocked_website(
    website_id: int,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> BlockedWebsiteResponse:
    """Return one of the current user's blocked websites (404 when absent)."""
    try:
        website = BlockedWebsiteService(db).get(user_id, website_id)
    except WebError as exc:
        _raise_http(exc)
    return BlockedWebsiteResponse.model_validate(website)


@blocked_websites_router.put(
    "/{website_id}",
    response_model=BlockedWebsiteResponse,
    summary="Update a blocked website",
)
def update_blocked_website(
    website_id: int,
    payload: BlockedWebsiteUpdate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> BlockedWebsiteResponse:
    """Partially update one of the current user's blocked websites (e.g. flip
    `is_blocked`). The domain is re-normalized when supplied; a change that
    would collide with another of the user's domains is rejected (409)."""
    try:
        website = BlockedWebsiteService(db).update(
            user_id, website_id, payload.model_dump(exclude_unset=True)
        )
    except WebError as exc:
        _raise_http(exc)
    return BlockedWebsiteResponse.model_validate(website)


@blocked_websites_router.delete(
    "/{website_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Delete a blocked website",
)
def delete_blocked_website(
    website_id: int,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> None:
    """Remove one of the current user's blocked websites (404 when absent)."""
    try:
        BlockedWebsiteService(db).delete(user_id, website_id)
    except WebError as exc:
        _raise_http(exc)


# ---------------------------------------------------------------------------
# Website events + summary
# ---------------------------------------------------------------------------


@web_events_router.post(
    "/events",
    response_model=WebsiteEventResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Submit a website event",
)
def create_website_event(
    payload: WebsiteEventCreate,
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> WebsiteEventResponse:
    """Persist one website event for the current user (event_type must be
    BLOCK_ATTEMPT, BLOCKED or UNBLOCKED; device / blocked_website references
    must belong to the user). The domain is normalized before storage.
    `occurred_at` defaults to the server's current UTC time when omitted."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    try:
        event = WebsiteEventService(db).create_event(user_id, payload.model_dump())
    except WebError as exc:
        _raise_http(exc)
    return WebsiteEventResponse.model_validate(event)


@web_events_router.get(
    "/events",
    response_model=list[WebsiteEventResponse],
    summary="List website events (history)",
)
def list_website_events(
    event_type: WebEventType | None = Query(default=None),
    device_id: int | None = Query(default=None),
    domain: str | None = Query(default=None, max_length=2048),
    start_date: date | None = Query(default=None),
    end_date: date | None = Query(default=None),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=50, ge=1, le=100),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> list[WebsiteEventResponse]:
    """Return the current user's website event history (newest first),
    filtered by event type, device, normalized domain or an occurred-at date
    range, with page/page_size pagination. Only the caller's own events are
    returned."""
    start_dt = datetime.combine(start_date, time.min) if start_date else None
    end_dt = datetime.combine(end_date, time.max) if end_date else None
    try:
        events = WebsiteEventService(db).list_events(
            user_id,
            event_type=event_type,
            device_id=device_id,
            domain=domain,
            start_date=start_dt,
            end_date=end_dt,
            page=page,
            page_size=page_size,
        )
    except WebError as exc:
        _raise_http(exc)
    return [WebsiteEventResponse.model_validate(e) for e in events]


@web_events_router.get(
    "/summary",
    response_model=WebSummary,
    summary="Get a basic web summary",
)
def get_web_summary(
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> WebSummary:
    """Read-only summary of the current user's stored web data: total block
    attempts / blocked / unblocked events and the number of distinct domains
    currently blocked. Intentionally basic — weekly/monthly reports, Your
    Score, Rank and leaderboard are later phases."""
    summary = WebsiteEventService(db).summary(user_id)
    return WebSummary.model_validate(summary)
