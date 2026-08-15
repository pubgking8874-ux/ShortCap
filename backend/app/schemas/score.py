"""score.py — ShortsCap backend: Pydantic schemas for the Your Score API.

Response models for `GET /score/daily|weekly|monthly`. `PeriodInfo` is
reused from the reporting layer (`app/schemas/reports.py`) so score and
report periods are always identical. The response carries the final score
(0–100), per-component breakdown, activity/coverage info and a deterministic
explanation — nothing about other users, and no rank/leaderboard data.
"""

from pydantic import BaseModel

from app.schemas.reports import PeriodInfo


class ScoreComponent(BaseModel):
    """One component's contribution: points out of `max`, raw `value` and
    data status (`evaluated` | `neutral`)."""

    name: str
    value: float
    status: str
    points: float
    max: int


class ScoreActivity(BaseModel):
    """Inactivity gate / coverage info for the period."""

    active_days: int
    required_days: int
    coverage: float


class ScoreExplanation(BaseModel):
    """Deterministic, user-facing explanation of the score."""

    summary: str
    positives: list[str]
    negatives: list[str]


class ScoreResponse(BaseModel):
    """One period's Your Score with its component breakdown."""

    period: PeriodInfo
    score: int
    status: str  # sufficient_data | partial_data | insufficient_data
    components: list[ScoreComponent]
    activity: ScoreActivity
    explanation: ScoreExplanation
