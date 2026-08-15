"""rank.py — ShortsCap backend: FastAPI routes for the Rank / Leaderboard
engine.

Architecture: API -> RankService -> Score Engine (batch_scores) + RankRepository
-> SQLAlchemy -> MySQL. Routers contain no database queries and no ranking
logic.

Endpoints:
  GET /rank/weekly?date=YYYY-MM-DD&page=1&page_size=20
  GET /rank/monthly?date=YYYY-MM-DD&page=1&page_size=20

Behavior follows the approved Phase 15A specification: competition ranking,
deterministic tie-break ordering, eligibility via opt-in + score status,
winner / top three from the same ranked pass, rank change vs the previous
equivalent period, dynamic board (nothing is written to `leaderboard_scores`),
and only approved public fields per entry. The Score Engine remains the
single source of score values.

Deliberately NOT here: rank storage, snapshots, caching, other users' private
data, AWS, Cognito.

TEMPORARY DEVELOPMENT IDENTITY (NOT PRODUCTION AUTH):
AWS Cognito is implemented in a later phase. Until then the API reads the
development user ID from the `X-Dev-User-Id` header (see
`app/routers/deps.py` — the single Cognito replacement point).
"""

from datetime import date

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.database import get_db
from app.routers.deps import ensure_dev_user, get_dev_user_id
from app.schemas.rank import RankResponse
from app.services.rank import RankService

router = APIRouter(prefix="/rank", tags=["rank"])


def _rank_endpoint(
    period_type: str,
    report_date: date | None,
    page: int,
    page_size: int,
    user_id: int,
    db: Session,
) -> RankResponse:
    """Shared handler for both periods."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    result = RankService(db).leaderboard(
        period_type,
        user_id,
        report_date=report_date,
        page=page,
        page_size=page_size,
    )
    return RankResponse.model_validate(result)


@router.get(
    "/weekly",
    response_model=RankResponse,
    summary="Weekly leaderboard",
)
def weekly_rank(
    date: date = Query(default=None, description="Any date in the UTC ISO week (default: today)"),
    page: int = Query(default=1, ge=1, description="Page number (1-based)"),
    page_size: int = Query(default=20, ge=1, le=100, description="Entries per page"),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> RankResponse:
    """This ISO week's leaderboard: current user's rank/score/change, winner,
    top three and the requested page of the full ranked list."""
    return _rank_endpoint("weekly", date, page, page_size, user_id, db)


@router.get(
    "/monthly",
    response_model=RankResponse,
    summary="Monthly leaderboard",
)
def monthly_rank(
    date: date = Query(default=None, description="Any date in the UTC month (default: today)"),
    page: int = Query(default=1, ge=1, description="Page number (1-based)"),
    page_size: int = Query(default=20, ge=1, le=100, description="Entries per page"),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> RankResponse:
    """This calendar month's leaderboard with the same contract as weekly."""
    return _rank_endpoint("monthly", date, page, page_size, user_id, db)
