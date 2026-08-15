"""score.py — ShortsCap backend: FastAPI routes for the Your Score engine.

Architecture: API -> ScoreService -> pure component modules -> ScoringQueries
-> SQLAlchemy -> MySQL. Routers contain no database queries and no scoring
math.

Endpoints:
  GET /score/daily?date=YYYY-MM-DD
  GET /score/weekly?date=YYYY-MM-DD
  GET /score/monthly?date=YYYY-MM-DD

The score is computed on the CURRENT user's data only (development identity
from the `X-Dev-User-Id` header). Dates are interpreted as UTC calendar
dates (the backend's documented naive-UTC convention); when omitted they
default to the server's current UTC date. Scores are always normalized to
0–100 with the approved Phase 14A weights (40/25/20/10/5), inactivity gate
and coverage rules, and include a deterministic explanation.

Deliberately NOT here: rank, leaderboard, other users' scores, score
storage (the engine calculates dynamically; `leaderboard_scores` is not
written), AWS, Cognito.

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
from app.schemas.score import ScoreResponse
from app.services.scoring import ScoreService
from app.utils.datetime import utcnow

router = APIRouter(prefix="/score", tags=["score"])


def _score_endpoint(period_type: str, report_date: date | None, user_id: int, db: Session) -> ScoreResponse:
    """Shared handler for all three periods."""
    ensure_dev_user(db, user_id)  # TEMPORARY DEVELOPMENT ONLY
    if report_date is None:
        report_date = utcnow().date()
    result = ScoreService(db).score(user_id, period_type, report_date)
    return ScoreResponse.model_validate(result)


@router.get(
    "/daily",
    response_model=ScoreResponse,
    summary="Daily Your Score",
)
def daily_score(
    date: date = Query(default=None, description="UTC calendar date (default: today)"),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ScoreResponse:
    """The current user's Your Score for one UTC day, with component
    breakdown and explanation."""
    return _score_endpoint("daily", date, user_id, db)


@router.get(
    "/weekly",
    response_model=ScoreResponse,
    summary="Weekly Your Score",
)
def weekly_score(
    date: date = Query(default=None, description="Any date in the UTC ISO week (default: today)"),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ScoreResponse:
    """The current user's Your Score for the ISO week (Monday–Sunday)
    containing the date, computed on the week's aggregates (never summed
    daily scores), with component breakdown and explanation."""
    return _score_endpoint("weekly", date, user_id, db)


@router.get(
    "/monthly",
    response_model=ScoreResponse,
    summary="Monthly Your Score",
)
def monthly_score(
    date: date = Query(default=None, description="Any date in the UTC month (default: today)"),
    user_id: int = Depends(get_dev_user_id),
    db: Session = Depends(get_db),
) -> ScoreResponse:
    """The current user's Your Score for the calendar month containing the
    date, computed on the month's aggregates, with component breakdown and
    explanation."""
    return _score_endpoint("monthly", date, user_id, db)
