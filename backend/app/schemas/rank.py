"""rank.py — ShortsCap backend: Pydantic schemas for the Rank / Leaderboard
API.

Response models for `GET /rank/weekly` and `GET /rank/monthly` per the
approved Phase 15A contract: period, the current user's rank/score/status,
rank change vs the previous equivalent period, winner, top three, paginated
entries and pagination info.

Privacy (Phase 15A §8): entries expose ONLY `rank`, `display_name`, `score`
and the opaque `user_id` — never email, phone, or any private profile field.
The current user's detailed score breakdown stays in the Score API.
"""

from datetime import date

from pydantic import BaseModel

from app.schemas.reports import PeriodInfo


class RankEntry(BaseModel):
    """One leaderboard row: approved public fields only."""

    rank: int
    display_name: str
    score: int
    user_id: int


class RankPagination(BaseModel):
    """Pagination over the full ranked list (global ranks on every page)."""

    page: int
    page_size: int
    total_pages: int


class RankResponse(BaseModel):
    """One period's leaderboard for the current user.

    `your_rank` / `your_score` are null when the current user is not eligible
    (opted out, or `insufficient_data`); `your_score_status` explains why.
    `rank_change` is null when the previous equivalent period has no
    leaderboard data or the user was not eligible then. `winner` is null
    when there are no eligible participants.
    """

    period: PeriodInfo
    your_rank: int | None
    your_score: int | None
    your_score_status: str | None  # sufficient_data | partial_data | insufficient_data | not_opted_in
    rank_change: int | None
    total_participants: int
    winner: RankEntry | None
    top_three: list[RankEntry]
    entries: list[RankEntry]
    pagination: RankPagination
